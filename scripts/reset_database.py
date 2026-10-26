"""Limpeza pontual autorizada. Nunca executado pelo Flyway ou na inicialização.

Dependências: python -m pip install pymysql python-dotenv
Sem --apply, apenas inspeciona. O backup deve ficar fora do repositório.
"""
import argparse
import gzip
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

import pymysql
from dotenv import dotenv_values

EMAIL = "gustavo@climbe.com.br"
PRESERVAR = {"cargos", "grupos_cargos", "grupos_permissoes", "grupo_permissao_itens",
             "permissoes", "flyway_schema_history"}
PARCIAIS = {"usuarios", "usuario_oauth", "usuario_permissoes"}


def identificador(nome):
    return "`" + nome.replace("`", "``") + "`"


def conectar(arquivo):
    config = dotenv_values(arquivo)
    url = urlsplit(config["SPRING_DATASOURCE_URL"].removeprefix("jdbc:"))
    return pymysql.connect(host=url.hostname, port=url.port or 3306, database=url.path.lstrip("/"),
                           user=config["SPRING_DATASOURCE_USERNAME"], password=config["SPRING_DATASOURCE_PASSWORD"],
                           charset="utf8mb4", connect_timeout=15, read_timeout=90, write_timeout=30,
                           ssl={"check_hostname": True}, autocommit=False)


def inventario(cursor):
    cursor.execute("SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES "
                   "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")
    tabelas = dict(cursor.fetchall())
    if not PRESERVAR.union(PARCIAIS).issubset(tabelas):
        raise RuntimeError("O schema não corresponde ao CRM esperado.")
    if any(engine != "InnoDB" for engine in tabelas.values()):
        raise RuntimeError("Todas as tabelas precisam suportar rollback (InnoDB).")
    cursor.execute("SELECT id FROM usuarios WHERE LOWER(email)=%s", (EMAIL,))
    contas = cursor.fetchall()
    if len(contas) != 1:
        raise RuntimeError("A conta a preservar não foi identificada de forma única.")
    return list(tabelas), contas[0][0]


def snapshot(cursor, tabelas):
    resultado = {}
    for tabela in tabelas:
        cursor.execute("SELECT * FROM " + identificador(tabela))
        linhas = sorted(repr(linha) for linha in cursor.fetchall())
        resultado[tabela] = hashlib.sha256("\n".join(linhas).encode("utf-8")).hexdigest()
    return resultado


