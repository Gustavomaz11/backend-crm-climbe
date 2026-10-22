package com.climb.api.integracao;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.service.ContratoKanbanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.climb.api.integracao.IntegracaoModulo.TECNICO;

@RestController @RequestMapping("/api/integracoes/v1/tecnico") @RequiredArgsConstructor
public class IntegracaoTecnicoController {
    private final IntegracaoEscopo escopo;
    private final ContratoRepository contratos;
    private final ContratoKanbanService kanban;

    @GetMapping("/contratos")
    @IntegracaoEndpoint(codigo = "tecnico.contratos.listar", modulo = TECNICO,
            descricao = "Lista contratos das empresas autorizadas. Query: pagina (0), tamanho (50, máximo 100).",
            resposta = "{\"itens\":[{\"id\":1,\"empresaId\":1,\"nome\":\"Empresa exemplo\",\"status\":\"APROVADO\"}],\"pagina\":0,\"tamanho\":50,\"total\":1,\"paginas\":1}")
    public IntegracaoPagina<Map<String, Object>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return IntegracaoPagina.de(contratos.findByEmpresa_IdEmpresaIn(escopo.empresas(), IntegracaoPagina.pedido(pagina, tamanho))
                .map(c -> Map.<String, Object>of("id", c.getIdContrato(), "empresaId", c.getEmpresa().getIdEmpresa(),
                        "nome", c.getEmpresaNomeFantasia(), "status", c.getStatus())));
    }
    @GetMapping("/contratos/{id}/kanban")
    @IntegracaoEndpoint(codigo = "tecnico.kanban.ler", modulo = TECNICO,
            descricao = "Consulta raias e tarefas do contrato. Query opcional: servico (código do serviço contratado).",
            resposta = "{\"contratoId\":1,\"raias\":[{\"id\":1,\"titulo\":\"Em andamento\",\"tasks\":[]}]}")
    public Map<String, Object> board(@PathVariable Long id, @RequestParam(required = false) ServicoComercial servico) {
        escopo.contrato(id); return seguro(kanban.buscarBoard(id, escopo.ator(), servico));
    }
    @PostMapping("/contratos/{id}/tarefas")
    @IntegracaoEndpoint(codigo = "tecnico.tarefas.criar", modulo = TECNICO, descricao = "Cria uma tarefa. Prazo obrigatório; responsáveis e raia devem pertencer ao serviço.",
            exemplo = "{\"raiaId\":1,\"titulo\":\"Analisar documentação\",\"descricao\":\"Conferir documentos recebidos\",\"prioridade\":\"MEDIA\",\"responsavelIds\":[1],\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-01-08\"}",
            resposta = "{\"contratoId\":1,\"raias\":[{\"id\":1,\"tasks\":[{\"id\":10,\"titulo\":\"Analisar documentação\"}]}]}")
    public Map<String, Object> criar(@PathVariable Long id, @RequestBody ContratoKanbanTaskRequestDTO dto) {
        escopo.contrato(id); return seguro(kanban.criarTask(id, escopo.ator(), dto));
    }
    @PutMapping("/contratos/{id}/tarefas/{tarefaId}")
    @IntegracaoEndpoint(codigo = "tecnico.tarefas.editar", modulo = TECNICO,
            descricao = "Atualiza os dados da tarefa. Envie todos os campos que devem ser preservados. Movimentação usa operação própria.",
            exemplo = "{\"raiaId\":1,\"titulo\":\"Conferir documentação\",\"descricao\":\"Conferência final\",\"prioridade\":\"MEDIA\",\"responsavelIds\":[1],\"dataInicio\":\"2027-01-04\",\"dataFim\":\"2027-01-08\"}",
            resposta = "{\"contratoId\":1,\"raias\":[]}")
    public Map<String, Object> editar(@PathVariable Long id, @PathVariable Long tarefaId, @RequestBody ContratoKanbanTaskRequestDTO dto) {
        escopo.contrato(id);
        var atual = kanban.exigirTaskVisivel(tarefaId, escopo.ator());
        if (!atual.getContrato().getIdContrato().equals(id)) throw IntegracaoEscopo.indisponivel();
        if (dto.raiaId() != null && !dto.raiaId().equals(atual.getRaia().getIdRaia()))
            throw IntegracaoService.erro(org.springframework.http.HttpStatus.BAD_REQUEST, "Use a operação de movimentação para alterar a raia.");
        return seguro(kanban.atualizarTask(id, tarefaId, escopo.ator(), dto));
    }
    @PatchMapping("/contratos/{id}/tarefas/{tarefaId}/mover")
    @IntegracaoEndpoint(codigo = "tecnico.tarefas.mover", modulo = TECNICO, descricao = "Move uma tarefa entre raias do mesmo serviço. Pode concluir tarefas conforme a raia de destino.",
            exemplo = "{\"raiaId\":2,\"justificativaAtraso\":\"Conclusão após conferência\"}", resposta = "{\"contratoId\":1,\"raias\":[]}")
    public Map<String, Object> mover(@PathVariable Long id, @PathVariable Long tarefaId, @RequestBody ContratoKanbanMoverTaskRequestDTO dto) {
        escopo.contrato(id); return seguro(kanban.moverTask(id, tarefaId, escopo.ator(), dto));
    }
    @DeleteMapping("/contratos/{id}/tarefas/{tarefaId}")
    @IntegracaoEndpoint(codigo = "tecnico.tarefas.excluir", modulo = TECNICO, descricao = "Exclui uma tarefa do contrato.", resposta = "HTTP 204, sem corpo.")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @PathVariable Long tarefaId) {
        escopo.contrato(id); kanban.removerTask(id, tarefaId, escopo.ator());
    }
    private Map<String, Object> seguro(ContratoKanbanBoardResponseDTO dto) {
        // O catálogo global de usuários e as permissões internas não fazem parte da API externa.
        return Map.of("contratoId", dto.contratoId(), "raias", dto.raias());
    }
}
