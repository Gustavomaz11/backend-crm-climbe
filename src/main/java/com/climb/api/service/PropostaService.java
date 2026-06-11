package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.HistoricoAprovacaoProposta;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Proposta;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.PropostaAprovacaoRequestDTO;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.dto.PropostaResponseDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoPropostaRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.service.RbacService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.climb.api.model.dto.HistoricoAprovacaoPropostaResponseDTO;

@Service
public class PropostaService {

    private final PropostaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistoricoAprovacaoPropostaRepository historicoRepository;
    private final RbacService rbacService;

    public PropostaService(PropostaRepository repository,
                           EmpresaRepository empresaRepository,
                           UsuarioRepository usuarioRepository,
                           HistoricoAprovacaoPropostaRepository historicoRepository,
                           RbacService rbacService) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoRepository = historicoRepository;
        this.rbacService = rbacService;
    }

    public List<HistoricoAprovacaoPropostaResponseDTO> listarHistorico(Long propostaId) {
        if (propostaId == null || !repository.existsById(propostaId)) {
            throw new RuntimeException("Proposta não encontrada");
        }

        return historicoRepository.findByPropostaIdOrderByDataAlteracaoDesc(propostaId)
                .stream()
                .map(h -> new HistoricoAprovacaoPropostaResponseDTO(
                        h.getIdHistorico(),
                        h.getPropostaId(),
                        h.getUsuarioId(),
                        h.getStatusAnterior(),
                        h.getStatusNovo(),
                        h.getDataAlteracao()
                ))
                .toList();
    }

    private PropostaResponseDTO toResponseDTO(Proposta proposta) {
        return new PropostaResponseDTO(
                proposta.getIdProposta(),
                proposta.getEmpresa() != null ? proposta.getEmpresa().getIdEmpresa() : null,
                proposta.getUsuario() != null ? proposta.getUsuario().getId() : null,
                proposta.getUrl(),
                proposta.getStatus(),
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
        validarStatusParaCriacao(dto.status());

        Proposta proposta = new Proposta();
        proposta.setEmpresa(buscarEmpresa(dto.empresaId()));
        proposta.setUsuario(buscarUsuario(dto.usuarioId()));
        proposta.setStatus(dto.status());
        proposta.setUrl(dto.url());
        proposta.setDataCriacao(dto.dataCriacao() != null ? dto.dataCriacao() : LocalDate.now());

        return toResponseDTO(repository.save(proposta));
    }

    @Transactional
    public PropostaResponseDTO aprovar(Long id, Long usuarioId, PropostaAprovacaoRequestDTO dto) {
        if (usuarioId == null) {
            throw new RuntimeException("Usuário não autenticado");
        }

        if (dto.status() == null) {
            throw new RuntimeException("Status é obrigatório");
        }

        if (dto.status() == PropostaStatus.PENDENTE) {
            throw new RuntimeException("Status inválido para aprovação");
        }

        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));

        PropostaStatus statusAnterior = proposta.getStatus();

        if (statusAnterior == dto.status()) {
            return toResponseDTO(proposta);
        }

        if (statusAnterior == PropostaStatus.REJEITADA && dto.status() == PropostaStatus.APROVADA) {
            throw new RuntimeException("Não é permitido reverter uma proposta rejeitada para aprovada");
        }

        proposta.setStatus(dto.status());
        Proposta propostaAtualizada = repository.save(proposta);

        HistoricoAprovacaoProposta historico = new HistoricoAprovacaoProposta();
        historico.setPropostaId(propostaAtualizada.getIdProposta());
        historico.setUsuarioId(usuarioId);
        historico.setStatusAnterior(statusAnterior.name());
        historico.setStatusNovo(dto.status().name());
        historico.setDataAlteracao(LocalDateTime.now());
        historicoRepository.save(historico);

        return toResponseDTO(propostaAtualizada);
    }

    public PropostaResponseDTO atualizar(Long id, PropostaRequestDTO dto) {
        // exige permissão para editar propostas
        if (dto.usuarioId() == null || !rbacService.temPermissao(dto.usuarioId(), PermissaoCodigo.PROPOSTA_CRUD)) {
            throw new RuntimeException("Usuário não tem permissão para editar propostas");
        }
        validarStatusParaAtualizacao(dto.status());

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
        
        // Valida se é um dos status permitidos do enum PropostaStatus
        boolean statusValido = false;
        for (PropostaStatus s : PropostaStatus.values()) {
            if (s.equals(status)) {
                statusValido = true;
                break;
            }
        }
        
        if (!statusValido) {
            throw new RuntimeException("Status inválido. Status permitidos: PENDENTE, APROVADA, REJEITADA");
        }
    }

    private void validarStatusParaCriacao(PropostaStatus status) {
        validarStatus(status);
        
        // Ao criar uma proposta, o status inicial deve ser PENDENTE
        if (status != PropostaStatus.PENDENTE) {
            throw new RuntimeException("Uma proposta nova deve ser criada com status PENDENTE. Status permitido: PENDENTE");
        }
    }

    private void validarStatusParaAtualizacao(PropostaStatus status) {
        validarStatus(status);
        
        // Ao atualizar uma proposta, permite qualquer status válido
        // Validação mais específica pode ser adicionada conforme as regras de negócio evoluem
    }
}