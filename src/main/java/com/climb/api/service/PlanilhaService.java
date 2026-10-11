package com.climb.api.service;

import com.climb.api.model.Planilha;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.repository.PlanilhaRepository;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.security.PlanilhaAcesso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PlanilhaService {

    private final PlanilhaRepository repository;
    private final ContratoRepository contratos;
    private final PlanilhaAcesso acesso;

    public PlanilhaService(PlanilhaRepository repository, ContratoRepository contratos, PlanilhaAcesso acesso) {
        this.repository = repository;
        this.contratos = contratos;
        this.acesso = acesso;
    }

    public List<Planilha> listar() {
        return repository.findAll().stream().filter(p -> acesso.pode("LER", p)).toList();
    }

    public Planilha buscarPorId(Long id) {
        Planilha planilha = carregar(id);
        acesso.exigir("LER", planilha);
        return planilha;
    }

    public Planilha criar(Planilha planilha) {
        planilha.setIdPlanilha(null);
        vincularContrato(planilha);
        acesso.exigir("CRIAR", planilha);
        if (Boolean.TRUE.equals(planilha.getBloqueada()) || planilha.getPermissaoVisualizacao() != null) {
            acesso.exigirConfiguracao(planilha);
        }
        return repository.save(planilha);
    }

    public Planilha atualizar(Long id, Planilha atualizada) {
        Planilha planilha = carregar(id);
        acesso.exigir("EDITAR", planilha);
        if (Boolean.TRUE.equals(planilha.getBloqueada())
                || !Objects.equals(planilha.getBloqueada(), atualizada.getBloqueada())
                || !Objects.equals(planilha.getPermissaoVisualizacao(), atualizada.getPermissaoVisualizacao())) {
            acesso.exigirConfiguracao(planilha);
        }
        vincularContrato(atualizada);
        acesso.exigir("EDITAR", atualizada);
        planilha.setContrato(atualizada.getContrato());
        planilha.setUrlGoogleSheets(atualizada.getUrlGoogleSheets());
        planilha.setBloqueada(atualizada.getBloqueada());
        planilha.setPermissaoVisualizacao(atualizada.getPermissaoVisualizacao());
        return repository.save(planilha);
    }

    public void deletar(Long id) {
        Planilha planilha = carregar(id);
        acesso.exigir("DELETAR", planilha);
        if (Boolean.TRUE.equals(planilha.getBloqueada())) acesso.exigirConfiguracao(planilha);
        repository.delete(planilha);
    }

    private Planilha carregar(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Planilha não encontrada."));
    }

    private void vincularContrato(Planilha planilha) {
        if (planilha.getPermissaoVisualizacao() != null && !planilha.getPermissaoVisualizacao().isBlank()) {
            try { PermissaoCodigo.valueOf(planilha.getPermissaoVisualizacao()); }
            catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma permissão de visualização válida.");
            }
        }
        if (planilha.getContrato() == null || planilha.getContrato().getIdContrato() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o contrato da planilha.");
        }
        planilha.setContrato(contratos.findById(planilha.getContrato().getIdContrato()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado.")));
    }
}
