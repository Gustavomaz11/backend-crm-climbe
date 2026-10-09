package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContratoRateioTecnicoServiceTest {
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoEquipeRepository equipe = mock(ContratoEquipeRepository.class);
    private final ContratoRateioTecnicoRepository registros = mock(ContratoRateioTecnicoRepository.class);
    private final ContratoApoioAtuacaoRepository atuacoes = mock(ContratoApoioAtuacaoRepository.class);
    private final List<ContratoRateioTecnicoParticipante> salvos = new ArrayList<>();
    private final List<ContratoApoioAtuacao> periodos = new ArrayList<>();
    private ContratoRateioTecnicoService service;
    private Contrato contrato;
    private Usuario lider, ana, apoio;
    private ContratoKanbanTask task;
    private ContratoApoioAtuacao periodo;

    @BeforeEach void setup() {
        lider = usuario(1L); ana = usuario(2L); apoio = usuario(3L);
        contrato = new Contrato(); contrato.setIdContrato(10L); contrato.setStatus("APROVADO");
        contrato.setEquipeConfigurada(true); contrato.setResponsavel(lider);
        when(contratos.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        var m = new ContratoEquipeMembro(); m.setUsuario(ana);
        when(equipe.findByContrato_IdContrato(10L)).thenReturn(List.of(m));
        when(registros.findByContrato_IdContratoAndCompetencia(eq(10L), any())).thenAnswer(i -> salvos.stream()
                .filter(p -> p.getCompetencia().equals(i.getArgument(1))).toList());
        when(registros.findByContrato_IdContrato(10L)).thenAnswer(i -> List.copyOf(salvos));
        when(registros.save(any())).thenAnswer(i -> { ContratoRateioTecnicoParticipante p = i.getArgument(0); salvos.add(p); return p; });
        when(atuacoes.findByTarefa_IdTask(anyLong())).thenAnswer(i -> periodos.stream()
                .filter(p -> p.getTarefa() != null && p.getTarefa().getIdTask().equals(i.getArgument(0))).toList());
        when(atuacoes.findByContrato_IdContrato(10L)).thenAnswer(i -> List.copyOf(periodos));
        when(atuacoes.buscarUsuariosNoMes(eq(10L), any(), any())).thenAnswer(i -> {
            LocalDateTime inicio = i.getArgument(1), proximo = i.getArgument(2);
            Map<Long, Usuario> pessoas = new TreeMap<>();
            periodos.stream().filter(p -> p.getInicio().isBefore(proximo) && (p.getFim() == null || !p.getFim().isBefore(inicio)))
                    .forEach(p -> pessoas.put(p.getUsuario().getId(), p.getUsuario()));
            return List.copyOf(pessoas.values());
        });
        task = new ContratoKanbanTask(); task.setIdTask(20L); task.setContrato(contrato);
        task.setCriadoEm(LocalDateTime.of(2026, 10, 1, 9, 0)); task.setDataInicio(LocalDate.of(2027, 1, 1));
        task.setResponsaveis(List.of(apoio)); task.getApoios().add(apoio);
        var raia = new ContratoKanbanRaia(); raia.setConcluiTarefas(true); task.setRaia(raia);
        periodo = periodo(task, apoio, "2026-11-01T09:00", "2026-11-09T09:00"); periodos.add(periodo);
        agora("2026-11-09T12:00:00Z");
    }

    @Test void apoioParticipaDoRateioDeNovembroEnquantoATarefaAindaEstaAberta() {
        agora("2026-11-20T12:00:00Z");
        periodo.setInicio(LocalDateTime.of(2026, 11, 15, 9, 0)); periodo.setFim(null);
        task.getRaia().setConcluiTarefas(false); propostaSimples();
        service.registrarAtuacao(task);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 11)).participantes())
                .extracting(p -> p.usuario().id()).containsExactly(1L, 2L, 3L);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 12)).participantes())
                .extracting(p -> p.usuario().id()).containsExactly(1L, 2L);
        assertThat(task.getConcluidaEm()).isNull();
    }

    @Test void deQuinzeDeNovembroAOitoDeDezembroRecebeNovembroEmDezembroEDezembroEmJaneiro() {
        agora("2026-12-08T12:00:00Z"); propostaSimples();
        periodo.setInicio(LocalDateTime.of(2026, 11, 15, 9, 0)); periodo.setFim(LocalDateTime.of(2026, 12, 8, 9, 0));
        service.registrarAtuacao(task);
        var novembro = service.consultar(contrato, YearMonth.of(2026, 11));
        var dezembro = service.consultar(contrato, YearMonth.of(2026, 12));
        assertThat(novembro.competenciaPagamento()).isEqualTo("2026-12");
        assertThat(dezembro.competenciaPagamento()).isEqualTo("2027-01");
        for (var rateio : List.of(novembro, dezembro)) {
            assertThat(rateio.participantes()).extracting(p -> p.valor()).containsExactly(
                    new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"));
            assertThat(rateio.comissaoTecnicaTotal()).isEqualByComparingTo("300.00");
        }
        assertThat(service.consultar(contrato, YearMonth.of(2026, 10)).participantes()).hasSize(2);
        assertThat(service.consultar(contrato, YearMonth.of(2027, 1)).participantes()).hasSize(2);
    }

    @Test void mesesIntermediariosSemMovimentacaoTambemIncluemOApoio() {
        agora("2027-02-08T12:00:00Z"); propostaSimples();
        periodo.setInicio(LocalDateTime.of(2026, 11, 15, 9, 0)); periodo.setFim(null);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 12)).participantes()).hasSize(3);
        assertThat(service.consultar(contrato, YearMonth.of(2027, 1)).participantes()).hasSize(3);
        assertThat(service.listar(contrato)).extracting(p -> p.competencia()).contains("2027-01", "2027-02");
    }

    @Test void variasTarefasNoMesmoMesNaoDuplicamAPessoa() {
        propostaSimples();
        var segunda = new ContratoKanbanTask(); segunda.setIdTask(21L); segunda.setContrato(contrato); segunda.setRaia(task.getRaia());
        periodos.add(periodo(segunda, apoio, "2026-11-05T09:00", "2026-11-09T09:00"));
        service.registrarAtuacao(task); service.registrarAtuacao(task); service.registrarAtuacao(segunda);
        assertThat(salvos).hasSize(3);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 11)).participantes()).hasSize(3);
    }

    @Test void reabrirGeraNovoPeriodoSemCobrarOsMesesEmQueATarefaFicouConcluida() {
        propostaSimples(); service.registrarAtuacao(task);
        task.getRaia().setConcluiTarefas(false); agora("2027-01-05T12:00:00Z");
        var reabertura = periodo(task, apoio, "2027-01-05T09:00", null); periodos.add(reabertura);
        service.registrarAtuacao(task);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 12)).participantes()).hasSize(2);
        assertThat(service.consultar(contrato, YearMonth.of(2027, 1)).participantes()).hasSize(3);
        assertThat(service.consultar(contrato, YearMonth.of(2027, 2)).participantes()).hasSize(2);
    }

    @Test void removerResponsavelOuExcluirTarefaPreservaOsMesesJaTrabalhados() {
        propostaSimples(); periodo.setTarefa(null); periodo.setFim(LocalDateTime.of(2026, 11, 9, 9, 0));
        task.setResponsaveis(List.of(ana)); task.getApoios().clear();
        assertThat(service.consultar(contrato, YearMonth.of(2026, 11)).participantes()).hasSize(3);
        assertThat(service.consultar(contrato, YearMonth.of(2026, 12)).participantes()).hasSize(2);
    }

    @Test void respeitaValoresMensaisEPercentuaisDeCadaServico() {
        var proposta = new Proposta(); proposta.setValuation(new BigDecimal("50000"));
        proposta.setServicos(List.of(new PropostaServico(ServicoComercial.CONTABILIDADE, new BigDecimal("30000"), new BigDecimal("25"), new BigDecimal("20")),
                new PropostaServico(ServicoComercial.VALUATION, new BigDecimal("20000"), new BigDecimal("10"), new BigDecimal("20"))));
        proposta.setRecebimentosPorServico(List.of(new PropostaRecebimentoServico(1, ServicoComercial.CONTABILIDADE, new BigDecimal("1000")),
                new PropostaRecebimentoServico(1, ServicoComercial.VALUATION, new BigDecimal("2000"))));
        contrato.setProposta(proposta); contrato.setParcelas(List.of(parcela(1, "2026-11-01", "3000"), parcela(2, "2026-12-01", "5000")));
        service.registrarAtuacao(task);
        var novembro = service.consultar(contrato, YearMonth.of(2026, 11));
        assertThat(novembro.comissaoTecnicaTotal()).isEqualByComparingTo("450.00");
        assertThat(novembro.participantes()).extracting(p -> p.valor()).containsExactly(
                new BigDecimal("150.00"), new BigDecimal("150.00"), new BigDecimal("150.00"));
        assertThat(service.consultar(contrato, YearMonth.of(2026, 12)).comissaoTecnicaTotal()).isEqualByComparingTo("950.00");
    }

    @ParameterizedTest @EnumSource(ServicoComercial.class)
    void suportaQualquerServicoEConservaCentavosDoRateio(ServicoComercial servico) {
        var p = new Proposta(); p.setServico(servico); p.setValuation(new BigDecimal("100.01")); p.setComissaoTecnicoPercentual(new BigDecimal("100"));
        contrato.setProposta(p); contrato.setParcelas(List.of(parcela(1, "2026-11-01", "100.01")));
        service.registrarAtuacao(task);
        var rateio = service.consultar(contrato, YearMonth.of(2026, 11));
        assertThat(rateio.participantes()).extracting(item -> item.valor()).containsExactly(
                new BigDecimal("33.34"), new BigDecimal("33.34"), new BigDecimal("33.33"));
        assertThat(rateio.participantes().stream().map(item -> item.valor()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.01");
    }

    private void agora(String instante) {
        service = new ContratoRateioTecnicoService(contratos, equipe, registros, atuacoes, new ContratoComissaoTecnicaCalculator(),
                mock(ContratoEquipeService.class), Clock.fixed(Instant.parse(instante), ZoneId.of("America/Sao_Paulo")));
    }
    private void propostaSimples() {
        var p = new Proposta(); p.setServico(ServicoComercial.CONTABILIDADE); p.setValuation(new BigDecimal("600"));
        p.setComissaoTecnicoPercentual(new BigDecimal("100")); contrato.setProposta(p);
        contrato.setParcelas(List.of(parcela(1, "2026-11-01", "300"), parcela(2, "2026-12-01", "300")));
    }
    private ContratoApoioAtuacao periodo(ContratoKanbanTask tarefa, Usuario pessoa, String inicio, String fim) {
        var p = new ContratoApoioAtuacao(); p.setContrato(contrato); p.setTarefa(tarefa); p.setUsuario(pessoa);
        p.setInicio(LocalDateTime.parse(inicio)); p.setFim(fim == null ? null : LocalDateTime.parse(fim)); return p;
    }
    private Usuario usuario(Long id) { var u = new Usuario(); u.setId(id); u.setNomeCompleto("Pessoa " + id); return u; }
    private ContratoParcela parcela(int numero, String data, String valor) {
        var p = new ContratoParcela(); p.setNumero(numero); p.setCompetencia(LocalDate.parse(data));
        p.setVencimento(LocalDate.parse(data)); p.setValor(new BigDecimal(valor)); return p;
    }
}
