package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.ContratoServicoEncerramentoResponseDTO;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.ContratoKanbanTaskRepository;
import com.climb.api.repository.ContratoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class ContratoServicoEncerramentoService {
    private final ContratoRepository contratos;
    private final ContratoKanbanTaskRepository tarefas;
    private final ContratoApoioAtuacaoService atuacoes;
    private final ContratoRateioTecnicoService rateio;
    private final ApplicationEventPublisher eventos;

    public ContratoServicoEncerramentoService(ContratoRepository contratos, ContratoKanbanTaskRepository tarefas,
            ContratoApoioAtuacaoService atuacoes, ContratoRateioTecnicoService rateio, ApplicationEventPublisher eventos) {
        this.contratos = contratos; this.tarefas = tarefas; this.atuacoes = atuacoes; this.rateio = rateio; this.eventos = eventos;
    }

    @Transactional
    public ContratoServicoEncerramentoResponseDTO encerrar(Long contratoId, ServicoComercial informado, Long usuarioId) {
        if (usuarioId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        Contrato contrato = contratos.findByIdForUpdate(contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        if (!ContratoService.STATUS_APROVADO.equals(contrato.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O contrato precisa estar aprovado para encerrar um serviço.");
        var servicos = ContratoServicos.listar(contrato);
        if (informado == null && servicos.size() > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Selecione o serviço que será encerrado.");
        var servico = informado == null ? ContratoServicos.primeiro(contrato) : informado;
        if (servico != null && !servicos.contains(servico)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Este serviço não pertence ao contrato.");
        Usuario responsavel = ContratoServicos.responsavel(contrato, servico);
        if (responsavel == null || !Objects.equals(responsavel.getId(), usuarioId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente o responsável pelo serviço pode encerrar este contrato.");
        String chave = ContratoServicos.chave(servico);
        var encerramento = contrato.getEncerramentosPorServico().get(chave);
        if (encerramento == null) {
            encerramento = new ContratoServicoEncerramento(LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS), responsavel);
            contrato.getEncerramentosPorServico().put(chave, encerramento);
            tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId).stream()
                    .filter(tarefa -> Objects.equals(ContratoServicos.daTarefa(tarefa), servico))
                    .forEach(tarefa -> { atuacoes.encerrar(tarefa); rateio.registrarAtuacao(tarefa); });
            contratos.save(contrato);
            eventos.publishEvent(new DrivePermissoesAlteradas(contrato.getEmpresa().getIdEmpresa()));
            eventos.publishEvent(new AvisoAtualizado(null, null, AvisoAtualizado.Tipo.KANBAN));
        }
        return new ContratoServicoEncerramentoResponseDTO(contratoId, servico,
                encerramento.getEncerradoEm(), encerramento.getEncerradoPor().getId());
    }
}
