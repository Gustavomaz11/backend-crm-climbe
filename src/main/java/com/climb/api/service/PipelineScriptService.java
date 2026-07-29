package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineScript;
import com.climb.api.model.dto.PipelineScriptDesempenhoDTO;
import com.climb.api.model.dto.PipelineScriptRequestDTO;
import com.climb.api.model.dto.PipelineScriptResponseDTO;
import com.climb.api.model.enums.PipelineTarefaStatus;
import com.climb.api.repository.PipelineScriptRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PipelineScriptService {
    private final PipelineScriptRepository repository;
    private final PipelineVendasTarefaRepository tarefaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;

    public PipelineScriptService(PipelineScriptRepository repository,
                                 PipelineVendasTarefaRepository tarefaRepository,
                                 UsuarioRepository usuarioRepository,
                                 RbacService rbacService) {
        this.repository = repository;
        this.tarefaRepository = tarefaRepository;
        this.usuarioRepository = usuarioRepository;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<PipelineScriptResponseDTO> listar(Long usuarioId, boolean somenteAtivos) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_SCRIPT_VISUALIZAR);
        List<PipelineScript> scripts = somenteAtivos
                ? repository.findByAtivoTrueOrderByNomeAsc()
                : repository.findAllByOrderByNomeAsc();
        return scripts.stream().map(this::toResponse).toList();
    }

    @Transactional
    public PipelineScriptResponseDTO criar(Long usuarioId, PipelineScriptRequestDTO dto) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_SCRIPT_CRIAR);
        String nome = dto.nome().trim();
        if (repository.existsByNomeIgnoreCase(nome)) conflito();
        PipelineScript script = new PipelineScript();
        script.setCriadoPor(usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado")));
        aplicar(script, dto, nome);
        return toResponse(repository.save(script));
    }

    @Transactional
    public PipelineScriptResponseDTO atualizar(Long id, Long usuarioId, PipelineScriptRequestDTO dto) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_SCRIPT_EDITAR);
        PipelineScript script = buscar(id);
        String nome = dto.nome().trim();
        if (repository.existsByNomeIgnoreCaseAndIdScriptNot(nome, id)) conflito();
        aplicar(script, dto, nome);
        return toResponse(repository.save(script));
    }

    @Transactional(readOnly = true)
    public PipelineScriptDesempenhoDTO desempenho(Long id, Long usuarioId) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_SCRIPT_VISUALIZAR);
        buscar(id);
        long geradas = tarefaRepository.countByScriptIdScript(id);
        long concluidas = tarefaRepository.countByScriptIdScriptAndStatus(id, PipelineTarefaStatus.CONCLUIDA);
        double taxa = geradas == 0 ? 0 : BigDecimal.valueOf(concluidas * 100.0 / geradas)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new PipelineScriptDesempenhoDTO(id, geradas, concluidas, taxa);
    }

    public PipelineScript buscarAtivo(Long id) {
        if (id == null) return null;
        return repository.findById(id).filter(script -> Boolean.TRUE.equals(script.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script ativo não encontrado"));
    }

    private PipelineScript buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Script não encontrado"));
    }

    private void aplicar(PipelineScript script, PipelineScriptRequestDTO dto, String nome) {
        script.setNome(nome);
        script.setCategoria(dto.categoria().trim());
        script.setCanal(dto.canal());
        script.setModeloMensagem(dto.modeloMensagem().trim());
        script.setAtivo(dto.ativo());
    }

    private void exigir(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para a biblioteca de scripts");
        }
    }

    private void conflito() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um script com este nome");
    }

    private PipelineScriptResponseDTO toResponse(PipelineScript script) {
        return new PipelineScriptResponseDTO(script.getIdScript(), script.getNome(), script.getCategoria(),
                script.getCanal(), script.getModeloMensagem(), Boolean.TRUE.equals(script.getAtivo()),
                script.getCriadoEm(), script.getAtualizadoEm());
    }
}
