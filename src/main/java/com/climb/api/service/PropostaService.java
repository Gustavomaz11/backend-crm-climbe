package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.Proposta;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.PropostaAprovacaoRequestDTO;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.dto.PropostaResponseDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.service.RbacService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PropostaService {

    private final PropostaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;

    public PropostaService(PropostaRepository repository,
                           EmpresaRepository empresaRepository,
                           UsuarioRepository usuarioRepository,
                           RbacService rbacService) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.rbacService = rbacService;
    }

    private PropostaResponseDTO toResponseDTO(Proposta proposta) {
        return new PropostaResponseDTO(
                proposta.getIdProposta(),
                proposta.getEmpresa() != null ? proposta.getEmpresa().getIdEmpresa() : null,
                proposta.getUsuario() != null ? proposta.getUsuario().getId() : null,
                proposta.getStatus(),
                proposta.getUrl(),
                proposta.getDataCriacao()
        );
    }

    private Empresa buscarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada"));
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    public List<PropostaResponseDTO> listar() {
        return repository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PropostaResponseDTO buscarPorId(Long id) {
        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));
        return toResponseDTO(proposta);
    }

    public List<PropostaResponseDTO> listarPorStatus(PropostaStatus status) {
        if (status == null) {
            throw new RuntimeException("Status é obrigatório");
        }

        return repository.findByStatus(status)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PropostaResponseDTO criar(PropostaRequestDTO dto) {
        // exige permissão para criar propostas
        if (dto.usuarioId() == null || !rbacService.temPermissao(dto.usuarioId(), PermissaoCodigo.PROPOSTA_CRUD)) {
            throw new RuntimeException("Usuário não tem permissão para criar propostas");
        }
        validarStatus(dto.status());

        Proposta proposta = new Proposta();
        proposta.setEmpresa(buscarEmpresa(dto.empresaId()));
        proposta.setUsuario(buscarUsuario(dto.usuarioId()));
        proposta.setStatus(dto.status());
        proposta.setUrl(dto.url());
        proposta.setDataCriacao(dto.dataCriacao() != null ? dto.dataCriacao() : LocalDate.now());

        return toResponseDTO(repository.save(proposta));
    }

    public PropostaResponseDTO aprovar(Long id, PropostaAprovacaoRequestDTO dto) {
        if (dto.status() == null) {
            throw new RuntimeException("Status é obrigatório");
        }

        if (dto.status() == PropostaStatus.PENDENTE) {
            throw new RuntimeException("Status inválido para aprovação");
        }

        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));

        proposta.setStatus(dto.status());

        return toResponseDTO(repository.save(proposta));
    }

    public PropostaResponseDTO atualizar(Long id, PropostaRequestDTO dto) {
        // exige permissão para editar propostas
        if (dto.usuarioId() == null || !rbacService.temPermissao(dto.usuarioId(), PermissaoCodigo.PROPOSTA_CRUD)) {
            throw new RuntimeException("Usuário não tem permissão para editar propostas");
        }
        validarStatus(dto.status());

        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));

        proposta.setEmpresa(buscarEmpresa(dto.empresaId()));
        proposta.setUsuario(buscarUsuario(dto.usuarioId()));
        proposta.setStatus(dto.status());
        proposta.setUrl(dto.url());
        proposta.setDataCriacao(dto.dataCriacao() != null ? dto.dataCriacao() : proposta.getDataCriacao());

        return toResponseDTO(repository.save(proposta));
    }

    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Proposta não encontrada");
        }
        repository.deleteById(id);
    }

    private void validarStatus(PropostaStatus status) {
        if (status == null) {
            throw new RuntimeException("Status é obrigatório");
        }
    }
}