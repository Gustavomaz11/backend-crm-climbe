package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoServicos;
import com.climb.api.model.Notificacao;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.repository.NotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Avisos internos dos eventos comerciais; a entrega por e-mail mantém seu fluxo próprio. */
@Service
@Transactional
public class DocumentoFluxoNotificacaoService {
    private final NotificacaoRepository notificacoes;
    private final UsuarioRepository usuarios;
    private record Destinatario(Usuario usuario, String destino) {}

    public DocumentoFluxoNotificacaoService(NotificacaoRepository notificacoes, UsuarioRepository usuarios) {
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
    }

    public void propostaAceita(Proposta proposta, Integer versao) {
        String mensagem = "A proposta #" + proposta.getIdProposta() + " da empresa "
                + (proposta.getEmpresa() == null ? "não informada" : proposta.getEmpresa().getNomeFantasia())
                + (versao == null ? "" : " (versão " + versao + ")") + " foi aceita.";
        registrar(comercial(proposta), "PROPOSTA_ACEITA", mensagem, "/propostas?proposta=" + proposta.getIdProposta());
    }

    public void propostaRespondidaPeloCliente(Proposta proposta, RevisaoDocumentoStatus status, int versao, String comentario) {
        if (!respostaComPendencia(status)) return;
        String tipo = status == RevisaoDocumentoStatus.REPROVADO ? "PROPOSTA_REPROVADA" : "PROPOSTA_REVISAO_SOLICITADA";
        String empresa = proposta.getEmpresa() == null ? "não informada" : proposta.getEmpresa().getNomeFantasia();
        String mensagem = mensagemResposta("Proposta #" + proposta.getIdProposta(), empresa, status, versao, comentario);
        registrar(comercial(proposta), tipo, mensagem, "/propostas?proposta=" + proposta.getIdProposta());
    }

    public void contratoRespondidoPeloCliente(Contrato contrato, RevisaoDocumentoStatus status, int versao, String comentario) {
        if (!respostaComPendencia(status)) return;
        String tipo = status == RevisaoDocumentoStatus.REPROVADO ? "CONTRATO_REPROVADO" : "CONTRATO_REVISAO_SOLICITADA";
        String mensagem = mensagemResposta("Contrato CT-" + contrato.getIdContrato(), contrato.getEmpresaNomeFantasia(), status, versao, comentario);
        notificar(comercialEJuridico(contrato), tipo, mensagem);
    }

    private boolean respostaComPendencia(RevisaoDocumentoStatus status) {
        return status == RevisaoDocumentoStatus.AJUSTES_SOLICITADOS || status == RevisaoDocumentoStatus.REPROVADO;
    }

    private String mensagemResposta(String documento, String empresa, RevisaoDocumentoStatus status, int versao, String comentario) {
        boolean reprovado = status == RevisaoDocumentoStatus.REPROVADO;
        String mensagem = (reprovado ? "O cliente reprovou o documento." : "O cliente solicitou revisão do documento.")
                + "\n" + documento + " da empresa " + empresa + " (versão " + versao + ").";
        if (comentario != null && !comentario.isBlank()) {
            mensagem += "\n" + (reprovado ? "Justificativa: " : "Comentário do cliente: ") + comentario.strip();
        }
        return mensagem;
    }

    public void contratoCriado(Contrato contrato) {
        var destinatarios = comercialEJuridico(contrato);
        String mensagem = "Novo contrato CT-" + contrato.getIdContrato() + " da empresa "
                + contrato.getEmpresaNomeFantasia() + " disponível para preparação e acompanhamento.";
        notificar(destinatarios, "CONTRATO_CRIADO", mensagem);
    }

    public void contratoAprovado(Contrato contrato) {
        contratoAprovado(contrato, null);
    }

    public void contratoAprovado(Contrato contrato, Integer versao) {
        var destinatarios = comercialEJuridico(contrato);
        for (Usuario tecnico : ContratoServicos.responsaveis(contrato)) {
            String servico = contrato.getResponsaveisPorServico().entrySet().stream()
                    .filter(item -> Objects.equals(item.getValue().getId(), tecnico.getId()))
                    .map(item -> "&servico=" + item.getKey().name()).findFirst().orElse("");
            adicionar(destinatarios, tecnico, "/contratos/kanban?contrato=" + contrato.getIdContrato() + servico);
        }
        notificar(destinatarios, "CONTRATO_APROVADO", "O contrato CT-" + contrato.getIdContrato()
                + " da empresa " + contrato.getEmpresaNomeFantasia()
                + (versao == null ? "" : " (versão " + versao + ")") + " foi aprovado.");
    }

    private Map<Long, Destinatario> comercialEJuridico(Contrato contrato) {
        var destinatarios = new LinkedHashMap<Long, Destinatario>();
        String destino = "/contratos?contrato=" + contrato.getIdContrato();
        Usuario comercial = contrato.getResponsavelComercial();
        if (comercial == null) comercial = contrato.getProposta() == null ? contrato.getUsuario() : comercial(contrato.getProposta());
        adicionar(destinatarios, comercial, destino);
        usuarios.buscarJuridicoAtivo().forEach(usuario -> adicionar(destinatarios, usuario, destino));
        return destinatarios;
    }

    private Usuario comercial(Proposta proposta) {
        if (proposta.getNegocio() != null && proposta.getNegocio().getResponsavel() != null) {
            return proposta.getNegocio().getResponsavel();
        }
        return proposta.getUsuario();
    }

    private void adicionar(Map<Long, Destinatario> destinatarios, Usuario usuario, String destino) {
        if (usuario != null && usuario.getId() != null) destinatarios.put(usuario.getId(), new Destinatario(usuario, destino));
    }

    private void notificar(Map<Long, Destinatario> destinatarios, String tipo, String mensagem) {
        destinatarios.values().forEach(item -> registrar(item.usuario(), tipo, mensagem, item.destino()));
    }

    private void registrar(Usuario usuario, String tipo, String mensagem, String destino) {
        if (usuario == null || usuario.getId() == null || !"ATIVO".equals(usuario.getSituacao())) return;
        if (notificacoes.existsByUsuario_IdAndTipoAndMensagem(usuario.getId(), tipo, mensagem)) return;
        var aviso = new Notificacao();
        aviso.setUsuario(usuario); aviso.setTipo(tipo); aviso.setMensagem(mensagem); aviso.setDestino(destino);
        notificacoes.save(aviso);
    }
}
