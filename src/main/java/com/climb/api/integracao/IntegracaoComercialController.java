package com.climb.api.integracao;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.*;
import com.climb.api.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import static com.climb.api.integracao.IntegracaoModulo.COMERCIAL;

@RestController @RequestMapping("/api/integracoes/v1/comercial") @RequiredArgsConstructor
public class IntegracaoComercialController {
    private final IntegracaoEscopo escopo;
    private final PropostaRepository repository;
    private final PropostaService propostas;
    private final PipelineVendasNegocioRepository negociosRepository;
    private final PipelineVendasEtapaRepository etapas;
    private final PipelineVendasService negocios;
    private final IntegracaoPreVendasService preVendas;
    public record PropostaInput(@NotNull @Positive Long empresaId, Long negocioId, @NotNull @DecimalMin("0.01") BigDecimal valuation) {}
    public record Valuation(@NotNull @DecimalMin("0.01") BigDecimal valuation) {}
    public record PropostaResumo(Long id, Long empresaId, Long negocioId, BigDecimal valuation, PropostaStatus status, LocalDate dataCriacao) {
        static PropostaResumo de(PropostaResponseDTO p) { return new PropostaResumo(p.idProposta(), p.empresaId(), p.negocioId(), p.valuation(), p.status(), p.dataCriacao()); }
    }
    public record NegocioInput(@NotNull @Positive Long empresaId, Long funilId, Long etapaId,
            @NotBlank @Size(max = 180) String nomeContato, @Size(max = 30) String telefone,
            @Email @Size(max = 254) String email, @NotNull @Positive Long responsavelId,
            @NotBlank @Size(max = 120) String origemNegocio, @NotBlank @Size(max = 500) String estrategiaComercial,
            @DecimalMin("0.01") BigDecimal valorEstimadoProposta, @Size(max = 5000) String observacoes) {}
    public record Etapa(@NotNull @Positive Long etapaId) {}
    public record NegocioResumo(Long id, Long empresaId, Long funilId, Long etapaId, Long responsavelId,
            String nomeContato, String telefone, String email, String origemNegocio, String estrategiaComercial,
            BigDecimal valorEstimadoProposta, String observacoes, PipelineVendasResultado resultado) {
        static NegocioResumo de(PipelineNegocioResponseDTO n) { return new NegocioResumo(n.id(), n.empresaId(), n.funilId(), n.etapaId(),
                n.responsavelId(), n.nomeContato(), n.telefone(), n.email(), n.origemNegocio(), n.estrategiaComercial(), n.valorEstimadoProposta(), n.observacoes(), n.resultado()); }
    }
    @GetMapping("/propostas")
    @IntegracaoEndpoint(codigo = "comercial.propostas.listar", modulo = COMERCIAL, descricao = "Lista propostas das empresas autorizadas. Query: pagina (0), tamanho (50, máximo 100).",
            resposta = "{\"itens\":[{\"id\":1,\"empresaId\":1,\"valuation\":100000,\"status\":\"PENDENTE\"}],\"pagina\":0,\"tamanho\":50,\"total\":1,\"paginas\":1}")
    public IntegracaoPagina<PropostaResumo> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return IntegracaoPagina.de(repository.findByEmpresa_IdEmpresaIn(escopo.empresas(), IntegracaoPagina.pedido(pagina, tamanho))
                .map(p -> new PropostaResumo(p.getIdProposta(), p.getEmpresa().getIdEmpresa(), p.getNegocio() == null ? null : p.getNegocio().getIdNegocio(), p.getValuation(), p.getStatus(), p.getDataCriacao())));
    }
    @GetMapping("/propostas/{id}")
    @IntegracaoEndpoint(codigo = "comercial.propostas.ler", modulo = COMERCIAL, descricao = "Consulta uma proposta autorizada.", resposta = "{\"id\":1,\"empresaId\":1,\"valuation\":100000,\"status\":\"PENDENTE\",\"dataCriacao\":\"2027-01-04\"}")
    public PropostaResumo buscar(@PathVariable Long id) { escopo.proposta(id); return PropostaResumo.de(propostas.buscarPorId(id)); }
    @PostMapping("/propostas") @ResponseStatus(HttpStatus.CREATED)
    @IntegracaoEndpoint(codigo = "comercial.propostas.criar", modulo = COMERCIAL, descricao = "Cria proposta pendente. negocioId é opcional e deve pertencer à mesma empresa.",
            exemplo = "{\"empresaId\":1,\"valuation\":100000}", resposta = "{\"id\":1,\"empresaId\":1,\"valuation\":100000,\"status\":\"PENDENTE\"}")
    public PropostaResumo criar(@Valid @RequestBody PropostaInput dto) {
        validarProposta(dto); return PropostaResumo.de(propostas.criar(propostaDTO(dto, PropostaStatus.PENDENTE, null)));
    }
    @PutMapping("/propostas/{id}")
    @IntegracaoEndpoint(codigo = "comercial.propostas.editar", modulo = COMERCIAL, descricao = "Atualiza empresa, vínculo de negócio e valuation; preserva status e arquivo.",
            exemplo = "{\"empresaId\":1,\"negocioId\":1,\"valuation\":120000}", resposta = "{\"id\":1,\"empresaId\":1,\"valuation\":120000,\"status\":\"PENDENTE\"}")
    public PropostaResumo editar(@PathVariable Long id, @Valid @RequestBody PropostaInput dto) {
        var atual = escopo.proposta(id); validarProposta(dto);
        return PropostaResumo.de(propostas.atualizar(id, propostaDTO(dto, atual.getStatus(), atual.getUrl())));
    }
    @PatchMapping("/propostas/{id}/valuation")
    @IntegracaoEndpoint(codigo = "comercial.propostas.valuation", modulo = COMERCIAL, descricao = "Altera somente o valuation, preservando os demais dados.",
            exemplo = "{\"valuation\":125000}", resposta = "{\"id\":1,\"empresaId\":1,\"valuation\":125000,\"status\":\"PENDENTE\"}")
    public PropostaResumo valuation(@PathVariable Long id, @Valid @RequestBody Valuation dto) {
        var atual = escopo.proposta(id);
        var input = new PropostaInput(atual.getEmpresa().getIdEmpresa(), atual.getNegocio() == null ? null : atual.getNegocio().getIdNegocio(), dto.valuation());
        validarProposta(input); return PropostaResumo.de(propostas.atualizar(id, propostaDTO(input, atual.getStatus(), atual.getUrl())));
    }
    @DeleteMapping("/propostas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @IntegracaoEndpoint(codigo = "comercial.propostas.excluir", modulo = COMERCIAL, descricao = "Exclui proposta autorizada. Vínculos existentes podem impedir a exclusão.", resposta = "HTTP 204, sem corpo.")
    public void excluir(@PathVariable Long id) { escopo.proposta(id); propostas.deletar(id); }
    @GetMapping("/negocios")
    @IntegracaoEndpoint(codigo = "comercial.negocios.listar", modulo = COMERCIAL, descricao = "Lista negócios das empresas autorizadas. Query: pagina (0), tamanho (50, máximo 100).",
            resposta = "{\"itens\":[{\"id\":1,\"empresaId\":1,\"funilId\":1,\"etapaId\":1,\"resultado\":\"ABERTO\"}],\"pagina\":0,\"tamanho\":50,\"total\":1,\"paginas\":1}")
    public IntegracaoPagina<NegocioResumo> listarNegocios(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return IntegracaoPagina.de(negociosRepository.findByEmpresa_IdEmpresaIn(escopo.empresas(), IntegracaoPagina.pedido(pagina, tamanho))
                .map(n -> NegocioResumo.de(negocios.buscar(n.getIdNegocio(), escopo.ator()))));
    }
    @GetMapping("/negocios/{id}")
    @IntegracaoEndpoint(codigo = "comercial.negocios.ler", modulo = COMERCIAL, descricao = "Consulta um negócio autorizado.", resposta = "{\"id\":1,\"empresaId\":1,\"funilId\":1,\"etapaId\":1,\"resultado\":\"ABERTO\"}")
    public NegocioResumo negocio(@PathVariable Long id) { escopo.negocio(id); return NegocioResumo.de(negocios.buscar(id, escopo.ator())); }
    @PostMapping("/pre-vendas/leads") @ResponseStatus(HttpStatus.CREATED)
    @IntegracaoEndpoint(codigo = "comercial.prevendas.leads.criar", modulo = COMERCIAL,
            descricao = "Cria um lead na etapa Lista de leads do funil Pré-vendas · Todas as estratégias. O destino é automático; não envie funilId ou etapaId. empresaId deve identificar uma empresa já cadastrada.",
            exemplo = "{\"empresaId\":1,\"nomeContato\":\"Representante exemplo\",\"telefone\":\"79999991234\",\"email\":\"contato@example.com\",\"responsavelId\":1,\"origemNegocio\":\"Integração\",\"estrategiaComercial\":\"Contato direto\",\"observacoes\":\"Lead recebido pela plataforma parceira\"}",
            resposta = "{\"id\":1,\"empresaId\":1,\"funilId\":2,\"etapaId\":10,\"responsavelId\":1,\"nomeContato\":\"Representante exemplo\",\"resultado\":\"ABERTO\"}")
    public NegocioResumo criarLead(@Valid @RequestBody IntegracaoLeadRequest dto) {
        return NegocioResumo.de(preVendas.criar(dto));
    }
    @PostMapping("/negocios") @ResponseStatus(HttpStatus.CREATED)
    @IntegracaoEndpoint(codigo = "comercial.negocios.criar", modulo = COMERCIAL, descricao = "Cria negócio no funil e na etapa definidos no payload, vinculado a uma empresa cadastrada. Campos obrigatórios adicionais do funil continuam sendo validados.",
            exemplo = "{\"empresaId\":1,\"funilId\":1,\"etapaId\":1,\"nomeContato\":\"Representante exemplo\",\"email\":\"contato@example.com\",\"responsavelId\":1,\"origemNegocio\":\"Integração\",\"estrategiaComercial\":\"Contato direto\",\"valorEstimadoProposta\":10000}",
            resposta = "{\"id\":1,\"empresaId\":1,\"funilId\":1,\"etapaId\":1,\"resultado\":\"ABERTO\"}")
    public NegocioResumo criarNegocio(@Valid @RequestBody NegocioInput dto) { return NegocioResumo.de(negocios.criar(escopo.ator(), negocioDTO(dto))); }
    @PutMapping("/negocios/{id}")
    @IntegracaoEndpoint(codigo = "comercial.negocios.editar", modulo = COMERCIAL, descricao = "Atualiza dados comerciais. Para mudar etapa, use a operação específica. Envie os campos obrigatórios do cadastro.",
            exemplo = "{\"empresaId\":1,\"funilId\":1,\"etapaId\":1,\"nomeContato\":\"Representante exemplo\",\"email\":\"contato@example.com\",\"responsavelId\":1,\"origemNegocio\":\"Integração\",\"estrategiaComercial\":\"Contato direto\",\"valorEstimadoProposta\":12000}",
            resposta = "{\"id\":1,\"empresaId\":1,\"resultado\":\"ABERTO\"}")
    public NegocioResumo editarNegocio(@PathVariable Long id, @Valid @RequestBody NegocioInput dto) {
        var atual = escopo.negocio(id);
        if (dto.etapaId() != null && !dto.etapaId().equals(atual.getEtapa().getIdEtapa())) throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "Use a operação de movimentação para mudar a etapa.");
        return NegocioResumo.de(negocios.atualizar(id, escopo.ator(), negocioDTO(dto)));
    }
    @PatchMapping("/negocios/{id}/etapa")
    @IntegracaoEndpoint(codigo = "comercial.negocios.mover", modulo = COMERCIAL, descricao = "Move negócio entre etapas abertas do mesmo funil. Não ganha negócios nem cria contratos.",
            exemplo = "{\"etapaId\":2}", resposta = "{\"id\":1,\"empresaId\":1,\"etapaId\":2,\"resultado\":\"ABERTO\"}")
    public NegocioResumo moverNegocio(@PathVariable Long id, @Valid @RequestBody Etapa dto) {
        escopo.negocio(id);
        var etapa = etapas.findById(dto.etapaId()).orElseThrow(IntegracaoEscopo::indisponivel);
        if (etapa.getResultado() != PipelineVendasResultado.ABERTO) throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "Somente etapas abertas são permitidas nesta operação.");
        return NegocioResumo.de(negocios.mover(id, escopo.ator(), dto.etapaId(), null, null, null, null));
    }
    private void validarProposta(PropostaInput dto) {
        escopo.empresa(dto.empresaId());
        if (dto.negocioId() != null && !escopo.negocio(dto.negocioId()).getEmpresa().getIdEmpresa().equals(dto.empresaId()))
            throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "O negócio deve pertencer à mesma empresa da proposta.");
    }
    private PropostaRequestDTO propostaDTO(PropostaInput dto, PropostaStatus status, String url) {
        return new PropostaRequestDTO(dto.empresaId(), dto.negocioId(), escopo.ator(), url, dto.valuation(), status, null);
    }
    private PipelineNegocioRequestDTO negocioDTO(NegocioInput dto) {
        var empresa = escopo.empresa(dto.empresaId());
        return new PipelineNegocioRequestDTO(dto.funilId(), dto.empresaId(), empresa.getNomeFantasia(), dto.nomeContato(), dto.telefone(), dto.email(),
                dto.responsavelId(), dto.etapaId(), null, dto.origemNegocio(), dto.estrategiaComercial(), null, dto.valorEstimadoProposta(), dto.observacoes());
    }
}
