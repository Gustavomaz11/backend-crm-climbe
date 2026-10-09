package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.TarefaPastaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;

/** Operates on the task already authorized by TarefaColaboracaoService. */
@Service
public class TarefaPastaService {
    private final TarefaPastaRepository repository;
    public TarefaPastaService(TarefaPastaRepository repository) { this.repository = repository; }

    public List<TarefaPasta> listar(TarefaTipo tipo, Long id) {
        return tipo == TarefaTipo.CONTRATO ? repository.findByContratoTask_IdTaskOrderByNomeAscIdAsc(id)
                : repository.findByPipelineTarefa_IdTarefaOrderByNomeAscIdAsc(id);
    }
    public TarefaPasta exigir(TarefaTipo tipo, Long id, Long pastaId) {
        if (pastaId == null) return null;
        TarefaPasta pasta = repository.findById(pastaId).orElseThrow(this::naoEncontrada);
        boolean pertence = tipo == TarefaTipo.CONTRATO
                ? pasta.getContratoTask() != null && Objects.equals(pasta.getContratoTask().getIdTask(), id)
                : pasta.getPipelineTarefa() != null && Objects.equals(pasta.getPipelineTarefa().getIdTarefa(), id);
        if (!pertence) throw naoEncontrada();
        return pasta;
    }
    public TarefaPasta criar(ContratoKanbanTask contrato, PipelineVendasTarefa comercial, Usuario autor, String nome, Long paiId) {
        String normalizado = nome == null ? "" : nome.trim();
        if (normalizado.isBlank()) throw erro("Informe um nome para a pasta.");
        if (normalizado.length() > 120) throw erro("O nome da pasta pode ter até 120 caracteres.");
        if (normalizado.equals(".") || normalizado.equals("..") || normalizado.contains("/") || normalizado.contains("\\")
                || normalizado.chars().anyMatch(Character::isISOControl)) throw erro("Escolha um nome para a pasta sem barras ou caracteres de controle.");
        TarefaTipo tipo = contrato != null ? TarefaTipo.CONTRATO : TarefaTipo.COMERCIAL;
        Long id = contrato != null ? contrato.getIdTask() : comercial.getIdTarefa();
        TarefaPasta pai = exigir(tipo, id, paiId);
        if (listar(tipo, id).stream().anyMatch(pasta -> Objects.equals(paiId, pasta.getPastaPai() == null ? null : pasta.getPastaPai().getId())
                && normalizado.equalsIgnoreCase(pasta.getNome()))) throw erro("Já existe uma pasta com esse nome aqui. Escolha outro nome.");
        TarefaPasta pasta = new TarefaPasta(); pasta.setContratoTask(contrato); pasta.setPipelineTarefa(comercial);
        pasta.setAutor(autor); pasta.setNome(normalizado); pasta.setPastaPai(pai);
        return repository.save(pasta);
    }
    public TarefaPasta anexosAutomaticos(ContratoKanbanTask contrato, PipelineVendasTarefa comercial, Usuario autor) {
        TarefaTipo tipo = contrato != null ? TarefaTipo.CONTRATO : TarefaTipo.COMERCIAL;
        Long id = contrato != null ? contrato.getIdTask() : comercial.getIdTarefa();
        return listar(tipo, id).stream().filter(pasta -> pasta.getPastaPai() == null && "Anexos".equalsIgnoreCase(pasta.getNome()))
                .findFirst().orElseGet(() -> criar(contrato, comercial, autor, "Anexos", null));
    }
    public TarefaPastaResponseDTO toResponse(TarefaPasta pasta) {
        var autor = pasta.getAutor();
        return new TarefaPastaResponseDTO(pasta.getId(), pasta.getNome(), pasta.getPastaPai() == null ? null : pasta.getPastaPai().getId(),
                new UsuarioResumoDTO(autor.getId(), autor.getNomeCompleto(), autor.getEmail()), pasta.getCriadoEm());
    }
    private ResponseStatusException naoEncontrada() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pasta não encontrada nesta tarefa."); }
    private ResponseStatusException erro(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
