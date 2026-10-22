package com.climb.api.integracao;

import com.climb.api.model.Contrato;
import com.climb.api.model.dto.ContratoPreparacaoRequestDTO;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import com.climb.api.service.ContratoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import java.time.LocalDate;
import static com.climb.api.integracao.IntegracaoModulo.JURIDICO;

@RestController @RequestMapping("/api/integracoes/v1/juridico") @RequiredArgsConstructor
public class IntegracaoJuridicoController {
    private final IntegracaoEscopo escopo;
    private final ContratoService contratos;
    private final ContratoRepository repository;
    private final UsuarioRepository usuarios;
    public record Novo(@NotNull @Positive Long propostaId, LocalDate dataInicio, LocalDate dataFim) {}
    public record Datas(LocalDate dataInicio, LocalDate dataFim) {}
    public record Aprovacao(@NotBlank @Pattern(regexp = "APROVADO|REJEITADO") String status) {}
    public record Resumo(Long id, Long empresaId, Long propostaId, String status, LocalDate dataInicio, LocalDate dataFim, ServicoComercial servico) {
        static Resumo de(Contrato c) { return new Resumo(c.getIdContrato(), c.getEmpresa().getIdEmpresa(),
                c.getProposta() == null ? null : c.getProposta().getIdProposta(), c.getStatus(), c.getDataInicio(), c.getDataFim(), c.getServico()); }
    }
    @GetMapping("/contratos")
    @IntegracaoEndpoint(codigo = "juridico.contratos.listar", modulo = JURIDICO, descricao = "Lista contratos das empresas autorizadas. Query: pagina (0), tamanho (50, máximo 100).",
            resposta = "{\"itens\":[{\"id\":1,\"empresaId\":1,\"propostaId\":1,\"status\":\"PENDENTE\",\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-12-31\"}],\"pagina\":0,\"tamanho\":50,\"total\":1,\"paginas\":1}")
    public IntegracaoPagina<Resumo> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return IntegracaoPagina.de(repository.findByEmpresa_IdEmpresaIn(escopo.empresas(), IntegracaoPagina.pedido(pagina, tamanho)).map(Resumo::de));
    }
    @GetMapping("/contratos/{id}")
    @IntegracaoEndpoint(codigo = "juridico.contratos.ler", modulo = JURIDICO, descricao = "Consulta um contrato autorizado.", resposta = "{\"id\":1,\"empresaId\":1,\"propostaId\":1,\"status\":\"PENDENTE\",\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-12-31\"}")
    public Resumo buscar(@PathVariable Long id) { return Resumo.de(escopo.contrato(id)); }
    @PostMapping("/contratos") @ResponseStatus(HttpStatus.CREATED)
    @IntegracaoEndpoint(codigo = "juridico.contratos.criar", modulo = JURIDICO, descricao = "Cria contrato pendente a partir de uma proposta de empresa autorizada, respeitando as regras de vínculo.",
            exemplo = "{\"propostaId\":1,\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-12-31\"}", resposta = "{\"id\":1,\"empresaId\":1,\"propostaId\":1,\"status\":\"PENDENTE\"}")
    public Resumo criar(@Valid @RequestBody Novo dto) {
        var proposta = escopo.proposta(dto.propostaId());
        var c = new Contrato(); c.setProposta(proposta); c.setEmpresa(proposta.getEmpresa());
        c.setUsuario(usuarios.findById(escopo.ator()).orElseThrow(IntegracaoEscopo::indisponivel));
        c.setDataInicio(dto.dataInicio()); c.setDataFim(dto.dataFim()); c.setStatus("PENDENTE");
        validarDatas(dto.dataInicio(), dto.dataFim()); return Resumo.de(contratos.criar(c, escopo.ator()));
    }
    @PutMapping("/contratos/{id}")
    @IntegracaoEndpoint(codigo = "juridico.contratos.editar", modulo = JURIDICO, descricao = "Atualiza as datas do contrato. Não altera aprovação, empresa, proposta ou responsáveis.",
            exemplo = "{\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-12-31\"}", resposta = "{\"id\":1,\"empresaId\":1,\"status\":\"PENDENTE\",\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-12-31\"}")
    public Resumo editar(@PathVariable Long id, @RequestBody Datas dto) {
        var atual = escopo.contrato(id); validarDatas(dto.dataInicio(), dto.dataFim());
        var novo = new Contrato(); novo.setStatus(atual.getStatus()); novo.setResponsavel(atual.getResponsavel());
        novo.setParticipantes(new java.util.HashSet<>(atual.getParticipantes()));
        novo.setDataInicio(dto.dataInicio()); novo.setDataFim(dto.dataFim());
        return Resumo.de(contratos.atualizar(id, novo, escopo.ator()));
    }
    @PatchMapping("/contratos/{id}/aprovar")
    @IntegracaoEndpoint(codigo = "juridico.contratos.aprovar", modulo = JURIDICO, descricao = "Aprova ou rejeita contrato. Esta operação precisa de autorização explícita.",
            exemplo = "{\"status\":\"APROVADO\"}", resposta = "{\"id\":1,\"empresaId\":1,\"status\":\"APROVADO\"}")
    public Resumo aprovar(@PathVariable Long id, @Valid @RequestBody Aprovacao dto) {
        escopo.contrato(id); return Resumo.de(contratos.aprovar(id, escopo.ator(), dto.status()));
    }
    @PatchMapping("/contratos/{id}/etapa-preparacao")
    @IntegracaoEndpoint(codigo = "juridico.contratos.mover", modulo = JURIDICO, descricao = "Altera a etapa de preparação, respeitando a máquina de estados do contrato.",
            exemplo = "{\"etapa\":\"CONCLUIDO\"}", resposta = "{\"id\":1,\"empresaId\":1,\"status\":\"PENDENTE\"}")
    public Resumo mover(@PathVariable Long id, @Valid @RequestBody ContratoPreparacaoRequestDTO dto) {
        escopo.contrato(id); return Resumo.de(contratos.moverPreparacao(id, escopo.ator(), dto.etapa()));
    }
    @GetMapping("/contratos/{id}/pdf")
    @IntegracaoEndpoint(codigo = "juridico.contratos.pdf", modulo = JURIDICO, descricao = "Baixa o arquivo PDF do contrato autorizado.", resposta = "HTTP 200; conteúdo binário application/pdf.")
    public ResponseEntity<Resource> pdf(@PathVariable Long id) { escopo.contrato(id); return contratos.baixarPdf(id); }
    @DeleteMapping("/contratos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @IntegracaoEndpoint(codigo = "juridico.contratos.excluir", modulo = JURIDICO, descricao = "Exclui contrato autorizado. Vínculos existentes podem impedir a exclusão.", resposta = "HTTP 204, sem corpo.")
    public void excluir(@PathVariable Long id) { escopo.contrato(id); contratos.deletar(id); }
    private void validarDatas(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "A data final deve ser igual ou posterior à inicial.");
    }
}
