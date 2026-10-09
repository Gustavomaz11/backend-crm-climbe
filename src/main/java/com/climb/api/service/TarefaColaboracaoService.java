package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;

@Service
public class TarefaColaboracaoService {
    private final ContratoKanbanService contratos;
    private final PipelineTarefaService comercial;
    private final TarefaComentarioRepository comentarios;
    private final TarefaAnexoRepository anexos;
    private final UsuarioRepository usuarios;
    private final CloudflareR2ArquivoStorageService storage;
    private final ArquivoValidationService validation;

    public TarefaColaboracaoService(ContratoKanbanService contratos, PipelineTarefaService comercial,
            TarefaComentarioRepository comentarios, TarefaAnexoRepository anexos, UsuarioRepository usuarios,
            CloudflareR2ArquivoStorageService storage, ArquivoValidationService validation) {
        this.contratos = contratos; this.comercial = comercial; this.comentarios = comentarios;
        this.anexos = anexos; this.usuarios = usuarios; this.storage = storage; this.validation = validation;
    }

    private record Tarefa(ContratoKanbanTask contrato, PipelineVendasTarefa comercial) {}
    public record ConteudoAnexo(TarefaAnexoResponseDTO anexo, byte[] conteudo) {}

    @Transactional(readOnly = true)
    public TarefaColaboracaoResponseDTO listar(TarefaTipo tipo, Long id, Long usuarioId) {
        exigirAcesso(tipo, id, usuarioId);
        var arquivos = tipo == TarefaTipo.CONTRATO ? anexos.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(id)
                : anexos.findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(id);
        var conversa = tipo == TarefaTipo.CONTRATO ? comentarios.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(id)
                : comentarios.findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(id);
        var porComentario = arquivos.stream().filter(anexo -> anexo.getComentario() != null)
                .collect(java.util.stream.Collectors.groupingBy(anexo -> anexo.getComentario().getId()));
        return new TarefaColaboracaoResponseDTO(arquivos.stream().filter(anexo -> anexo.getComentario() == null)
                .map(this::toAnexo).toList(), conversa.stream().map(comentario -> toComentario(comentario,
                        porComentario.getOrDefault(comentario.getId(), List.of()))).toList());
    }

    @Transactional
    public List<TarefaAnexoResponseDTO> anexar(TarefaTipo tipo, Long id, Long usuarioId, List<MultipartFile> arquivos) {
        Tarefa tarefa = exigirAcesso(tipo, id, usuarioId);
        if (arquivos == null || arquivos.isEmpty()) throw erro("Selecione pelo menos um arquivo para anexar à tarefa.");
        validarArquivos(arquivos);
        return salvarAnexos(tarefa, buscarAutor(usuarioId), null, tipo, id, arquivos).stream().map(this::toAnexo).toList();
    }

    @Transactional
    public TarefaComentarioResponseDTO comentar(TarefaTipo tipo, Long id, Long usuarioId,
            String conteudo, Long comentarioPaiId, List<MultipartFile> arquivos) {
        Tarefa tarefa = exigirAcesso(tipo, id, usuarioId);
        if (conteudo == null || conteudo.isBlank()) throw erro("Escreva uma mensagem para publicar seu comentário.");
        if (conteudo.length() > 5000) throw erro("Seu comentário pode ter até 5.000 caracteres.");
        TarefaComentario pai = comentarioPaiId == null ? null : comentarios.findById(comentarioPaiId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário respondido não encontrado."));
        if (pai != null && !pertence(pai.getContratoTask(), pai.getPipelineTarefa(), tipo, id)) {
            throw erro("O comentário respondido deve pertencer a esta tarefa.");
        }
        validarArquivos(arquivos);
        TarefaComentario comentario = new TarefaComentario();
        comentario.setContratoTask(tarefa.contrato()); comentario.setPipelineTarefa(tarefa.comercial());
        comentario.setAutor(buscarAutor(usuarioId)); comentario.setConteudo(conteudo.trim()); comentario.setComentarioPai(pai);
        TarefaComentario salvo = comentarios.save(comentario);
        return toComentario(salvo, salvarAnexos(tarefa, comentario.getAutor(), salvo, tipo, id, arquivos));
    }

    @Transactional(readOnly = true)
    public ConteudoAnexo baixar(TarefaTipo tipo, Long id, Long anexoId, Long usuarioId) {
        exigirAcesso(tipo, id, usuarioId);
        TarefaAnexo anexo = anexos.findById(anexoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        if (!pertence(anexo.getContratoTask(), anexo.getPipelineTarefa(), tipo, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado nesta tarefa.");
        }
        return new ConteudoAnexo(toAnexo(anexo), storage.baixar(anexo.getChave()));
    }

    private Tarefa exigirAcesso(TarefaTipo tipo, Long id, Long usuarioId) {
        return tipo == TarefaTipo.CONTRATO ? new Tarefa(contratos.exigirTaskVisivel(id, usuarioId), null)
                : new Tarefa(null, comercial.exigirTarefaVisivel(id, usuarioId));
    }
    private boolean pertence(ContratoKanbanTask contrato, PipelineVendasTarefa comercial, TarefaTipo tipo, Long id) {
        return tipo == TarefaTipo.CONTRATO ? contrato != null && Objects.equals(contrato.getIdTask(), id)
                : comercial != null && Objects.equals(comercial.getIdTarefa(), id);
    }
    private Usuario buscarAutor(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }
    private void validarArquivos(List<MultipartFile> arquivos) {
        if (arquivos != null) arquivos.forEach(validation::validar);
    }
    private List<TarefaAnexo> salvarAnexos(Tarefa tarefa, Usuario autor, TarefaComentario comentario,
            TarefaTipo tipo, Long id, List<MultipartFile> arquivos) {
        if (arquivos == null) return List.of();
        return arquivos.stream().map(arquivo -> {
            var upload = storage.salvar(arquivo, "tarefas/" + tipo + "/" + id);
            TarefaAnexo anexo = new TarefaAnexo();
            anexo.setContratoTask(tarefa.contrato()); anexo.setPipelineTarefa(tarefa.comercial());
            anexo.setComentario(comentario); anexo.setAutor(autor); anexo.setNome(upload.nomeOriginal());
            anexo.setContentType(upload.contentType()); anexo.setTamanho(upload.tamanho()); anexo.setChave(upload.chave());
            return anexos.save(anexo);
        }).toList();
    }
    private UsuarioResumoDTO toAutor(Usuario usuario) {
        return new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail());
    }
    private TarefaAnexoResponseDTO toAnexo(TarefaAnexo anexo) {
        return new TarefaAnexoResponseDTO(anexo.getId(), anexo.getNome(), anexo.getContentType(), anexo.getTamanho(), toAutor(anexo.getAutor()), anexo.getCriadoEm());
    }
    private TarefaComentarioResponseDTO toComentario(TarefaComentario comentario, List<TarefaAnexo> arquivos) {
        return new TarefaComentarioResponseDTO(comentario.getId(), comentario.getComentarioPai() == null ? null : comentario.getComentarioPai().getId(),
                toAutor(comentario.getAutor()), comentario.getConteudo(), comentario.getCriadoEm(), arquivos.stream().map(this::toAnexo).toList());
    }
    private ResponseStatusException erro(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
}
