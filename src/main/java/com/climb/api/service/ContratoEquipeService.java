package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ContratoEquipeService {
    private final ContratoRepository contratos;
    private final ContratoEquipeRepository membros;
    private final UsuarioRepository usuarios;
    private final ContratoNotificacaoService notificacoes;
    private final RbacService rbac;
    private final ContratoKanbanTaskRepository tarefas;

    public ContratoEquipeService(ContratoRepository contratos, ContratoEquipeRepository membros,
            UsuarioRepository usuarios, ContratoNotificacaoService notificacoes, RbacService rbac,
            ContratoKanbanTaskRepository tarefas) {
        this.contratos = contratos; this.membros = membros; this.usuarios = usuarios;
        this.notificacoes = notificacoes; this.rbac = rbac; this.tarefas = tarefas;
    }

    @Transactional(readOnly = true)
    public List<Contrato> listarDisponiveis(Long usuarioId) {
        exigirAutenticado(usuarioId);
        return contratos.findByStatus(ContratoService.STATUS_APROVADO).stream()
                .filter(contrato -> podeVisualizar(contrato, usuarioId)).toList();
    }

    @Transactional(readOnly = true)
    public ContratoEquipeResponseDTO buscarEquipe(Long contratoId, Long usuarioId) {
        exigirAutenticado(usuarioId);
        Contrato contrato = buscarContrato(contratoId);
        if (!podeVisualizar(contrato, usuarioId)) throw negado();
        return resposta(contrato, usuarioId);
    }

    @Transactional
    public ContratoEquipeResponseDTO salvarEquipe(Long contratoId, Long usuarioId, List<Long> ids) {
        exigirAutenticado(usuarioId);
        Contrato contrato = contratos.findByIdForUpdate(contratoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        exigirAprovado(contrato);
        if (!isLider(contrato, usuarioId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente o líder técnico pode selecionar a equipe do contrato.");
        Set<Usuario> selecionados = new TarefaResponsaveisService(usuarios).resolver(ids, null, true);
        selecionados.add(contrato.getResponsavel());
        List<ContratoEquipeMembro> anteriores = membros.findByContrato_IdContrato(contratoId);
        Set<Long> idsAnteriores = new HashSet<>();
        anteriores.forEach(m -> idsAnteriores.add(m.getUsuario().getId()));
        Set<Long> idsSelecionados = new HashSet<>();
        selecionados.forEach(u -> idsSelecionados.add(u.getId()));
        membros.deleteAll(anteriores.stream().filter(m -> !idsSelecionados.contains(m.getUsuario().getId())).toList());
        for (Usuario usuario : selecionados) {
            if (idsAnteriores.contains(usuario.getId())) continue;
            ContratoEquipeMembro membro = new ContratoEquipeMembro();
            membro.setContrato(contrato); membro.setUsuario(usuario); membros.save(membro);
            notificacoes.notificarParticipacaoEquipe(contrato, usuario, false);
        }
        contrato.setEquipeConfigurada(true);
        contratos.save(contrato);
        membros.flush();
        return resposta(contrato, usuarioId);
    }

    public boolean isLider(Contrato contrato, Long usuarioId) {
        return usuarioId != null && contrato.getResponsavel() != null
                && Objects.equals(contrato.getResponsavel().getId(), usuarioId);
    }

    public boolean membroFixo(Contrato contrato, Long usuarioId) {
        return isLider(contrato, usuarioId) || membros.existsByContrato_IdContratoAndUsuario_Id(contrato.getIdContrato(), usuarioId);
    }

    public boolean podeEditar(Contrato contrato, Long usuarioId) {
        if (usuarioId == null || !contrato.isEquipeConfigurada() || !ContratoService.STATUS_APROVADO.equals(contrato.getStatus())) return false;
        return membroFixo(contrato, usuarioId) || tarefas.existeApoioPendente(contrato.getIdContrato(), usuarioId);
    }

    public void exigirAcesso(Contrato contrato, Long usuarioId, boolean editar) {
        exigirAutenticado(usuarioId);
        exigirAprovado(contrato);
        if (!(editar ? podeEditar(contrato, usuarioId) : podeVisualizar(contrato, usuarioId))) throw negado();
        if (!contrato.isEquipeConfigurada()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O líder técnico precisa selecionar a equipe antes de abrir o kanban.");
    }

    public List<Usuario> membrosAtuais(Contrato contrato) {
        Map<Long, Usuario> equipe = new LinkedHashMap<>();
        membros.findByContrato_IdContrato(contrato.getIdContrato()).forEach(m -> equipe.put(m.getUsuario().getId(), m.getUsuario()));
        tarefas.buscarApoiosPendentes(contrato.getIdContrato()).forEach(t -> apoiosAtribuidos(t)
                .forEach(u -> equipe.put(u.getId(), u)));
        if (contrato.getResponsavel() != null) equipe.put(contrato.getResponsavel().getId(), contrato.getResponsavel());
        return equipe.values().stream().sorted(Comparator.comparing(Usuario::getNomeCompleto,
                Comparator.nullsLast(String::compareToIgnoreCase))).toList();
    }

    @Transactional
    public void atribuirResponsaveis(ContratoKanbanTask tarefa, Long autorId, Set<Long> anteriores) {
        Contrato contrato = tarefa.getContrato();
        Set<Long> selecionados = new HashSet<>();
        tarefa.getResponsaveisEfetivos().forEach(u -> selecionados.add(u.getId()));
        tarefa.getApoios().removeIf(u -> !selecionados.contains(u.getId()));
        if (!isLider(contrato, autorId)) {
            for (Usuario usuario : tarefa.getResponsaveisEfetivos()) {
                if (!anteriores.contains(usuario.getId()) && !podeEditar(contrato, usuario.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Somente o líder técnico pode atribuir pessoas de fora da equipe.");
                }
            }
            return;
        }
        contratos.findByIdForUpdate(contrato.getIdContrato()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        for (Usuario usuario : tarefa.getResponsaveisEfetivos()) {
            if (membroFixo(contrato, usuario.getId())) continue;
            boolean jaTemAcesso = podeEditar(contrato, usuario.getId());
            tarefa.getApoios().add(usuario);
            if (!jaTemAcesso) notificacoes.notificarParticipacaoEquipe(contrato, usuario, true);
        }
    }

    private List<Usuario> apoiosAtribuidos(ContratoKanbanTask tarefa) {
        Set<Long> ids = new HashSet<>();
        tarefa.getResponsaveisEfetivos().forEach(u -> ids.add(u.getId()));
        return tarefa.getApoios().stream().filter(u -> ids.contains(u.getId())).toList();
    }
    private boolean podeVisualizar(Contrato contrato, Long usuarioId) {
        return isLider(contrato, usuarioId) || podeEditar(contrato, usuarioId)
                || rbac.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS);
    }
    private Contrato buscarContrato(Long id) {
        Contrato contrato = contratos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        exigirAprovado(contrato);
        return contrato;
    }
    private void exigirAprovado(Contrato contrato) {
        if (!ContratoService.STATUS_APROVADO.equals(contrato.getStatus())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "O contrato precisa ser aceito e assinado antes de iniciar a execução.");
    }
    private void exigirAutenticado(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
    }
    private ResponseStatusException negado() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não faz parte da equipe deste contrato."); }
    private UsuarioResumoDTO resumo(Usuario usuario) {
        return usuario == null ? null : new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail());
    }
    private ContratoEquipeResponseDTO resposta(Contrato contrato, Long usuarioId) {
        boolean lider = isLider(contrato, usuarioId);
        List<UsuarioResumoDTO> fixos = membros.findByContrato_IdContrato(contrato.getIdContrato()).stream().map(m -> resumo(m.getUsuario())).toList();
        List<UsuarioResumoDTO> disponiveis = (lider ? usuarios.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO") : membrosAtuais(contrato))
                .stream().map(this::resumo).toList();
        Map<Long, Usuario> temporarios = new LinkedHashMap<>();
        Map<Long, List<ContratoEquipeResponseDTO.TarefaResumo>> pendentes = new LinkedHashMap<>();
        tarefas.buscarApoiosPendentes(contrato.getIdContrato()).forEach(t -> apoiosAtribuidos(t).forEach(u -> {
            if (membroFixo(contrato, u.getId())) return;
            temporarios.put(u.getId(), u);
            pendentes.computeIfAbsent(u.getId(), id -> new ArrayList<>()).add(new ContratoEquipeResponseDTO.TarefaResumo(t.getIdTask(), t.getTitulo()));
        }));
        return new ContratoEquipeResponseDTO(contrato.getIdContrato(), contrato.isEquipeConfigurada(), lider,
                resumo(contrato.getResponsavel()), fixos, disponiveis, temporarios.values().stream()
                .map(u -> new ContratoEquipeResponseDTO.Temporario(resumo(u), pendentes.get(u.getId()))).toList());
    }
}
