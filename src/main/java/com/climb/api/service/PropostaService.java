package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.HistoricoAprovacaoProposta;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.Proposta;
import com.climb.api.model.PropostaReajuste;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.dto.PropostaAprovacaoRequestDTO;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.dto.PropostaResponseDTO;
import com.climb.api.model.dto.PropostaComercialRequestDTO;
import com.climb.api.model.dto.PropostaReajusteDTO;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoPropostaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.climb.api.model.dto.HistoricoAprovacaoPropostaResponseDTO;

@Service
public class PropostaService {

    private final PropostaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final HistoricoAprovacaoPropostaRepository historicoRepository;
    private final RbacService rbacService;
    private final CloudflareR2ArquivoStorageService arquivoStorageService;
    private final RevisaoDocumentoService revisaoDocumentoService;

    public PropostaService(PropostaRepository repository,
                           EmpresaRepository empresaRepository,
                           UsuarioRepository usuarioRepository,
                           PipelineVendasNegocioRepository negocioRepository,
                           HistoricoAprovacaoPropostaRepository historicoRepository,
                           RbacService rbacService,
                           CloudflareR2ArquivoStorageService arquivoStorageService,
                           RevisaoDocumentoService revisaoDocumentoService) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.negocioRepository = negocioRepository;
        this.historicoRepository = historicoRepository;
        this.rbacService = rbacService;
        this.arquivoStorageService = arquivoStorageService;
        this.revisaoDocumentoService = revisaoDocumentoService;
    }

    public List<HistoricoAprovacaoPropostaResponseDTO> listarHistorico(Long propostaId) {
        if (propostaId == null || !repository.existsById(propostaId)) {
            throw new RuntimeException("Proposta não encontrada");
        }

        List<HistoricoAprovacaoProposta> historico = historicoRepository.findByPropostaIdOrderByDataAlteracaoDesc(propostaId);
        Map<Long, Usuario> usuariosPorId = buscarUsuariosDoHistorico(historico);

        return historico
                .stream()
                .map(h -> new HistoricoAprovacaoPropostaResponseDTO(
                        h.getIdHistorico(),
                        h.getPropostaId(),
                        h.getUsuarioId(),
                        usuariosPorId.get(h.getUsuarioId()) != null ? usuariosPorId.get(h.getUsuarioId()).getNomeCompleto() : null,
                        h.getStatusAnterior(),
                        h.getStatusNovo(),
                        h.getDataAlteracao()
                ))
                .toList();
    }

    private Map<Long, Usuario> buscarUsuariosDoHistorico(List<HistoricoAprovacaoProposta> historico) {
        List<Long> usuarioIds = historico.stream()
                .map(HistoricoAprovacaoProposta::getUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (usuarioIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return usuarioRepository.findAllById(usuarioIds)
                .stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    private PropostaResponseDTO toResponseDTO(Proposta proposta) {
        return new PropostaResponseDTO(
                proposta.getIdProposta(),
                proposta.getEmpresa() != null ? proposta.getEmpresa().getIdEmpresa() : null,
                proposta.getNegocio() != null ? proposta.getNegocio().getIdNegocio() : null,
                proposta.getUsuario() != null ? proposta.getUsuario().getId() : null,
                proposta.getUrl(),
                proposta.getValuation(),
                proposta.getStatus(),
                proposta.getDataCriacao(),
                proposta.getServico(),
                proposta.getMesInicio(),
                proposta.getRecorrenciaMeses(),
                proposta.getQuantidadeParcelas(),
                proposta.getParcelasIguais(),
                proposta.getComissaoTecnicoPercentual(),
                proposta.getComissaoComercialPercentual(),
                proposta.getEquipeTecnicaIds(),
                proposta.getEquipeComercialIds(),
                proposta.getReajustes().stream()
                        .map(item -> new PropostaReajusteDTO(item.getMesVigencia(), item.getValor()))
                        .toList(),
                proposta.getObservacoes()
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

    @Transactional(readOnly = true)
    public List<PropostaResponseDTO> listar(Long empresaId, Long negocioId) {
        List<Proposta> propostas;
        if (negocioId != null) {
            propostas = repository.findByNegocioIdNegocioOrderByDataCriacaoDescIdPropostaDesc(negocioId);
        } else if (empresaId != null) {
            propostas = repository.findByEmpresaIdEmpresaOrderByDataCriacaoDescIdPropostaDesc(empresaId);
        } else {
            propostas = repository.findAll();
        }
        return propostas
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PropostaResponseDTO buscarPorId(Long id) {
        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));
        return toResponseDTO(proposta);
    }

    public String gerarUrlDownload(Long id) {
        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));

        if (!StringUtils.hasText(proposta.getUrl())) {
            throw new RuntimeException("Arquivo da proposta não encontrado");
        }

        return arquivoStorageService.gerarUrlTemporariaDownload(proposta.getUrl());
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
        validarEmpresaObrigatoria(dto.empresaId());
        validarValuation(dto.valuation());
        validarStatusParaCriacao(dto.status());

        Empresa empresa = buscarEmpresa(dto.empresaId());
        Proposta proposta = new Proposta();
        proposta.setEmpresa(empresa);
        proposta.setNegocio(resolverNegocio(dto.negocioId(), empresa));
        proposta.setUsuario(buscarUsuario(dto.usuarioId()));
        proposta.setStatus(dto.status());
        proposta.setUrl(dto.url());
        proposta.setValuation(dto.valuation());
        proposta.setDataCriacao(dto.dataCriacao() != null ? dto.dataCriacao() : LocalDate.now());

        return toResponseDTO(repository.save(proposta));
    }

    @Transactional
    public PropostaResponseDTO criarComArquivo(Long empresaId, Long negocioId, Long usuarioId, org.springframework.web.multipart.MultipartFile arquivo,
                                               BigDecimal valuation, PropostaComercialRequestDTO configuracao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, PermissaoCodigo.PROPOSTA_CRUD)) {
            throw new RuntimeException("Usuário não tem permissão para criar propostas");
        }
        validarEmpresaObrigatoria(empresaId);
        validarValuation(valuation);
        validarConfiguracaoComercial(configuracao);

        Empresa empresa = buscarEmpresa(empresaId);
        PipelineVendasNegocio negocio = resolverNegocio(negocioId, empresa);
        Usuario usuario = buscarUsuario(usuarioId);
        revisaoDocumentoService.validarEnvio(empresa, arquivo);

        String prefixo = "propostas/empresa-" + empresa.getIdEmpresa();
        ArquivoUploadResponseDTO upload = arquivoStorageService.salvar(arquivo, prefixo);

        Proposta proposta = new Proposta();
        proposta.setEmpresa(empresa);
        proposta.setNegocio(negocio);
        proposta.setUsuario(usuario);
        proposta.setStatus(PropostaStatus.PENDENTE);
        proposta.setUrl(upload.url());
        proposta.setValuation(valuation);
        proposta.setDataCriacao(LocalDate.now());
        aplicarConfiguracaoComercial(proposta, configuracao);

        Proposta salva = repository.save(proposta);
        revisaoDocumentoService.iniciarProposta(salva, upload, usuario);
        return toResponseDTO(salva);
    }

    private void validarConfiguracaoComercial(PropostaComercialRequestDTO configuracao) {
        if (configuracao == null || configuracao.servico() == null) {
            throw new RuntimeException("Selecione o serviço da proposta");
        }
        int recorrencia = configuracao.recorrenciaMeses() == null ? 0 : configuracao.recorrenciaMeses();
        if (configuracao.servico().recorrente() && (recorrencia < 0 || recorrencia > 24)) {
            throw new RuntimeException("A recorrência deve estar entre 0 e 24 meses");
        }
        int parcelas = configuracao.quantidadeParcelas() == null ? 1 : configuracao.quantidadeParcelas();
        if (!configuracao.servico().recorrente() && (parcelas < 1 || parcelas > 24)) {
            throw new RuntimeException("A quantidade de parcelas deve estar entre 1 e 24");
        }
    }

    private void aplicarConfiguracaoComercial(Proposta proposta, PropostaComercialRequestDTO configuracao) {
        proposta.setServico(configuracao.servico());
        proposta.setMesInicio(configuracao.mesInicio());
        proposta.setRecorrenciaMeses(configuracao.servico().recorrente()
                ? Objects.requireNonNullElse(configuracao.recorrenciaMeses(), 0)
                : null);
        proposta.setQuantidadeParcelas(configuracao.servico().recorrente()
                ? 1
                : Objects.requireNonNullElse(configuracao.quantidadeParcelas(), 1));
        proposta.setParcelasIguais(!Boolean.FALSE.equals(configuracao.parcelasIguais()));
        proposta.setComissaoTecnicoPercentual(configuracao.comissaoTecnicoPercentual());
        proposta.setComissaoComercialPercentual(configuracao.comissaoComercialPercentual());
        proposta.setObservacoes(configuracao.observacoes());
        proposta.setEquipeTecnicaIds(validarUsuarios(configuracao.equipeTecnicaIds()));
        proposta.setEquipeComercialIds(validarUsuarios(configuracao.equipeComercialIds()));
        proposta.setReajustes(toReajustes(configuracao.reajustes()));
    }

    private HashSet<Long> validarUsuarios(List<Long> ids) {
        HashSet<Long> unicos = ids == null ? new HashSet<>() : ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (unicos.isEmpty()) return unicos;
        if (usuarioRepository.findAllById(unicos).size() != unicos.size()) {
            throw new RuntimeException("Uma das pessoas selecionadas para a equipe não foi encontrada");
        }
        return unicos;
    }

    private List<PropostaReajuste> toReajustes(List<PropostaReajusteDTO> reajustes) {
        if (reajustes == null) return List.of();
        return reajustes.stream().map(dto -> {
            PropostaReajuste reajuste = new PropostaReajuste();
            reajuste.setMesVigencia(dto.mesVigencia());
            reajuste.setValor(dto.valor());
            return reajuste;
        }).toList();
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

        if (statusAnterior == PropostaStatus.APROVADA || statusAnterior == PropostaStatus.REJEITADA) {
            throw new RuntimeException("Não é permitido alterar o status de uma proposta já aprovada ou rejeitada");
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
        validarEmpresaObrigatoria(dto.empresaId());
        validarValuation(dto.valuation());
        validarStatusParaAtualizacao(dto.status());

        Proposta proposta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposta não encontrada"));

        Empresa empresa = buscarEmpresa(dto.empresaId());
        proposta.setEmpresa(empresa);
        proposta.setNegocio(resolverNegocio(dto.negocioId(), empresa));
        proposta.setUsuario(buscarUsuario(dto.usuarioId()));
        proposta.setStatus(dto.status());
        proposta.setUrl(dto.url());
        proposta.setValuation(dto.valuation());
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

    private void validarEmpresaObrigatoria(Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            throw new RuntimeException("Selecione uma empresa para a proposta");
        }
    }

    private PipelineVendasNegocio resolverNegocio(Long negocioId, Empresa empresa) {
        if (negocioId == null) return null;
        PipelineVendasNegocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new RuntimeException("Negócio do pipeline não encontrado"));
        Long empresaDoNegocio = negocio.getEmpresa() == null ? null : negocio.getEmpresa().getIdEmpresa();
        if (!Objects.equals(empresaDoNegocio, empresa.getIdEmpresa())) {
            throw new RuntimeException("O negócio informado não pertence à empresa da proposta");
        }
        return negocio;
    }

    private void validarValuation(BigDecimal valuation) {
        if (valuation == null || valuation.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Informe o valuation da proposta");
        }
    }
}
