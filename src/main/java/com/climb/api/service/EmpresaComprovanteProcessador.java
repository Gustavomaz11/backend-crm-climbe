package com.climb.api.service;

import com.climb.api.repository.EmpresaComprovanteRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class EmpresaComprovanteProcessador {
    private final EmpresaComprovanteRepository repository;
    private final CnpjaService cnpja;
    private final DriveOrganizacaoService drive;

    public EmpresaComprovanteProcessador(EmpresaComprovanteRepository repository, CnpjaService cnpja, DriveOrganizacaoService drive) {
        this.repository = repository;
        this.cnpja = cnpja;
        this.drive = drive;
    }

    @Scheduled(fixedDelayString = "${app.cnpja.comprovantes-interval-ms:2000}", scheduler = "cnpjaComprovantesScheduler")
    public void processar() {
        var agora = LocalDateTime.now();
        repository.interromperAntigos(agora.minusMinutes(10), agora);
        var pendente = repository.findFirstByStatusOrderByIdAsc("PENDENTE");
        if (pendente.isEmpty() || repository.assumir(pendente.get().getId(), agora) != 1) return;
        var registro = repository.findById(pendente.get().getId()).orElse(null);
        if (registro == null) return;
        try {
            registro.setConteudo(cnpja.emitirComprovante(registro.getTipo(), registro.getCnpj()));
            registro.setStatus("DISPONIVEL");
            registro.setGeradoEm(LocalDateTime.now());
            registro.setMensagem(null);
        } catch (ResponseStatusException exception) {
            registro.setStatus(exception.getStatusCode().value() == 404 ? "INDISPONIVEL" : "ERRO");
            registro.setMensagem(exception.getStatusCode().value() == 404
                    ? "O provedor não disponibilizou comprovante para este CNPJ."
                    : exception.getReason());
        } catch (RuntimeException exception) {
            registro.setStatus("ERRO");
            registro.setMensagem("Não foi possível emitir o comprovante. Tente novamente.");
        }
        registro.setAtualizadoEm(LocalDateTime.now());
        try {
            repository.saveAndFlush(registro);
            if ("DISPONIVEL".equals(registro.getStatus())) drive.agendarEmpresa(registro.getEmpresaId());
        }
        catch (OptimisticLockingFailureException exception) { /* O cadastro mudou durante a emissão; mantém a versão mais recente. */ }
    }
}
