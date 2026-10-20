package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.EmpresaDriveServico;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.EmpresaDriveRepository;
import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class DriveTarefaService {
    private final ContratoKanbanService tarefas;
    private final EmpresaDriveServicoRepository pastas;
    private final EmpresaDriveRepository empresas;
    private final DriveOrganizacaoService organizacao;
    private final GoogleDriveClient drive;
    private final ArquivoValidationService validation;

    public record Servico(ServicoComercial servico, String nome, String pastaId) {}
    public record Pastas(List<Servico> servicos, String mensagem) {}
    public record NovaPasta(String nome, String pastaPaiId) {}

    public DriveTarefaService(ContratoKanbanService tarefas, EmpresaDriveServicoRepository pastas,
            EmpresaDriveRepository empresas, DriveOrganizacaoService organizacao, GoogleDriveClient drive,
            ArquivoValidationService validation) {
        this.tarefas = tarefas; this.pastas = pastas; this.empresas = empresas;
        this.organizacao = organizacao; this.drive = drive; this.validation = validation;
    }

    @Transactional(readOnly = true)
    public Pastas servicos(Long tarefaId, Long usuarioId) {
        Contrato contrato = contrato(tarefaId, usuarioId);
        Long empresaId = contrato.getEmpresa().getIdEmpresa();
        var lista = organizacao.servicos(contrato).stream().map(servico -> new Servico(servico, organizacao.nomeServico(servico),
                pastas.findByEmpresaIdAndServico(empresaId, servico).map(EmpresaDriveServico::getPastaId).orElse(null))).toList();
        String mensagem = empresas.findById(empresaId).map(fila -> fila.getErro()).orElse(null);
        return new Pastas(lista, mensagem);
    }

    @Transactional(readOnly = true)
    public List<GoogleDriveClient.Arquivo> listar(Long tarefaId, Long usuarioId, ServicoComercial servico, String pastaId) {
        String raiz = raiz(tarefaId, usuarioId, servico);
        String pasta = pastaId == null ? raiz : pastaId;
        exigirPasta(raiz, pasta);
        return drive.listar(pasta);
    }

    @Transactional(readOnly = true)
    public GoogleDriveClient.Arquivo criarPasta(Long tarefaId, Long usuarioId, ServicoComercial servico, NovaPasta dto) {
        String raiz = raiz(tarefaId, usuarioId, servico);
        if (dto == null || dto.nome() == null || dto.nome().isBlank() || dto.nome().trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome de pasta com até 120 caracteres.");
        }
        String pasta = dto.pastaPaiId() == null ? raiz : dto.pastaPaiId();
        exigirPasta(raiz, pasta);
        return drive.criarPasta(pasta, dto.nome().trim(), null);
    }

    @Transactional(readOnly = true)
    public List<GoogleDriveClient.Arquivo> enviar(Long tarefaId, Long usuarioId, ServicoComercial servico,
            String pastaId, List<MultipartFile> arquivos) {
        String raiz = raiz(tarefaId, usuarioId, servico);
        String pasta = pastaId == null ? raiz : pastaId;
        exigirPasta(raiz, pasta);
        if (arquivos == null || arquivos.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo.");
        var validados = arquivos.stream().map(validation::validar).toList();
        return validados.stream().map(item -> drive.enviar(pasta, item.nomeOriginal(), item.contentType(), item.conteudo(), null)).toList();
    }

    @Transactional(readOnly = true)
    public GoogleDriveClient.Conteudo baixar(Long tarefaId, Long usuarioId, ServicoComercial servico, String arquivoId) {
        String raiz = raiz(tarefaId, usuarioId, servico);
        return drive.baixar(exigirDentroDaPasta(raiz, arquivoId));
    }

    private Contrato contrato(Long tarefaId, Long usuarioId) { return tarefas.exigirTaskVisivel(tarefaId, usuarioId).getContrato(); }

    private String raiz(Long tarefaId, Long usuarioId, ServicoComercial servico) {
        Contrato contrato = contrato(tarefaId, usuarioId);
        if (!organizacao.servicos(contrato).contains(servico)) throw naoEncontrado();
        return pastas.findByEmpresaIdAndServico(contrato.getEmpresa().getIdEmpresa(), servico)
                .map(EmpresaDriveServico::getPastaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "A pasta do serviço está aguardando sincronização após a assinatura do contrato."));
    }

    private void exigirPasta(String raiz, String id) {
        if (!exigirDentroDaPasta(raiz, id).pasta()) throw naoEncontrado();
    }

    private GoogleDriveClient.Arquivo exigirDentroDaPasta(String raiz, String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]+")) throw naoEncontrado();
        var item = drive.buscar(id);
        if (item.removido()) throw naoEncontrado();
        var atual = item;
        var visitados = new HashSet<String>();
        while (visitados.add(atual.id()) && visitados.size() <= 100) {
            if (atual.id().equals(raiz)) return item;
            if (atual.pais().size() != 1) throw naoEncontrado();
            atual = drive.buscar(atual.pais().getFirst());
            if (atual.removido()) throw naoEncontrado();
        }
        throw naoEncontrado();
    }

    private ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo ou pasta não encontrado neste serviço.");
    }
}
