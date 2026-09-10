package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class PipelineCancelamentoRelatorioService {
    private final PipelineVendasTarefaRepository tarefas;
    private final RbacService rbac;
    public PipelineCancelamentoRelatorioService(PipelineVendasTarefaRepository tarefas, RbacService rbac) { this.tarefas = tarefas; this.rbac = rbac; }
    public record Linha(Long tarefaId, String titulo, String contato, String campanha, String responsavel, String motivo, String comentario, LocalDateTime canceladoEm) {}
    public record Relatorio(long total, Map<String, Long> porMotivo, List<Linha> tarefas) {}
    @Transactional(readOnly = true)
    public Relatorio buscar(Long usuario, LocalDate inicio, LocalDate fim, Long campanhaId, Long responsavelId) {
        if (!rbac.temPermissao(usuario, PermissaoCodigo.COMERCIAL_DASHBOARD_VISUALIZAR)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para o dashboard");
        if (inicio != null && fim != null && inicio.isAfter(fim)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
        var lista = tarefas.findCancelamentos(inicio == null ? null : inicio.atStartOfDay(), fim == null ? null : fim.plusDays(1).atStartOfDay(), campanhaId, responsavelId);
        Map<String, Long> motivos = new LinkedHashMap<>();
        var linhas = lista.stream().map(t -> {
            String motivo = t.getMotivoCancelamento() == null ? "SEM_MOTIVO_LEGADO" : t.getMotivoCancelamento();
            motivos.merge(motivo, 1L, Long::sum);
            PipelineCampanha campanha = t.getCampanha() == null ? t.getNegocio().getCampanhaOrigem() : t.getCampanha();
            return new Linha(t.getIdTarefa(), t.getTitulo(), t.getNegocio().getNomeContato(), campanha == null ? null : campanha.getNome(), t.getResponsavel().getNomeCompleto(), motivo, t.getComentarioCancelamento(), t.getCanceladoEm());
        }).toList();
        return new Relatorio(linhas.size(), motivos, linhas);
    }
}
