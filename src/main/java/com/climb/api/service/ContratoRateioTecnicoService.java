package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ContratoRateioTecnicoService {
    private final ContratoRepository contratos;
    private final ContratoEquipeRepository equipe;
    private final ContratoRateioTecnicoRepository participantes;
    private final ContratoApoioAtuacaoRepository atuacoes;
    private final ContratoComissaoTecnicaCalculator calculator;
    private final Clock clock;
    private final ContratoEquipeService acesso;

    @Autowired
    public ContratoRateioTecnicoService(ContratoRepository contratos, ContratoEquipeRepository equipe,
            ContratoRateioTecnicoRepository participantes, ContratoApoioAtuacaoRepository atuacoes,
            ContratoComissaoTecnicaCalculator calculator, ContratoEquipeService acesso) {
        this(contratos, equipe, participantes, atuacoes, calculator, acesso, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }
    ContratoRateioTecnicoService(ContratoRepository contratos, ContratoEquipeRepository equipe,
            ContratoRateioTecnicoRepository participantes, ContratoApoioAtuacaoRepository atuacoes,
            ContratoComissaoTecnicaCalculator calculator, ContratoEquipeService acesso, Clock clock) {
        this.contratos = contratos; this.equipe = equipe; this.participantes = participantes;
        this.calculator = calculator; this.clock = clock;
        this.acesso = acesso;
        this.atuacoes = atuacoes;
    }

    @Transactional(readOnly = true)
    public ContratoRateioTecnicoResponseDTO consultar(Long id, String competencia, Long usuarioId) {
        Contrato contrato = contratos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        acesso.exigirAcesso(contrato, usuarioId, false);
        try { return consultar(contrato, YearMonth.parse(competencia)); }
        catch (java.time.format.DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o mês do rateio técnico."); }
    }

    @Transactional
    public void registrarAtuacao(ContratoKanbanTask tarefa) {
        Contrato contrato = contratos.findByIdForUpdate(tarefa.getContrato().getIdContrato()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        LocalDateTime agora = LocalDateTime.now(clock);
        Set<YearMonth> meses = new TreeSet<>();
        atuacoes.findByTarefa_IdTask(tarefa.getIdTask()).forEach(p -> meses.addAll(mesesDoPeriodo(p, agora)));
        if (tarefa.getRaia().isConcluiTarefas() && tarefa.getConcluidaEm() == null) {
            tarefa.setConcluidaEm(agora);
            meses.add(YearMonth.from(agora));
        }
        meses.forEach(mes -> registrarMes(contrato, mes));
    }

    private void registrarMes(Contrato contrato, YearMonth mes) {
        LocalDate competencia = mes.atDay(1);
        var registros = participantes.findByContrato_IdContratoAndCompetencia(contrato.getIdContrato(), competencia);
        Set<Long> registrados = new HashSet<>();
        registros.forEach(p -> registrados.add(p.getUsuario().getId()));
        beneficiarios(contrato, mes, registros).values().forEach(u -> {
            if (registrados.contains(u.getId())) return;
            ContratoRateioTecnicoParticipante p = new ContratoRateioTecnicoParticipante();
            p.setContrato(contrato); p.setUsuario(u); p.setCompetencia(competencia); participantes.save(p);
        });
    }

    @Transactional(readOnly = true)
    public ContratoRateioTecnicoResponseDTO consultar(Contrato contrato, YearMonth competencia) {
        var servicos = calculator.calcular(contrato, competencia);
        BigDecimal recebido = servicos.stream().map(ContratoRateioTecnicoResponseDTO.Servico::recebimento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal comissao = servicos.stream().map(ContratoRateioTecnicoResponseDTO.Servico::comissao).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        var registros = participantes.findByContrato_IdContratoAndCompetencia(contrato.getIdContrato(), competencia.atDay(1));
        Map<Long, Usuario> usuarios = beneficiarios(contrato, competencia, registros);
        List<ContratoRateioTecnicoResponseDTO.Participante> valores = dividir(comissao, usuarios.values());
        return new ContratoRateioTecnicoResponseDTO(contrato.getIdContrato(), competencia.toString(), competencia.plusMonths(1).toString(),
                recebido.setScale(2), comissao, !registros.isEmpty(), servicos, valores);
    }

    @Transactional(readOnly = true)
    public List<ContratoRateioTecnicoResponseDTO> listar(Contrato contrato) {
        if (!contrato.isEquipeConfigurada()) return List.of();
        Set<YearMonth> meses = new TreeSet<>();
        contrato.getParcelas().forEach(p -> {
            var data = p.getCompetencia() != null ? p.getCompetencia() : p.getVencimento();
            if (data != null) meses.add(YearMonth.from(data));
        });
        participantes.findByContrato_IdContrato(contrato.getIdContrato()).forEach(p -> meses.add(YearMonth.from(p.getCompetencia())));
        LocalDateTime agora = LocalDateTime.now(clock);
        atuacoes.findByContrato_IdContrato(contrato.getIdContrato()).forEach(p -> meses.addAll(mesesDoPeriodo(p, agora)));
        return meses.stream().map(m -> consultar(contrato, m)).toList();
    }

    private Set<YearMonth> mesesDoPeriodo(ContratoApoioAtuacao periodo, LocalDateTime agora) {
        Set<YearMonth> meses = new TreeSet<>();
        LocalDateTime fim = periodo.getFim() == null || periodo.getFim().isAfter(agora) ? agora : periodo.getFim();
        for (YearMonth mes = YearMonth.from(periodo.getInicio()); !mes.isAfter(YearMonth.from(fim)); mes = mes.plusMonths(1)) meses.add(mes);
        return meses;
    }

    private Map<Long, Usuario> beneficiarios(Contrato contrato, YearMonth mes, List<ContratoRateioTecnicoParticipante> registros) {
        Map<Long, Usuario> resultado = new TreeMap<>();
        registros.forEach(p -> resultado.put(p.getUsuario().getId(), p.getUsuario()));
        YearMonth atual = YearMonth.now(clock);
        if (registros.isEmpty() || mes.equals(atual)) resultado.putAll(fixos(contrato));
        if (!mes.isAfter(atual)) atuacoes.buscarUsuariosNoMes(contrato.getIdContrato(), mes.atDay(1).atStartOfDay(),
                mes.plusMonths(1).atDay(1).atStartOfDay()).forEach(u -> resultado.put(u.getId(), u));
        return resultado;
    }

    private Map<Long, Usuario> fixos(Contrato contrato) {
        Map<Long, Usuario> fixos = new TreeMap<>();
        equipe.findByContrato_IdContrato(contrato.getIdContrato()).forEach(m -> fixos.put(m.getUsuario().getId(), m.getUsuario()));
        if (contrato.getResponsavel() != null) fixos.put(contrato.getResponsavel().getId(), contrato.getResponsavel());
        return fixos;
    }
    private List<ContratoRateioTecnicoResponseDTO.Participante> dividir(BigDecimal total, Collection<Usuario> usuarios) {
        if (usuarios.isEmpty()) return List.of();
        long centavos = total.movePointRight(2).longValueExact();
        long base = centavos / usuarios.size();
        long sobra = centavos % usuarios.size();
        List<ContratoRateioTecnicoResponseDTO.Participante> resultado = new ArrayList<>();
        int indice = 0;
        for (Usuario usuario : usuarios) {
            BigDecimal valor = BigDecimal.valueOf(base + (indice++ < sobra ? 1 : 0), 2);
            resultado.add(new ContratoRateioTecnicoResponseDTO.Participante(
                    new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail()), valor));
        }
        return resultado;
    }
}
