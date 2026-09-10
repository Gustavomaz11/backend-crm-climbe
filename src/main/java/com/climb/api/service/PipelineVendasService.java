package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PipelineHistoricoTipo;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.PipelineVendasEtapaRepository;
import com.climb.api.repository.PipelineVendasFunilRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.RevisaoDocumentoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineVendasService {
    private final PipelineVendasEtapaRepository etapaRepository;
    private final PipelineVendasFunilRepository funilRepository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final PropostaRepository propostaRepository;
    private final RevisaoDocumentoRepository revisaoDocumentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PipelineEmpresaCadastroService empresaCadastroService;
    private final ContratoService contratoService;
    private final PipelineHistoricoService historicoService;
    private final PipelineMotivoPerdaService motivoPerdaService;
    private final PipelineMovimentacaoEtapaService movimentacaoEtapaService;
    private final RbacService rbacService;
    private final PipelinePreVendaService preVendas;
    private final PipelineCadastroService cadastros;

    public PipelineVendasService(PipelineVendasEtapaRepository etapaRepository,
                                 PipelineVendasFunilRepository funilRepository,
                                 PipelineVendasNegocioRepository negocioRepository,
                                 PropostaRepository propostaRepository,
                                 RevisaoDocumentoRepository revisaoDocumentoRepository,
                                 UsuarioRepository usuarioRepository,
                                 EmpresaRepository empresaRepository,
                                 PipelineEmpresaCadastroService empresaCadastroService,
                                 ContratoService contratoService,
                                 PipelineHistoricoService historicoService,
                                 PipelineMotivoPerdaService motivoPerdaService,
                                 PipelineMovimentacaoEtapaService movimentacaoEtapaService,
                                 RbacService rbacService, PipelinePreVendaService preVendas, PipelineCadastroService cadastros) {
        this.etapaRepository = etapaRepository;
        this.funilRepository = funilRepository;
        this.negocioRepository = negocioRepository;
        this.propostaRepository = propostaRepository;
        this.revisaoDocumentoRepository = revisaoDocumentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.empresaCadastroService = empresaCadastroService;
        this.contratoService = contratoService;
        this.historicoService = historicoService;
        this.motivoPerdaService = motivoPerdaService;
        this.movimentacaoEtapaService = movimentacaoEtapaService;
        this.rbacService = rbacService;
        this.preVendas = preVendas;
        this.cadastros = cadastros;
    }

    @Transactional(readOnly = true)
    public PipelineBoardResponseDTO buscarBoard(Long usuarioId, Long funilId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL);
        PipelineVendasFunil funil = buscarFunilAtivo(funilId);
        List<PipelineVendasEtapa> etapas = etapaRepository
                .findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(funil.getIdFunil());
        List<PipelineVendasNegocio> negocios = negocioRepository
                .findByFunilIdFunilOrderByEtapaPosicaoAscUltimaMovimentacaoEmDesc(funil.getIdFunil());
        IndicadoresProposta indicadores = buscarIndicadoresProposta(negocios);
        Map<Long, List<PipelineNegocioResponseDTO>> negociosPorEtapa = negocios
                .stream()
                .map(negocio -> toResponse(negocio, indicadores))
                .collect(Collectors.groupingBy(PipelineNegocioResponseDTO::etapaId));

        return new PipelineBoardResponseDTO(funil.getIdFunil(), funil.getNome(), etapas.stream()
                .map(etapa -> new PipelineEtapaResponseDTO(
                        etapa.getIdEtapa(), etapa.getCodigo(), etapa.getNome(), etapa.getPosicao(),
                        etapa.getResultado(), etapa.getObjetivo(), etapa.getCriteriosConclusao(),
                        etapa.getTempoMaximoPermanenciaDias(), etapa.getCamposObrigatorios(),
                        negociosPorEtapa.getOrDefault(etapa.getIdEtapa(), List.of())
                ))
                .toList());
    }

    @Transactional
    public PipelineNegocioResponseDTO criar(Long usuarioId, PipelineNegocioRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CRIAR);
        LocalDateTime agora = LocalDateTime.now();
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setCriadoPor(buscarUsuario(usuarioId, "Usuário criador não encontrado"));
        negocio.setCriadoEm(agora);
        negocio.setUltimaMovimentacaoEm(agora);
        negocio.setResultado(PipelineVendasResultado.ABERTO);
        aplicarDados(negocio, dto, resolverEmpresa(dto));
        PipelineVendasEtapa etapa = resolverEtapaCriacao(dto);
        if (etapa.getResultado() != PipelineVendasResultado.ABERTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um negócio deve ser criado em uma etapa aberta");
        }
        negocio.setFunil(etapa.getFunil());
        negocio.setEtapa(etapa);
        negocio.setResultado(negocio.getEtapa().getResultado());
        cadastros.aplicar(negocio, dto);
        if (negocio.getFunil().isPreVendas()) negocio.setValorEstimadoProposta(null);
        PipelineCamposObrigatorios.validar(negocio, etapa);
        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        movimentacaoEtapaService.iniciar(salvo, agora);
        preVendas.sincronizar(salvo);
        historicoService.registrar(salvo, usuarioId, PipelineHistoricoTipo.CRIACAO_NEGOCIO,
                "Negócio criado na etapa " + salvo.getEtapa().getNome());
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO atualizar(Long negocioId, Long usuarioId, PipelineNegocioRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_EDITAR);
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        if (dto.funilId() != null && !Objects.equals(dto.funilId(), negocio.getFunil().getIdFunil())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível trocar o funil de um negócio existente");
        }
        if (negocio.getFunil().isPreVendas() && negocio.getResultado() != PipelineVendasResultado.ABERTO) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reative o lead antes de editar");
        NegocioSnapshot anterior = NegocioSnapshot.of(negocio);
        aplicarDados(negocio, dto, resolverEmpresa(dto));

        if (dto.etapaId() != null && !Objects.equals(dto.etapaId(), anterior.etapaId())) {
            exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_MOVIMENTAR);
            PipelineVendasEtapa novaEtapa = buscarEtapaDoFunil(dto.etapaId(), negocio.getFunil().getIdFunil());
            if (novaEtapa.getResultado() != PipelineVendasResultado.ABERTO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Use as ações de ganhar ou perder para concluir o negócio");
            }
            validarPropostaAntesDaMovimentacao(negocio, novaEtapa);
            PipelineCamposObrigatorios.validar(negocio, novaEtapa);
            LocalDateTime momento = LocalDateTime.now();
            aplicarEtapa(negocio, novaEtapa, momento);
            movimentacaoEtapaService.mudar(negocio, novaEtapa, momento, false);
        }

        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        cadastros.aplicar(salvo, dto);
        if (salvo.getFunil().isPreVendas()) salvo.setValorEstimadoProposta(null);
        preVendas.sincronizar(salvo);
        registrarAlteracoes(salvo, usuarioId, anterior);
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO mover(Long negocioId,
                                            Long usuarioId,
                                            Long etapaId,
                                            Long motivoPerdaId,
                                            String observacaoPerda) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_MOVIMENTAR);
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        if (negocio.getFunil().isPreVendas() && negocio.getResultado() != PipelineVendasResultado.ABERTO) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reative o lead antes de movimentar");
        PipelineVendasEtapa novaEtapa = buscarEtapaDoFunil(etapaId, negocio.getFunil().getIdFunil());
        if (Objects.equals(negocio.getEtapa().getIdEtapa(), etapaId)) return toResponse(negocio);
        exigirPermissaoDeConclusaoSeNecessario(usuarioId, negocio.getResultado(), novaEtapa.getResultado());
        validarPropostaAntesDaMovimentacao(negocio, novaEtapa);
        PipelineCamposObrigatorios.validar(negocio, novaEtapa);
        PipelineVendasEtapa etapaAnterior = negocio.getEtapa();
        PipelineVendasResultado resultadoAnterior = negocio.getResultado();
        LocalDateTime momento = LocalDateTime.now();
        prepararResultado(negocio, novaEtapa.getResultado(), motivoPerdaId, observacaoPerda, momento);
        aplicarEtapa(negocio, novaEtapa, momento);
        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        movimentacaoEtapaService.mudar(salvo, novaEtapa, momento,
                novaEtapa.getResultado() != PipelineVendasResultado.ABERTO);
        preVendas.sincronizar(salvo);
        registrarMovimentacao(salvo, usuarioId, etapaAnterior, resultadoAnterior);
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO marcarResultado(Long negocioId,
                                                       Long usuarioId,
                                                       PipelineVendasResultado resultado,
                                                       Long motivoPerdaId,
                                                       String observacaoPerda) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CONCLUIR);
        if (resultado == PipelineVendasResultado.ABERTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe se o negócio foi ganho ou perdido");
        }
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        if (negocio.getFunil().isPreVendas()) return concluirPreVenda(negocio, usuarioId, resultado, motivoPerdaId, observacaoPerda, null, null);
        PipelineVendasEtapa etapaFinal = etapaRepository
                .findFirstByFunilIdFunilAndResultadoAndAtivoTrueOrderByPosicaoAsc(
                        negocio.getFunil().getIdFunil(), resultado
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etapa final não configurada"));
        PipelineCamposObrigatorios.validar(negocio, etapaFinal);
        PipelineVendasEtapa etapaAnterior = negocio.getEtapa();
        PipelineVendasResultado resultadoAnterior = negocio.getResultado();
        LocalDateTime momento = LocalDateTime.now();
        prepararResultado(negocio, resultado, motivoPerdaId, observacaoPerda, momento);
        aplicarEtapa(negocio, etapaFinal, momento);
        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        movimentacaoEtapaService.mudar(salvo, etapaFinal, momento, true);
        preVendas.sincronizar(salvo);
        registrarMovimentacao(salvo, usuarioId, etapaAnterior, resultadoAnterior);
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO reativar(Long negocioId, Long usuarioId, Long etapaId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CONCLUIR);
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        if (negocio.getResultado() == PipelineVendasResultado.ABERTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O negócio já está ativo");
        }
        PipelineVendasEtapa novaEtapa = etapaId == null
                ? (negocio.getFunil().isPreVendas() ? negocio.getEtapa() : buscarEtapaInicialAberta(negocio.getFunil().getIdFunil()))
                : buscarEtapaDoFunil(etapaId, negocio.getFunil().getIdFunil());
        if (novaEtapa.getResultado() != PipelineVendasResultado.ABERTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma etapa aberta para reativar o negócio");
        }
        PipelineVendasEtapa etapaAnterior = negocio.getEtapa();
        PipelineVendasResultado resultadoAnterior = negocio.getResultado();
        LocalDateTime momento = LocalDateTime.now();
        prepararResultado(negocio, PipelineVendasResultado.ABERTO, null, null, momento);
        aplicarEtapa(negocio, novaEtapa, momento);
        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        movimentacaoEtapaService.mudar(salvo, novaEtapa, momento, false);
        preVendas.sincronizar(salvo);
        registrarMovimentacao(salvo, usuarioId, etapaAnterior, resultadoAnterior);
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO converterEmContrato(Long negocioId, Long usuarioId, Long empresaId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CONVERTER_CONTRATO);
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        if (negocio.getResultado() != PipelineVendasResultado.GANHO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Somente negócios ganhos podem ser convertidos em contrato");
        }
        if (negocio.getFunil().isPreVendas()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Converta o lead em negócio de vendas");
        if (negocio.getContrato() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este negócio já foi convertido em contrato");
        }

        Empresa empresa = resolverEmpresaConversao(negocio, empresaId);
        Usuario usuario = buscarUsuario(usuarioId, "Usuário não encontrado");
        Contrato contrato = contratoService.criarAPartirDoPipeline(empresa, usuario, negocio.getResponsavel());
        negocio.setEmpresa(empresa);
        negocio.setNomeEmpresa(empresa.getNomeFantasia());
        negocio.setContrato(contrato);
        negocio.setUltimaMovimentacaoEm(LocalDateTime.now());
        PipelineVendasNegocio salvo = negocioRepository.save(negocio);
        historicoService.registrar(salvo, usuarioId, PipelineHistoricoTipo.CONVERSAO_CONTRATO,
                "Negócio convertido no contrato CT-" + contrato.getIdContrato());
        return toResponse(salvo);
    }

    @Transactional
    public PipelineNegocioResponseDTO ganharPreVenda(Long id, Long usuarioId, Long funilId, Long responsavelId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CONCLUIR);
        PipelineVendasNegocio lead = buscarNegocio(id);
        if (!lead.getFunil().isPreVendas()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um lead de pré-vendas");
        return concluirPreVenda(lead, usuarioId, PipelineVendasResultado.GANHO, null, null, funilId, responsavelId);
    }

    private PipelineNegocioResponseDTO concluirPreVenda(PipelineVendasNegocio lead, Long usuarioId, PipelineVendasResultado resultado,
            Long motivoId, String observacao, Long funilId, Long responsavelId) {
        if (lead.getResultado() == resultado) return toResponse(lead);
        if (lead.getResultado() != PipelineVendasResultado.ABERTO) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reative o lead antes de alterar seu resultado");
        if (resultado == PipelineVendasResultado.GANHO) preVendas.converter(lead, usuarioId, funilId, responsavelId);
        prepararResultado(lead, resultado, motivoId, observacao, LocalDateTime.now());
        lead.setResultado(resultado); lead.setUltimaMovimentacaoEm(LocalDateTime.now());
        lead.setEncerradoEm(lead.getUltimaMovimentacaoEm()); negocioRepository.save(lead);
        movimentacaoEtapaService.mudar(lead, lead.getEtapa(), lead.getUltimaMovimentacaoEm(), true);
        preVendas.sincronizar(lead);
        historicoService.registrar(lead, usuarioId, resultado == PipelineVendasResultado.GANHO ? PipelineHistoricoTipo.FECHAMENTO : PipelineHistoricoTipo.PERDA, "Pré-venda encerrada: " + resultado);
        return toResponse(lead);
    }


    private void registrarAlteracoes(PipelineVendasNegocio negocio, Long usuarioId, NegocioSnapshot anterior) {
        if (!Objects.equals(anterior.responsavelId(), negocio.getResponsavel().getId())) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.ALTERACAO_RESPONSAVEL,
                    "Responsável alterado de " + anterior.responsavelNome() + " para " + negocio.getResponsavel().getNomeCompleto());
        }
        if (!Objects.equals(anterior.valorEstimado(), negocio.getValorEstimadoProposta())) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.ALTERACAO_VALOR_PROPOSTA,
                    "Valor estimado da proposta alterado");
        }
        if (anterior.dadosGeraisForamAlterados(negocio)) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.ALTERACAO_DADOS,
                    "Dados do negócio atualizados");
        }
        if (!Objects.equals(anterior.etapaId(), negocio.getEtapa().getIdEtapa())) {
            PipelineVendasEtapa etapaAnterior = new PipelineVendasEtapa();
            etapaAnterior.setIdEtapa(anterior.etapaId());
            etapaAnterior.setNome(anterior.etapaNome());
            registrarMovimentacao(negocio, usuarioId, etapaAnterior, anterior.resultado());
        }
    }

    private void registrarMovimentacao(PipelineVendasNegocio negocio,
                                       Long usuarioId,
                                       PipelineVendasEtapa etapaAnterior,
                                       PipelineVendasResultado resultadoAnterior) {
        historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.MOVIMENTACAO_ETAPA,
                "Movido de " + etapaAnterior.getNome() + " para " + negocio.getEtapa().getNome());
        if (resultadoAnterior != PipelineVendasResultado.GANHO && negocio.getResultado() == PipelineVendasResultado.GANHO) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.FECHAMENTO, "Negócio marcado como ganho");
        } else if (resultadoAnterior != PipelineVendasResultado.PERDIDO && negocio.getResultado() == PipelineVendasResultado.PERDIDO) {
            String detalhe = negocio.getMotivoPerda() == null ? "" : ": " + negocio.getMotivoPerda().getNome();
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.PERDA,
                    "Negócio marcado como perdido" + detalhe);
        } else if (resultadoAnterior != PipelineVendasResultado.ABERTO && negocio.getResultado() == PipelineVendasResultado.ABERTO) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.REATIVACAO, "Negócio reativado");
        }
    }

    private void aplicarDados(PipelineVendasNegocio negocio, PipelineNegocioRequestDTO dto, Empresa empresa) {
        negocio.setEmpresa(empresa);
        negocio.setNomeEmpresa(dto.nomeEmpresa().trim());
        negocio.setNomeContato(dto.nomeContato().trim());
        negocio.setTelefone(dto.telefone() == null ? "" : dto.telefone().trim());
        negocio.setEmail(dto.email() == null ? "" : dto.email().trim().toLowerCase());
        negocio.setResponsavel(buscarUsuario(dto.responsavelId(), "Responsável não encontrado"));
        negocio.setDataReuniao(dto.dataReuniao());
        negocio.setOrigemNegocio(dto.origemNegocio().trim());
        negocio.setEstrategiaComercial(dto.estrategiaComercial().trim());
        negocio.setServicosInteresse(normalizarServicos(dto));
        negocio.setValorEstimadoProposta(dto.valorEstimadoProposta());
        negocio.setObservacoes(normalizarTextoOpcional(dto.observacoes()));
    }

    private void aplicarEtapa(PipelineVendasNegocio negocio,
                              PipelineVendasEtapa etapa,
                              LocalDateTime momento) {
        negocio.setEtapa(etapa);
        negocio.setResultado(etapa.getResultado());
        negocio.setUltimaMovimentacaoEm(momento);
    }

    private void prepararResultado(PipelineVendasNegocio negocio,
                                    PipelineVendasResultado resultado,
                                    Long motivoPerdaId,
                                    String observacaoPerda,
                                    LocalDateTime momento) {
        if (resultado == PipelineVendasResultado.PERDIDO) {
            negocio.setMotivoPerda(motivoPerdaService.buscarAtivoObrigatorio(motivoPerdaId));
            negocio.setObservacaoPerda(normalizarTextoOpcional(observacaoPerda));
            negocio.setEncerradoEm(momento);
            return;
        }
        negocio.setMotivoPerda(null);
        negocio.setObservacaoPerda(null);
        negocio.setEncerradoEm(resultado == PipelineVendasResultado.GANHO ? momento : null);
    }

    private PipelineVendasNegocio buscarNegocio(Long negocioId) {
        return negocioRepository.findByIdForUpdate(negocioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negócio não encontrado"));
    }

    private PipelineVendasEtapa buscarEtapaInicialAberta(Long funilId) {
        return etapaRepository.findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(funilId).stream()
                .filter(etapa -> etapa.getResultado() == PipelineVendasResultado.ABERTO)
                .sorted(java.util.Comparator.comparingInt(etapa -> !etapa.getFunil().isPreVendas() && etapa.getCodigo().startsWith("DIAGNOSTICO") ? -1 : etapa.getPosicao()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pipeline comercial sem etapa inicial aberta"));
    }

    private PipelineVendasEtapa buscarEtapaDoFunil(Long etapaId, Long funilId) {
        return etapaRepository.findById(etapaId)
                .filter(etapa -> Boolean.TRUE.equals(etapa.getAtivo()) && Objects.equals(etapa.getFunil().getIdFunil(), funilId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa comercial não encontrada"));
    }

    private PipelineVendasEtapa resolverEtapaCriacao(PipelineNegocioRequestDTO dto) {
        if (dto.etapaId() != null) {
            PipelineVendasEtapa etapa = etapaRepository.findById(dto.etapaId())
                    .filter(item -> Boolean.TRUE.equals(item.getAtivo()) && Boolean.TRUE.equals(item.getFunil().getAtivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa comercial não encontrada"));
            if (dto.funilId() != null && !Objects.equals(dto.funilId(), etapa.getFunil().getIdFunil())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A etapa não pertence ao funil selecionado");
            }
            return etapa;
        }
        PipelineVendasFunil funil = buscarFunilAtivo(dto.funilId());
        return buscarEtapaInicialAberta(funil.getIdFunil());
    }

    private PipelineVendasFunil buscarFunilAtivo(Long funilId) {
        if (funilId != null) {
            return funilRepository.findById(funilId)
                    .filter(funil -> Boolean.TRUE.equals(funil.getAtivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funil comercial ativo não encontrado"));
        }
        return funilRepository.findByAtivoTrueOrderByPosicaoAsc().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum funil comercial ativo configurado"));
    }

    private void validarPropostaAntesDaMovimentacao(PipelineVendasNegocio negocio, PipelineVendasEtapa novaEtapa) {
        if (!etapaEhPropostaApresentada(novaEtapa)) return;
        if (propostaRepository.existsByNegocioIdNegocio(negocio.getIdNegocio())) return;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Crie uma proposta para este negócio antes de movê-lo para Proposta apresentada");
    }

    private boolean etapaEhPropostaApresentada(PipelineVendasEtapa etapa) {
        String codigo = etapa.getCodigo() == null ? "" : etapa.getCodigo().toUpperCase();
        return codigo.startsWith("PROPOSTA_APRESENTADA");
    }

    private Usuario buscarUsuario(Long usuarioId, String mensagem) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, mensagem));
    }

    private Empresa buscarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
    }

    private Empresa resolverEmpresa(PipelineNegocioRequestDTO dto) {
        if (dto.empresaId() != null) return buscarEmpresa(dto.empresaId());
        if (Boolean.TRUE.equals(dto.cadastrarEmpresa())) {
            return empresaCadastroService.cadastrar(dto);
        }
        return null;
    }

    private List<String> normalizarServicos(PipelineNegocioRequestDTO dto) {
        List<String> informados = dto.servicosInteresse() == null || dto.servicosInteresse().isEmpty()
                ? List.of(dto.servicoInteresse() == null ? "" : dto.servicoInteresse())
                : dto.servicosInteresse();
        LinkedHashSet<String> normalizados = new LinkedHashSet<>();
        informados.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(servico -> !servico.isBlank())
                .forEach(normalizados::add);
        return List.copyOf(normalizados);
    }

    private Empresa resolverEmpresaConversao(PipelineVendasNegocio negocio, Long empresaId) {
        if (empresaId != null) return buscarEmpresa(empresaId);
        if (negocio.getEmpresa() != null) return negocio.getEmpresa();
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma empresa cadastrada para gerar o contrato");
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão comercial para esta ação");
        }
    }

    private void exigirPermissaoDeConclusaoSeNecessario(Long usuarioId,
                                                         PipelineVendasResultado resultadoAnterior,
                                                         PipelineVendasResultado novoResultado) {
        if (resultadoAnterior != PipelineVendasResultado.ABERTO || novoResultado != PipelineVendasResultado.ABERTO) {
            exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_CONCLUIR);
        }
    }

    private String normalizarTextoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private PipelineNegocioResponseDTO toResponse(PipelineVendasNegocio negocio) {
        return toResponse(negocio, buscarIndicadoresProposta(List.of(negocio)));
    }

    private PipelineNegocioResponseDTO toResponse(PipelineVendasNegocio negocio, IndicadoresProposta indicadores) {
        return new PipelineNegocioResponseDTO(
                negocio.getIdNegocio(),
                negocio.getFunil().getIdFunil(), negocio.getFunil().getNome(),
                negocio.getEmpresa() != null ? negocio.getEmpresa().getIdEmpresa() : null,
                negocio.getNomeEmpresa(), negocio.getNomeContato(), negocio.getTelefone(), negocio.getEmail(),
                negocio.getResponsavel().getId(), negocio.getResponsavel().getNomeCompleto(),
                negocio.getEtapa().getIdEtapa(), negocio.getEtapa().getCodigo(), negocio.getEtapa().getNome(),
                negocio.getDataReuniao(), negocio.getOrigemNegocio(), negocio.getEstrategiaComercial(),
                negocio.getServicoInteresse(), negocio.getServicosInteresse(),
                negocio.getValorEstimadoProposta(), negocio.getObservacoes(),
                negocio.getResultado(),
                negocio.getMotivoPerda() == null ? null : negocio.getMotivoPerda().getIdMotivo(),
                negocio.getMotivoPerda() == null ? null : negocio.getMotivoPerda().getNome(),
                negocio.getObservacaoPerda(), negocio.getEncerradoEm(),
                negocio.getContrato() != null ? negocio.getContrato().getIdContrato() : null,
                indicadores.possuiProposta(negocio.getIdNegocio()),
                indicadores.possuiAjustesPendentes(negocio.getIdNegocio()),
                negocio.getCriadoEm(), negocio.getUltimaMovimentacaoEm(),
                negocio.getFunil().getTipo(), negocio.getPessoa() == null ? null : negocio.getPessoa().getIdPessoa(),
                negocio.getCampanhaOrigem() == null ? null : negocio.getCampanhaOrigem().getIdCampanha(),
                negocio.getCampanhaOrigem() == null ? null : negocio.getCampanhaOrigem().getNome(),
                negocio.getPreVendaOrigem() == null ? null : negocio.getPreVendaOrigem().getIdNegocio(),
                preVendas.negocioGerado(negocio), Set.copyOf(negocio.getTags()), Map.copyOf(negocio.getCampos()), preVendas.status(negocio)
        );
    }

    private IndicadoresProposta buscarIndicadoresProposta(List<PipelineVendasNegocio> negocios) {
        List<Long> negocioIds = negocios.stream()
                .map(PipelineVendasNegocio::getIdNegocio)
                .filter(Objects::nonNull)
                .toList();
        if (negocioIds.isEmpty()) return IndicadoresProposta.vazio();
        return new IndicadoresProposta(
                new HashSet<>(propostaRepository.findNegocioIdsComProposta(negocioIds)),
                new HashSet<>(revisaoDocumentoRepository.findNegocioIdsComAjustesSolicitados(negocioIds))
        );
    }

    private record IndicadoresProposta(Set<Long> negocioIdsComProposta, Set<Long> negocioIdsComAjustes) {
        static IndicadoresProposta vazio() {
            return new IndicadoresProposta(Set.of(), Set.of());
        }

        boolean possuiProposta(Long negocioId) {
            return negocioId != null && negocioIdsComProposta.contains(negocioId);
        }

        boolean possuiAjustesPendentes(Long negocioId) {
            return negocioId != null && negocioIdsComAjustes.contains(negocioId);
        }
    }

    private record NegocioSnapshot(
            Long empresaId,
            String nomeEmpresa,
            String nomeContato,
            String telefone,
            String email,
            Long responsavelId,
            String responsavelNome,
            LocalDateTime dataReuniao,
            String origem,
            String estrategia,
            String servico,
            BigDecimal valorEstimado,
            String observacoes,
            Long etapaId,
            String etapaNome,
            PipelineVendasResultado resultado,
            Long pessoaId,
            Long campanhaId,
            Set<Long> tags,
            Map<Long, String> campos
    ) {
        static NegocioSnapshot of(PipelineVendasNegocio negocio) {
            return new NegocioSnapshot(
                    negocio.getEmpresa() == null ? null : negocio.getEmpresa().getIdEmpresa(),
                    negocio.getNomeEmpresa(), negocio.getNomeContato(), negocio.getTelefone(), negocio.getEmail(),
                    negocio.getResponsavel().getId(), negocio.getResponsavel().getNomeCompleto(),
                    negocio.getDataReuniao(), negocio.getOrigemNegocio(), negocio.getEstrategiaComercial(),
                    negocio.getServicoInteresse(), negocio.getValorEstimadoProposta(), negocio.getObservacoes(),
                    negocio.getEtapa().getIdEtapa(), negocio.getEtapa().getNome(), negocio.getResultado(),
                    negocio.getPessoa() == null ? null : negocio.getPessoa().getIdPessoa(),
                    negocio.getCampanhaOrigem() == null ? null : negocio.getCampanhaOrigem().getIdCampanha(),
                    negocio.getTags().stream().map(PipelineTag::getId).collect(Collectors.toSet()), Map.copyOf(negocio.getCampos())
            );
        }

        boolean dadosGeraisForamAlterados(PipelineVendasNegocio negocio) {
            Long empresaAtual = negocio.getEmpresa() == null ? null : negocio.getEmpresa().getIdEmpresa();
            return !Objects.equals(empresaId, empresaAtual)
                    || !Objects.equals(nomeEmpresa, negocio.getNomeEmpresa())
                    || !Objects.equals(nomeContato, negocio.getNomeContato())
                    || !Objects.equals(telefone, negocio.getTelefone())
                    || !Objects.equals(email, negocio.getEmail())
                    || !Objects.equals(dataReuniao, negocio.getDataReuniao())
                    || !Objects.equals(origem, negocio.getOrigemNegocio())
                    || !Objects.equals(estrategia, negocio.getEstrategiaComercial())
                    || !Objects.equals(servico, negocio.getServicoInteresse())
                    || !Objects.equals(observacoes, negocio.getObservacoes())
                    || !Objects.equals(pessoaId, negocio.getPessoa() == null ? null : negocio.getPessoa().getIdPessoa())
                    || !Objects.equals(campanhaId, negocio.getCampanhaOrigem() == null ? null : negocio.getCampanhaOrigem().getIdCampanha())
                    || !Objects.equals(tags, negocio.getTags().stream().map(PipelineTag::getId).collect(Collectors.toSet()))
                    || !Objects.equals(campos, negocio.getCampos());
        }
    }
    @Transactional
    public PipelineNegocioResponseDTO buscar(Long id, Long usuario) { exigirPermissao(usuario, PermissaoCodigo.COMERCIAL); return toResponse(buscarNegocio(id)); }
    @Transactional
    public List<PipelineNegocioResponseDTO> criarLote(Long usuario, List<PipelineNegocioRequestDTO> dados) {
        exigirPermissao(usuario, PermissaoCodigo.COMERCIAL_CRIAR);
        if (dados == null || dados.isEmpty() || dados.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione de 1 a 100 pessoas");
        for (var dto : dados) {
            if (dto.pessoaId() == null || !resolverEtapaCriacao(dto).getFunil().isPreVendas()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O lote deve conter pessoas em pré-vendas");
        }
        return dados.stream().map(dto -> criar(usuario, dto)).toList();
    }
    @Transactional
    public void reiniciarCadencia(Long id, Long usuario) { preVendas.reiniciar(id, usuario); }

}
