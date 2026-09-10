package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PipelinePreVendaService {
    private final PipelineVendasNegocioRepository negocios;
    private final PipelineVendasFunilRepository funis;
    private final UsuarioRepository usuarios;
    private final PipelineCampanhaExecucaoRepository execucoes;
    private final PipelineCampanhaExecucaoManager manager;
    private final PipelineCadenciaEngine engine;
    private final PipelineHistoricoService historico;
    private final PipelineMovimentacaoEtapaService movimentacao;
    private final PipelineCadastroService cadastros;

    public PipelinePreVendaService(PipelineVendasNegocioRepository negocios, PipelineVendasFunilRepository funis,
            UsuarioRepository usuarios, PipelineCampanhaExecucaoRepository execucoes, PipelineCampanhaExecucaoManager manager,
            PipelineCadenciaEngine engine, PipelineHistoricoService historico, PipelineMovimentacaoEtapaService movimentacao,
            PipelineCadastroService cadastros) {
        this.negocios = negocios; this.funis = funis; this.usuarios = usuarios; this.execucoes = execucoes;
        this.manager = manager; this.engine = engine; this.historico = historico; this.movimentacao = movimentacao; this.cadastros = cadastros;
    }

    @Transactional
    public void sincronizar(PipelineVendasNegocio negocio) {
        engine.encerrarForaDaEtapa(negocio);
        if (!negocio.getFunil().isPreVendas() || negocio.getCampanhaOrigem() == null || negocio.getResultado() != PipelineVendasResultado.ABERTO) return;
        PipelineCampanha campanha = negocio.getCampanhaOrigem();
        campanha.getLeads().add(negocio);
        manager.sincronizar(campanha);
        if (Boolean.TRUE.equals(campanha.getAtivo())) engine.processarPendentes();
    }

    @Transactional
    public PipelineVendasNegocio converter(PipelineVendasNegocio lead, Long usuarioId, Long funilId, Long responsavelId) {
        cadastros.exigir(usuarioId, PermissaoCodigo.COMERCIAL_CONCLUIR);
        if (!lead.getFunil().isPreVendas()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um lead de pré-vendas");
        var existente = negocios.findByPreVendaOrigemIdNegocio(lead.getIdNegocio());
        if (existente.isPresent()) return existente.get();
        PipelineVendasFunil funil = funis.findByAtivoTrueOrderByPosicaoAsc().stream()
                .filter(f -> !f.isPreVendas() && (funilId == null || f.getIdFunil().equals(funilId)))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure um funil de vendas ativo"));
        var abertas = funil.getEtapas().stream().filter(e -> Boolean.TRUE.equals(e.getAtivo()) && e.getResultado() == PipelineVendasResultado.ABERTO)
                .sorted(Comparator.comparing(PipelineVendasEtapa::getPosicao)).toList();
        PipelineVendasEtapa etapa = abertas.stream().filter(e -> e.getCodigo().startsWith("DIAGNOSTICO"))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure a etapa Diagnóstico no funil de destino"));
        PipelineVendasNegocio venda = new PipelineVendasNegocio();
        venda.setFunil(funil); venda.setEtapa(etapa); venda.setPreVendaOrigem(lead);
        venda.setPessoa(lead.getPessoa()); venda.setEmpresa(lead.getEmpresa()); venda.setNomeEmpresa(lead.getNomeEmpresa());
        venda.setNomeContato(lead.getNomeContato()); venda.setEmail(lead.getEmail()); venda.setTelefone(lead.getTelefone());
        venda.setResponsavel(responsavelId == null ? lead.getResponsavel() : usuarios.findById(responsavelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável não encontrado")));
        venda.setCriadoPor(usuarios.findById(usuarioId).orElseThrow());
        venda.setCampanhaOrigem(lead.getCampanhaOrigem()); venda.setEstrategiaComercial(lead.getEstrategiaComercial());
        venda.setOrigemNegocio(lead.getOrigemNegocio()); venda.setDataReuniao(lead.getDataReuniao());
        venda.setServicosInteresse(lead.getServicosInteresse()); venda.setObservacoes(lead.getObservacoes());
        venda.setTags(new LinkedHashSet<>(lead.getTags())); venda.setCampos(cadastros.camposParaVenda(lead));
        venda.setResultado(PipelineVendasResultado.ABERTO); venda.setCriadoEm(LocalDateTime.now()); venda.setUltimaMovimentacaoEm(venda.getCriadoEm());
        PipelineCamposObrigatorios.validar(venda, etapa);
        negocios.saveAndFlush(venda);
        movimentacao.iniciar(venda, venda.getCriadoEm());
        historico.registrar(venda, usuarioId, PipelineHistoricoTipo.CRIACAO_NEGOCIO, "Gerado pelo lead de pré-vendas #" + lead.getIdNegocio());
        historico.registrar(lead, usuarioId, PipelineHistoricoTipo.FECHAMENTO, "Negócio de vendas #" + venda.getIdNegocio() + " criado em Diagnóstico");
        return venda;
    }

    @Transactional
    public void reiniciar(Long negocioId, Long usuarioId) {
        cadastros.exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_EXECUTAR);
        PipelineVendasNegocio lead = negocios.findByIdForUpdate(negocioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (lead.getResultado() != PipelineVendasResultado.ABERTO || lead.getCampanhaOrigem() == null || !Boolean.TRUE.equals(lead.getCampanhaOrigem().getAtivo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O lead e a campanha precisam estar ativos");
        boolean fluxoDisponivel = lead.getFunil().isPreVendas() && lead.getCampanhaOrigem().getEtapas().stream()
                .anyMatch(e -> e.getVersao().equals(lead.getCampanhaOrigem().getVersao()) && e.getEtapaFunilCodigo().equals(lead.getEtapa().getCodigo()));
        if (!fluxoDisponivel) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure uma cadência para a etapa atual antes de reiniciar");
        execucoes.findByNegocioIdNegocio(negocioId).stream()
                .filter(e -> e.getCampanha().getIdCampanha().equals(lead.getCampanhaOrigem().getIdCampanha()) && e.getEtapaFunilCodigo().equals(lead.getEtapa().getCodigo()))
                .forEach(e -> {
                    if (e.getStatus() == PipelineExecucaoStatus.ATIVA || e.getStatus() == PipelineExecucaoStatus.PAUSADA) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma cadência em andamento");
                    e.setStatus(PipelineExecucaoStatus.ATIVA); e.setVersao(lead.getCampanhaOrigem().getVersao()); e.setOrdemAtual(0);
                    e.setFinalizadoEm(null); e.setIniciadoEm(LocalDateTime.now()); e.setProximaExecucaoEm(LocalDateTime.now()); e.setTarefaAtual(null);
                    execucoes.save(e);
                });
        historico.registrar(lead, usuarioId, PipelineHistoricoTipo.CADENCIA_INICIADA, "Reinício solicitado da cadência da etapa");
        sincronizar(lead);
    }

    public String status(PipelineVendasNegocio negocio) {
        return execucoes.findByNegocioIdNegocio(negocio.getIdNegocio()).stream()
                .filter(e -> e.getEtapaFunilCodigo().equals(negocio.getEtapa().getCodigo()))
                .filter(e -> negocio.getCampanhaOrigem() != null && e.getCampanha().getIdCampanha().equals(negocio.getCampanhaOrigem().getIdCampanha()))
                .map(e -> e.getStatus().name()).findFirst().orElse(null);
    }
    public Long negocioGerado(PipelineVendasNegocio lead) {
        return lead.getFunil().isPreVendas() ? negocios.findByPreVendaOrigemIdNegocio(lead.getIdNegocio()).map(PipelineVendasNegocio::getIdNegocio).orElse(null) : null;
    }
}
