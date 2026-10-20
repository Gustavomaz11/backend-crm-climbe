package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class DriveSincronizacaoService {
    private final EmpresaRepository empresas;
    private final EmpresaDriveRepository organizacoes;
    private final EmpresaDriveServicoRepository servicos;
    private final EmpresaComprovanteRepository comprovantes;
    private final GoogleDriveClient drive;
    private final GoogleDriveProperties properties;
    private final DriveOrganizacaoService nomes;

    public DriveSincronizacaoService(EmpresaRepository empresas, EmpresaDriveRepository organizacoes,
            EmpresaDriveServicoRepository servicos, EmpresaComprovanteRepository comprovantes,
            GoogleDriveClient drive, GoogleDriveProperties properties, DriveOrganizacaoService nomes) {
        this.empresas = empresas; this.organizacoes = organizacoes; this.servicos = servicos;
        this.comprovantes = comprovantes; this.drive = drive; this.properties = properties; this.nomes = nomes;
    }

    @Transactional
    public void sincronizar(Long empresaId) {
        var empresa = empresas.findByIdForUpdate(empresaId).orElse(null);
        var fila = organizacoes.findById(empresaId).orElse(null);
        if (empresa == null || fila == null || !fila.isPendente() || fila.getTentarEm().isAfter(LocalDateTime.now())) return;
        try {
            String raiz = drive.garantirPasta(properties.getRootFolderId(), nomes.nomeEmpresa(empresa), "empresa-" + empresaId);
            fila.setPastaId(raiz);
            String documentos = drive.garantirPasta(raiz, "Documentações", "documentacoes-" + empresaId);
            fila.setDocumentacoesId(documentos);
            for (var pasta : servicos.findByEmpresaIdOrderByIdAsc(empresaId)) {
                pasta.setPastaId(drive.garantirPasta(raiz, nomes.nomeServico(pasta.getServico()), "servico-" + empresaId + "-" + pasta.getServico()));
                pasta.setPermissoesTentarEm(LocalDateTime.now());
                servicos.save(pasta);
            }
            for (var comprovante : comprovantes.findByEmpresaIdAndStatusAndDriveArquivoIdIsNull(empresaId, "DISPONIVEL")) {
                if (comprovante.getConteudo() == null || !comprovante.getCnpj().equals(empresa.getCnpj().replaceAll("\\D", ""))) continue;
                String chave = "comprovante-" + comprovante.getId() + "-" + comprovante.getCnpj();
                var arquivo = drive.enviar(documentos, comprovante.getTipo() + "-" + comprovante.getCnpj() + ".pdf",
                        "application/pdf", comprovante.getConteudo(), chave);
                comprovante.setDriveArquivoId(arquivo.id()); comprovantes.save(comprovante);
            }
            fila.setPendente(false); fila.setErro(null);
        } catch (ResponseStatusException exception) {
            fila.setErro(exception.getReason()); fila.setTentarEm(LocalDateTime.now().plusMinutes(1));
        }
        organizacoes.save(fila);
    }
}