def salvar_backup(conexao, cursor, tabelas, pasta):
    pasta.mkdir(parents=True, exist_ok=True)
    destino = pasta / ("climbe-antes-limpeza-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".sql.gz")
    with gzip.open(destino, "xt", encoding="utf-8", newline="\n") as arquivo:
        arquivo.write("-- Backup completo anterior à limpeza pontual\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n")
        for tabela in tabelas:
            nome = identificador(tabela)
            cursor.execute("SHOW CREATE TABLE " + nome)
            ddl = cursor.fetchone()[1]
            arquivo.write("DROP TABLE IF EXISTS " + nome + ";\n" + ddl + ";\n")
            cursor.execute("SELECT * FROM " + nome)
            colunas = ",".join(identificador(coluna[0]) for coluna in cursor.description)
            for linha in cursor.fetchall():
                valores = ",".join(conexao.escape(valor) for valor in linha)
                arquivo.write("INSERT INTO " + nome + " (" + colunas + ") VALUES (" + valores + ");\n")
        arquivo.write("SET FOREIGN_KEY_CHECKS=1;\n")
    # Confere a integridade do arquivo comprimido antes de qualquer exclusão.
    with gzip.open(destino, "rb") as arquivo:
        while arquivo.read(1024 * 1024):
            pass
    print("Backup: " + str(destino), flush=True)
    return destino


def limpar(cursor, tabelas, usuario_id):
    cursor.execute("SET FOREIGN_KEY_CHECKS=0")
    for tabela in tabelas:
        if tabela not in PRESERVAR.union(PARCIAIS):
            cursor.execute("DELETE FROM " + identificador(tabela))
    cursor.execute("DELETE FROM usuario_oauth WHERE usuario_id <> %s", (usuario_id,))
    cursor.execute("DELETE FROM usuario_permissoes")
    cursor.execute("DELETE FROM usuarios WHERE id <> %s", (usuario_id,))
    cursor.execute("UPDATE usuarios SET situacao='ATIVO', versao_autenticacao=versao_autenticacao+1, "
                   "versao_registro=versao_registro+1 WHERE id=%s", (usuario_id,))
    cursor.execute("INSERT INTO usuario_permissoes (id_usuario,id_permissao) "
                   "SELECT %s,id_permissao FROM permissoes", (usuario_id,))
    cursor.execute("SET FOREIGN_KEY_CHECKS=1")


def verificar(cursor, tabelas, usuario_id, preservado):
    if snapshot(cursor, PRESERVAR) != preservado:
        raise RuntimeError("Um catálogo que deveria ser preservado foi alterado.")
    for tabela in tabelas:
        if tabela not in PRESERVAR.union(PARCIAIS):
            cursor.execute("SELECT COUNT(*) FROM " + identificador(tabela))
            if cursor.fetchone()[0]:
                raise RuntimeError("Dados remanescentes em " + tabela)
    cursor.execute("SELECT id,email,situacao FROM usuarios")
    if cursor.fetchall() != ((usuario_id, EMAIL, "ATIVO"),):
        raise RuntimeError("Falha na preservação do administrador.")
    cursor.execute("SELECT COUNT(*) FROM usuario_oauth WHERE usuario_id <> %s", (usuario_id,))
    if cursor.fetchone()[0]:
        raise RuntimeError("Vínculos Google de usuários removidos permaneceram.")
    cursor.execute("SELECT COUNT(*) FROM usuario_permissoes WHERE id_usuario=%s", (usuario_id,))
    total = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM permissoes")
    if total != cursor.fetchone()[0]:
        raise RuntimeError("O administrador não recebeu todas as permissões.")
    return total


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(__file__).resolve().parents[1] / ".env")
    parser.add_argument("--backup-dir", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    with conectar(args.env_file) as conexao, conexao.cursor() as cursor:
        tabelas, usuario_id = inventario(cursor)
        print(json.dumps({"tabelas": len(tabelas), "administrador": EMAIL, "id": usuario_id,
                          "preservar": sorted(PRESERVAR), "aplicar": args.apply}), flush=True)
        if not args.apply:
            return
        if not args.backup_dir:
            parser.error("--backup-dir é obrigatório com --apply")
        repo = Path(__file__).resolve().parents[1]
        if args.backup_dir.resolve().is_relative_to(repo):
            parser.error("O backup deve ser salvo fora do repositório.")
        # Impede escritas concorrentes durante backup, exclusões e verificação.
        # Não usar BEGIN: ele liberaria os locks adquiridos por LOCK TABLES.
        conexao.rollback()
        cursor.execute("SET SESSION lock_wait_timeout=20")
        cursor.execute("LOCK TABLES " + ",".join(identificador(tabela) + " WRITE" for tabela in tabelas))
        try:
            tabelas_atuais, id_atual = inventario(cursor)
            if tabelas_atuais != tabelas or id_atual != usuario_id:
                raise RuntimeError("O schema mudou durante a preparação.")
            preservado = snapshot(cursor, PRESERVAR)
            salvar_backup(conexao, cursor, tabelas, args.backup_dir)
            limpar(cursor, tabelas, usuario_id)
            total = verificar(cursor, tabelas, usuario_id, preservado)
            conexao.commit()
            print(json.dumps({"concluido": True, "usuarios": 1, "permissoes_administrador": total,
                              "sessoes_revogadas": True, "aprovacoes_anteriores_removidas": True}), flush=True)
        except BaseException:
            conexao.rollback()
            raise
        finally:
            cursor.execute("SET FOREIGN_KEY_CHECKS=1")
            cursor.execute("UNLOCK TABLES")


if __name__ == "__main__":
    main()
