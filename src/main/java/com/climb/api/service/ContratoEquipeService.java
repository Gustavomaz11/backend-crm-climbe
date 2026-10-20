package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.ServicoComercial;
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
        return contratos.buscarVisiveisParaKanban(usuarioId, podeVisualizarTodos(usuarioId));
    }

    @Transactional(readOnly = true)
    public boolean temDisponiveis(Long usuarioId) {
        exigirAutenticado(usuarioId);
        return contratos.existeVisivelParaKanban(usuarioId, podeVisualizarTodos(usuarioId));
    }

    @Transactional(readOnly = true)
    public List<ContratoKanbanResumoDTO> listarResumos(Long usuarioId) {
        exigirAutenticado(usuarioId);
        Map<Long, ContratoKanbanResumoDTO> resumos = new LinkedHashMap<>();
        for (var linha : contratos.buscarResumosVisiveisParaKanban(usuarioId, podeVisualizarTodos(usuarioId))) {
            var resumo = resumos.computeIfAbsent(linha.getId(), id -> new ContratoKanbanResumoDTO(id,
                    linha.getEmpresaNome(), linha.getUrlPdf(), linha.getStatus(), linha.getEquipeConfigurada(),
                    linha.getServico(), new ArrayList<>()));
            if (linha.getServicoDaProposta() != null) {
                resumo.servicos().add(new ContratoKanbanResumoDTO.ServicoResumo(linha.getServicoDaProposta()));
            }
        }
        return List.copyOf(resumos.values());
    }

    private boolean podeVisualizarTodos(Long usuarioId) {
        return rbac.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS);
    }

    @Transactional(readOnly = true)
    public ContratoEquipeResponseDTO buscarEquipe(Long contratoId, Long usuarioId) {
        return buscarEquipe(buscarContrato(contratoId), usuarioId);
    }

    public ContratoEquipeResponseDTO buscarEquipe(Contrato contrato, Long usuarioId) {
        return buscarEquipe(contrato, usuarioId, ContratoServicos.primeiro(contrato));
    }

    @Transactional(readOnly = true)
    public ContratoEquipeResponseDTO buscarEquipe(Long contratoId, Long usuarioId, ServicoComercial servico) {
        return buscarEquipe(buscarContrato(contratoId), usuarioId, servico);
    }

    public ContratoEquipeResponseDTO buscarEquipe(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        exigirAutenticado(usuarioId); exigirAprovado(contrato);
        var selecionado = resolverServico(contrato, servico);
        if (!podeVisualizar(contrato, usuarioId, selecionado)) throw negado();
        return resposta(contrato, usuarioId, selecionado);
    }

    @Transactional
    public ContratoEquipeResponseDTO salvarEquipe(Long contratoId, Long usuarioId, List<Long> ids) {
        return salvarEquipe(contratoId, usuarioId, ids, null);
    }

    @Transactional
    public ContratoEquipeResponseDTO salvarEquipe(Long contratoId, Long usuarioId, List<Long> ids, ServicoComercial informado) {
        exigirAutenticado(usuarioId);
        Contrato contrato = contratos.findByIdForUpdate(contratoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        exigirAprovado(contrato);
        var servico = resolverServico(contrato, informado);
        if (!isLider(contrato, usuarioId, servico)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente o líder técnico pode selecionar a equipe deste serviço.");
        Set<Usuario> selecionados = new TarefaResponsaveisService(usuarios).resolver(ids, null, true);
        var lider = ContratoServicos.responsavel(contrato, servico);
        if (lider != null) selecionados.add(lider);
        List<ContratoEquipeMembro> anteriores = fixos(contrato, servico);
        Set<Long> idsAnteriores = new HashSet<>();
        anteriores.forEach(m -> idsAnteriores.add(m.getUsuario().getId()));
        Set<Long> idsSelecionados = new HashSet<>();
        selecionados.forEach(u -> idsSelecionados.add(u.getId()));
        membros.deleteAll(anteriores.stream().filter(m -> !idsSelecionados.contains(m.getUsuario().getId())).toList());
        for (Usuario usuario : selecionados) {
            if (idsAnteriores.contains(usuario.getId())) continue;
            var membro = new ContratoEquipeMembro();
            membro.setContrato(contrato); membro.setUsuario(usuario); membro.setServico(ContratoServicos.chave(servico));
            membros.save(membro);
            notificacoes.notificarParticipacaoEquipe(contrato, usuario, false, servico);
        }
        contrato.setEquipeConfigurada(true); contratos.save(contrato); membros.flush();
        return resposta(contrato, usuarioId, servico);
    }

    public ServicoComercial resolverServico(Contrato contrato, ServicoComercial informado) {
        var servico = informado == null ? ContratoServicos.primeiro(contrato) : informado;
        if (servico != null && !ContratoServicos.listar(contrato).contains(servico))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este serviço não pertence ao contrato.");
        if (ContratoServicos.encerrado(contrato, servico))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este serviço está encerrado.");
        return servico;
    }

    public boolean isLider(Contrato contrato, Long usuarioId) {
        return usuarioId != null && ContratoServicos.responsaveis(contrato).stream()
                .anyMatch(lider -> Objects.equals(lider.getId(), usuarioId));
    }
    public boolean isLider(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        var lider = ContratoServicos.responsavel(contrato, servico);
        return usuarioId != null && lider != null && Objects.equals(lider.getId(), usuarioId);
    }
    private List<ContratoEquipeMembro> fixos(Contrato contrato, ServicoComercial servico) {
        return membros.findByContrato_IdContrato(contrato.getIdContrato()).stream()
                .filter(m -> ContratoServicos.chave(servico).equals(m.getServico())).toList();
    }
    public boolean configurada(Contrato contrato, ServicoComercial servico) {
        return contrato.isEquipeConfigurada() && (servico == null || !fixos(contrato, servico).isEmpty());
    }
    public boolean membroFixo(Contrato contrato, Long usuarioId) {
        return isLider(contrato, usuarioId) || membros.findByContrato_IdContrato(contrato.getIdContrato()).stream()
                .anyMatch(m -> !"LEGADO".equals(m.getServico()) && Objects.equals(m.getUsuario().getId(), usuarioId));
    }
    public boolean membroFixo(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        return isLider(contrato, usuarioId, servico) || fixos(contrato, servico).stream()
                .anyMatch(m -> Objects.equals(m.getUsuario().getId(), usuarioId));
    }
    private List<ContratoKanbanTask> apoios(Contrato contrato, ServicoComercial servico) {
        return tarefas.buscarApoiosPendentes(contrato.getIdContrato()).stream()
                .filter(t -> Objects.equals(ContratoServicos.daTarefa(t), servico)).toList();
    }
    public boolean podeEditar(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        if (usuarioId == null || !configurada(contrato, servico) || !ContratoService.STATUS_APROVADO.equals(contrato.getStatus())
                || ContratoServicos.encerrado(contrato, servico)) return false;
        return membroFixo(contrato, usuarioId, servico) || apoios(contrato, servico).stream()
                .flatMap(t -> apoiosAtribuidos(t).stream()).anyMatch(u -> Objects.equals(u.getId(), usuarioId));
    }
    public boolean podeEditar(Contrato contrato, Long usuarioId) {
        var servicos = ContratoServicos.listar(contrato);
        return servicos.isEmpty() ? podeEditar(contrato, usuarioId, null)
                : servicos.stream().anyMatch(s -> podeEditar(contrato, usuarioId, s));
    }
    public boolean podeVisualizar(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        return isLider(contrato, usuarioId, servico) || podeEditar(contrato, usuarioId, servico) || podeVisualizarTodos(usuarioId);
    }
    public boolean podeVisualizar(Contrato contrato, Long usuarioId) {
        return isLider(contrato, usuarioId) || podeEditar(contrato, usuarioId) || podeVisualizarTodos(usuarioId);
    }
    public void exigirAcesso(Contrato contrato, Long usuarioId, boolean editar) {
        exigirAutenticado(usuarioId); exigirAprovado(contrato);
        if (!(editar ? podeEditar(contrato, usuarioId) : podeVisualizar(contrato, usuarioId))) throw negado();
        if (!contrato.isEquipeConfigurada()) throw equipePendente();
    }
    public void exigirAcesso(Contrato contrato, Long usuarioId, boolean editar, ServicoComercial servico) {
        exigirAutenticado(usuarioId); exigirAprovado(contrato);
        resolverServico(contrato, servico);
        if (!(editar ? podeEditar(contrato, usuarioId, servico) : podeVisualizar(contrato, usuarioId, servico))) throw negado();
        if (!configurada(contrato, servico)) throw equipePendente();
    }
    private ResponseStatusException equipePendente() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "O líder técnico precisa selecionar a equipe antes de abrir o kanban.");
    }
    public List<Usuario> membrosAtuais(Contrato contrato) {
        Map<Long, Usuario> equipe = new LinkedHashMap<>();
        var servicos = ContratoServicos.listar(contrato);
        if (servicos.isEmpty()) return membrosAtuais(contrato, null);
        servicos.stream().filter(s -> !ContratoServicos.encerrado(contrato, s)).forEach(s ->
                membrosAtuais(contrato, s).forEach(u -> equipe.put(u.getId(), u)));
        return List.copyOf(equipe.values());
    }
    public List<Usuario> membrosAtuais(Contrato contrato, ServicoComercial servico) {
        Map<Long, Usuario> equipe = new LinkedHashMap<>();
        fixos(contrato, servico).forEach(m -> equipe.put(m.getUsuario().getId(), m.getUsuario()));
        apoios(contrato, servico).forEach(t -> apoiosAtribuidos(t).forEach(u -> equipe.put(u.getId(), u)));
        var lider = ContratoServicos.responsavel(contrato, servico);
        if (lider != null) equipe.put(lider.getId(), lider);
        return equipe.values().stream().sorted(Comparator.comparing(Usuario::getNomeCompleto,
                Comparator.nullsLast(String::compareToIgnoreCase))).toList();
    }
    @Transactional
    public void atribuirResponsaveis(ContratoKanbanTask tarefa, Long autorId, Set<Long> anteriores) {
        Contrato contrato = tarefa.getContrato();
        var servico = ContratoServicos.daTarefa(tarefa);
        Set<Long> selecionados = new HashSet<>();
        tarefa.getResponsaveisEfetivos().forEach(u -> selecionados.add(u.getId()));
        tarefa.getApoios().removeIf(u -> !selecionados.contains(u.getId()));
        if (!isLider(contrato, autorId, servico)) {
            for (Usuario usuario : tarefa.getResponsaveisEfetivos()) {
                if (!anteriores.contains(usuario.getId()) && !podeEditar(contrato, usuario.getId(), servico))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o líder técnico pode atribuir pessoas de fora da equipe.");
            }
            return;
        }
        contratos.findByIdForUpdate(contrato.getIdContrato()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        for (Usuario usuario : tarefa.getResponsaveisEfetivos()) {
            if (membroFixo(contrato, usuario.getId(), servico)) continue;
            boolean jaTemAcesso = podeEditar(contrato, usuario.getId(), servico);
            tarefa.getApoios().add(usuario);
            if (!jaTemAcesso) notificacoes.notificarParticipacaoEquipe(contrato, usuario, true, servico);
        }
    }
    private List<Usuario> apoiosAtribuidos(ContratoKanbanTask tarefa) {
        Set<Long> ids = new HashSet<>(); tarefa.getResponsaveisEfetivos().forEach(u -> ids.add(u.getId()));
        return tarefa.getApoios().stream().filter(u -> ids.contains(u.getId())).toList();
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
    private ContratoEquipeResponseDTO resposta(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        boolean lider = isLider(contrato, usuarioId, servico);
        var fixos = fixos(contrato, servico).stream().map(m -> resumo(m.getUsuario())).toList();
        var disponiveis = (lider ? usuarios.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO") : membrosAtuais(contrato, servico))
                .stream().map(this::resumo).toList();
        Map<Long, Usuario> temporarios = new LinkedHashMap<>();
        Map<Long, List<ContratoEquipeResponseDTO.TarefaResumo>> pendentes = new LinkedHashMap<>();
        apoios(contrato, servico).forEach(t -> apoiosAtribuidos(t).forEach(u -> {
            if (membroFixo(contrato, u.getId(), servico)) return;
            temporarios.put(u.getId(), u);
            pendentes.computeIfAbsent(u.getId(), id -> new ArrayList<>()).add(new ContratoEquipeResponseDTO.TarefaResumo(t.getIdTask(), t.getTitulo()));
        }));
        return new ContratoEquipeResponseDTO(contrato.getIdContrato(), configurada(contrato, servico), lider,
                resumo(ContratoServicos.responsavel(contrato, servico)), fixos, disponiveis, temporarios.values().stream()
                .map(u -> new ContratoEquipeResponseDTO.Temporario(resumo(u), pendentes.get(u.getId()))).toList(), servico);
    }
}
