package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.PipelineTarefaNotificacaoTipo;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class PipelineTarefaReminderService {

    private static final Logger log = LoggerFactory.getLogger(PipelineTarefaReminderService.class);
    private static final List<Integer> OVERDUE_MILESTONES = List.of(1, 3, 7);

    private final PipelineVendasTarefaRepository tarefaRepository;
    private final PipelineTarefaReminderLedger ledger;
    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineTarefaReminderEmail reminderEmail;
    private final ZoneId zoneId;

    public PipelineTarefaReminderService(
            PipelineVendasTarefaRepository tarefaRepository,
            PipelineTarefaReminderLedger ledger,
            CargoRepository cargoRepository,
            UsuarioRepository usuarioRepository,
            PipelineTarefaReminderEmail reminderEmail,
            @Value("${app.pipeline.task-reminders.zone:America/Sao_Paulo}") String zone
    ) {
        this.tarefaRepository = tarefaRepository;
        this.ledger = ledger;
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reminderEmail = reminderEmail;
        this.zoneId = ZoneId.of(zone);
    }

    @Scheduled(
            cron = "${app.pipeline.task-reminders.weekly-cron:0 0 8 * * *}",
            zone = "${app.pipeline.task-reminders.zone:America/Sao_Paulo}"
    )
    public void enviarResumoSemanal() {
        processarResumoSemanal(LocalDate.now(zoneId));
    }

    @Scheduled(
            cron = "${app.pipeline.task-reminders.overdue-cron:0 10 8 * * *}",
            zone = "${app.pipeline.task-reminders.zone:America/Sao_Paulo}"
    )
    public void enviarAlertasDeAtraso() {
        processarAlertasDeAtraso(LocalDate.now(zoneId));
    }

    void processarResumoSemanal(LocalDate hoje) {
        List<PipelineVendasTarefa> tarefas = tarefaRepository.findAbertasComPrazoAte(hoje.plusDays(5));
        String referencia = PipelineTarefaReminderKey.weeklyReference(hoje);
        Map<Usuario, List<PipelineVendasTarefa>> porResponsavel = agruparPorResponsavel(tarefas);
        Set<String> existentes = ledger.buscarChaves(porResponsavel.keySet().stream()
                .map(usuario -> PipelineTarefaReminderKey.weekly(usuario.getId(), referencia))
                .toList());

        porResponsavel.forEach((responsavel, lista) -> {
            String chave = PipelineTarefaReminderKey.weekly(responsavel.getId(), referencia);
            if (existentes.contains(chave)) return;
            executarEnvio(responsavel, lista, chave, referencia,
                    PipelineTarefaNotificacaoTipo.RESUMO_SEMANAL,
                    (destinatario, tarefasDoEmail) -> reminderEmail.enviarResumoSemanal(
                            destinatario, tarefasDoEmail, hoje));
        });
    }

    void processarAlertasDeAtraso(LocalDate hoje) {
        List<PipelineVendasTarefa> atrasadas = tarefaRepository.findAbertasComPrazoAte(hoje.minusDays(1));
        processarMarcosDeAtraso(atrasadas, hoje);
        processarEscalacoes(atrasadas, hoje);
    }

    private void processarMarcosDeAtraso(List<PipelineVendasTarefa> tarefas, LocalDate hoje) {
        List<PipelineTarefaReminderDelivery> entregas = tarefas.stream()
                .flatMap(tarefa -> entregasDeAtraso(tarefa, hoje).stream())
                .toList();
        List<PipelineTarefaReminderDelivery> pendentes = ledger.filtrarPendentes(entregas);
        pendentes.stream().collect(Collectors.groupingBy(
                PipelineTarefaReminderDelivery::destinatario,
                LinkedHashMap::new,
                Collectors.toList()
        )).forEach((destinatario, grupo) -> enviarGrupo(
                destinatario,
                grupo,
                (usuario, lista) -> reminderEmail.enviarLembreteAtrasos(usuario, lista, hoje)
        ));
    }

    private void processarEscalacoes(List<PipelineVendasTarefa> tarefas, LocalDate hoje) {
        List<PipelineTarefaReminderDelivery> entregas = tarefas.stream()
                .filter(tarefa -> diasAtraso(tarefa, hoje) > 7)
                .flatMap(tarefa -> superioresDiretos(tarefa.getResponsavel()).stream()
                        .map(superior -> entrega(tarefa, superior,
                                PipelineTarefaNotificacaoTipo.ESCALACAO_SUPERIOR,
                                tarefa.getPrazo().toString())))
                .toList();
        ledger.filtrarPendentes(entregas).stream().collect(Collectors.groupingBy(
                PipelineTarefaReminderDelivery::destinatario,
                LinkedHashMap::new,
                Collectors.toList()
        )).forEach((superior, grupo) -> enviarGrupo(
                superior,
                grupo,
                (usuario, lista) -> reminderEmail.enviarEscalacao(usuario, lista, hoje)
        ));
    }

    private List<PipelineTarefaReminderDelivery> entregasDeAtraso(PipelineVendasTarefa tarefa, LocalDate hoje) {
        long dias = diasAtraso(tarefa, hoje);
        return OVERDUE_MILESTONES.stream()
                .filter(marco -> dias >= marco)
                .map(marco -> entrega(tarefa, tarefa.getResponsavel(), tipoDoMarco(marco), tarefa.getPrazo().toString()))
                .toList();
    }

    private List<Usuario> superioresDiretos(Usuario responsavel) {
        Cargo cargo = responsavel.getCargo();
        if (cargo == null || cargo.getCargoSuperiorId() == null) return List.of();
        if (cargoRepository.findByIdAndAtivoTrue(cargo.getCargoSuperiorId()).isEmpty()) return List.of();
        return usuarioRepository.findAllBySituacaoAndCargo_IdOrderByNomeCompletoAsc(
                "ATIVO", cargo.getCargoSuperiorId());
    }

    private void enviarGrupo(Usuario destinatario,
                             List<PipelineTarefaReminderDelivery> entregas,
                             BiFunction<Usuario, List<PipelineVendasTarefa>, Boolean> enviar) {
        List<PipelineVendasTarefa> tarefas = entregas.stream()
                .map(PipelineTarefaReminderDelivery::tarefa)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PipelineVendasTarefa::getIdTarefa, tarefa -> tarefa,
                                (primeira, ignorada) -> primeira, LinkedHashMap::new),
                        mapa -> List.copyOf(mapa.values())
                ));
        try {
            if (!enviar.apply(destinatario, tarefas)) return;
            ledger.registrarTodas(entregas);
        } catch (RuntimeException exception) {
            log.error("Falha ao processar lembretes de tarefas para o usuário {}", destinatario.getId(), exception);
        }
    }

    private void executarEnvio(Usuario destinatario,
                               List<PipelineVendasTarefa> tarefas,
                               String chave,
                               String referencia,
                               PipelineTarefaNotificacaoTipo tipo,
                               BiFunction<Usuario, List<PipelineVendasTarefa>, Boolean> enviar) {
        try {
            if (!enviar.apply(destinatario, tarefas)) return;
            PipelineTarefaReminderDelivery entrega = new PipelineTarefaReminderDelivery(
                    null, destinatario, tipo, referencia, chave);
            ledger.registrar(entrega);
        } catch (RuntimeException exception) {
            log.error("Falha ao processar resumo semanal para o usuário {}", destinatario.getId(), exception);
        }
    }

    private Map<Usuario, List<PipelineVendasTarefa>> agruparPorResponsavel(List<PipelineVendasTarefa> tarefas) {
        return tarefas.stream().collect(Collectors.groupingBy(
                PipelineVendasTarefa::getResponsavel,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private PipelineTarefaReminderDelivery entrega(PipelineVendasTarefa tarefa,
                            Usuario destinatario,
                            PipelineTarefaNotificacaoTipo tipo,
                            String referencia) {
        String chave = PipelineTarefaReminderKey.task(
                tipo, tarefa.getIdTarefa(), destinatario.getId(), referencia);
        return new PipelineTarefaReminderDelivery(tarefa, destinatario, tipo, referencia, chave);
    }

    private long diasAtraso(PipelineVendasTarefa tarefa, LocalDate hoje) {
        return ChronoUnit.DAYS.between(tarefa.getPrazo(), hoje);
    }

    private PipelineTarefaNotificacaoTipo tipoDoMarco(int marco) {
        return switch (marco) {
            case 1 -> PipelineTarefaNotificacaoTipo.ATRASO_1_DIA;
            case 3 -> PipelineTarefaNotificacaoTipo.ATRASO_3_DIAS;
            case 7 -> PipelineTarefaNotificacaoTipo.ATRASO_7_DIAS;
            default -> throw new IllegalArgumentException("Marco de atraso inválido: " + marco);
        };
    }

}
