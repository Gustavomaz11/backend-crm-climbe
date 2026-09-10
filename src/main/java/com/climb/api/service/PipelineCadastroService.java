package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;

@Service
public class PipelineCadastroService {
    private final PipelineTagRepository tags;
    private final PipelineCampoRepository campos;
    private final PessoaClienteRepository pessoas;
    private final PipelineCampanhaRepository campanhas;
    private final RbacService rbac;

    public PipelineCadastroService(PipelineTagRepository tags, PipelineCampoRepository campos,
            PessoaClienteRepository pessoas, PipelineCampanhaRepository campanhas, RbacService rbac) {
        this.tags = tags; this.campos = campos; this.pessoas = pessoas; this.campanhas = campanhas; this.rbac = rbac;
    }

    public void exigir(Long usuario, PermissaoCodigo permissao) {
        if (usuario == null || !rbac.temPermissao(usuario, permissao))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para esta ação comercial");
    }

    @Transactional(readOnly = true)
    public List<PipelineTag> tags(Long usuario) { exigir(usuario, PermissaoCodigo.COMERCIAL); return tags.findAll(); }
    @Transactional(readOnly = true)
    public List<PipelineCampo> campos(Long usuario) { exigir(usuario, PermissaoCodigo.COMERCIAL); return campos.findAll(); }

    @Transactional
    public PipelineTag salvarTag(Long usuario, Long id, String nome, String cor, Boolean ativo) {
        exigir(usuario, PermissaoCodigo.COMERCIAL_CADASTROS_GERENCIAR);
        if (nome == null || nome.isBlank() || nome.length() > 80 || cor == null || !cor.matches("#[0-9a-fA-F]{6}")) erro("Informe nome e cor válidos");
        PipelineTag tag = id == null ? new PipelineTag() : tags.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        tag.setNome(nome.trim()); tag.setCor(cor); tag.setAtivo(!Boolean.FALSE.equals(ativo));
        return tags.save(tag);
    }

    @Transactional
    public PipelineCampo salvarCampo(Long usuario, Long id, String nome, String tipo, String escopo, String opcoes, Boolean ativo) {
        exigir(usuario, PermissaoCodigo.COMERCIAL_CADASTROS_GERENCIAR);
        if (nome == null || nome.isBlank() || nome.length() > 100 || tipo == null || !Set.of("TEXTO", "NUMERO", "DATA", "SELECAO", "BOOLEANO").contains(tipo)
                || escopo == null || !Set.of("TODOS", "PRE_VENDAS", "VENDAS").contains(escopo)) erro("Definição de campo inválida");
        if ("SELECAO".equals(tipo) && (opcoes == null || opcoes.isBlank())) erro("Informe uma opção por linha");
        PipelineCampo campo = id == null ? new PipelineCampo() : campos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (id != null && (!campo.getTipo().equals(tipo) || !campo.getEscopo().equals(escopo) || !Objects.equals(campo.getOpcoes(), opcoes)))
            erro("Tipo, escopo e opções de um campo existente são imutáveis; crie outro campo");
        campo.setNome(nome.trim()); campo.setTipo(tipo); campo.setEscopo(escopo); campo.setOpcoes(opcoes); campo.setAtivo(!Boolean.FALSE.equals(ativo));
        return campos.save(campo);
    }

    public void aplicar(PipelineVendasNegocio negocio, PipelineNegocioRequestDTO dto) {
        if (dto.pessoaId() != null) {
            PessoaCliente pessoa = pessoas.findById(dto.pessoaId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pessoa não encontrada"));
            if (negocio.getEmpresa() == null || pessoa.getEmpresas().stream().noneMatch(e -> e.getIdEmpresa().equals(negocio.getEmpresa().getIdEmpresa()))) erro("Selecione uma empresa vinculada à pessoa");
            negocio.setPessoa(pessoa);
        } else {
            negocio.setPessoa(null);
        }
        if (negocio.getCampanhaOrigem() != null && dto.campanhaOrigemId() == null) erro("A campanha de origem é preservada; crie outro lead para uma nova abordagem");
        if (dto.campanhaOrigemId() != null) {
            PipelineCampanha campanha = campanhas.findById(dto.campanhaOrigemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campanha não encontrada"));
            if (negocio.getCampanhaOrigem() != null && !negocio.getCampanhaOrigem().getIdCampanha().equals(dto.campanhaOrigemId())) erro("Para outra campanha, crie um novo lead da mesma pessoa");
            if (negocio.getCampanhaOrigem() == null) negocio.setEstrategiaComercial(campanha.getEstrategia());
            negocio.setCampanhaOrigem(campanha);
        }
        if (dto.tagIds() != null) {
            Set<PipelineTag> selecionadas = new LinkedHashSet<>(tags.findAllById(dto.tagIds()));
            if (selecionadas.size() != dto.tagIds().size()) erro("Tag não encontrada");
            negocio.setTags(selecionadas);
        }
        if (dto.campos() != null) {
            dto.campos().forEach((id, valor) -> validarValor(id, valor, negocio.getFunil().getTipo()));
            negocio.setCampos(new LinkedHashMap<>(dto.campos()));
        }
    }

    public Map<Long, String> camposParaVenda(PipelineVendasNegocio lead) {
        Map<Long, String> valores = new LinkedHashMap<>();
        campos.findAllById(lead.getCampos().keySet()).stream().filter(c -> !"PRE_VENDAS".equals(c.getEscopo()))
                .forEach(c -> valores.put(c.getId(), lead.getCampos().get(c.getId())));
        return valores;
    }

    private void validarValor(Long id, String valor, String escopo) {
        PipelineCampo campo = campos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo não encontrado"));
        if (!"TODOS".equals(campo.getEscopo()) && !campo.getEscopo().equals(escopo)) erro("Campo indisponível neste funil");
        if (valor == null || valor.length() > 4000) erro("Valor de campo inválido");
        if (valor.isBlank()) return;
        try {
            switch (campo.getTipo()) {
                case "NUMERO" -> new BigDecimal(valor);
                case "DATA" -> LocalDate.parse(valor);
                case "BOOLEANO" -> { if (!Set.of("true", "false").contains(valor)) erro("Informe sim ou não"); }
                case "SELECAO" -> { if (campo.getOpcoes().lines().noneMatch(valor::equals)) erro("Opção inválida"); }
                default -> { }
            }
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) { erro("Valor inválido para " + campo.getNome()); }
    }
    private void erro(String mensagem) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    public record CampanhaOpcao(Long id, String nome, String estrategia, boolean ativo) {}
    @Transactional(readOnly = true)
    public List<CampanhaOpcao> campanhas(Long usuario) {
        exigir(usuario, PermissaoCodigo.COMERCIAL);
        return campanhas.findAll().stream().map(c -> new CampanhaOpcao(c.getIdCampanha(), c.getNome(), c.getEstrategia(), Boolean.TRUE.equals(c.getAtivo()))).toList();
    }

}
