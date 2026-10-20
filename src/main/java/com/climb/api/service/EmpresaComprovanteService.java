package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.EmpresaComprovante;
import com.climb.api.model.dto.EmpresaComprovanteDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.EmpresaComprovanteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmpresaComprovanteService {
    private static final List<String> TIPOS = List.of("RFB", "SIMPLES", "CCC", "SUFRAMA");
    private final EmpresaRepository empresas;
    private final EmpresaComprovanteRepository comprovantes;

    public EmpresaComprovanteService(EmpresaRepository empresas, EmpresaComprovanteRepository comprovantes) {
        this.empresas = empresas;
        this.comprovantes = comprovantes;
    }

    public List<EmpresaComprovanteDTO> listar(Long empresaId) {
        buscarEmpresa(empresaId);
        return comprovantes.listar(empresaId);
    }

    @Transactional
    public List<EmpresaComprovanteDTO> solicitar(Long empresaId, String tipo) {
        if (!TIPOS.contains(tipo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de comprovante inválido.");
        var empresa = buscarEmpresa(empresaId);
        var registro = comprovantes.findByEmpresaIdAndTipo(empresaId, tipo).orElseGet(EmpresaComprovante::new);
        String cnpj = empresa.getCnpj().replaceAll("\\D", "");
        if (registro.getId() != null && cnpj.equals(registro.getCnpj())
                && List.of("PENDENTE", "PROCESSANDO", "DISPONIVEL").contains(registro.getStatus())) return comprovantes.listar(empresaId);
        identificar(registro, empresaId, cnpj, tipo);
        preparar(registro, "PENDENTE");
        return comprovantes.listar(empresaId);
    }

    public void agendar(Empresa empresa) {
        String cnpj = empresa.getCnpj().replaceAll("\\D", "");
        for (String tipo : TIPOS) {
            var registro = comprovantes.findByEmpresaIdAndTipo(empresa.getIdEmpresa(), tipo).orElseGet(EmpresaComprovante::new);
            if (registro.getId() != null && cnpj.equals(registro.getCnpj())
                    && (!"RFB".equals(tipo) || !"ERRO".equals(registro.getStatus()))) continue;
            identificar(registro, empresa.getIdEmpresa(), cnpj, tipo);
            preparar(registro, "RFB".equals(tipo) ? "PENDENTE" : "NAO_SOLICITADO");
        }
    }

    @Transactional
    public void tentarNovamente(Long empresaId, Long id) {
        buscarEmpresa(empresaId);
        var registro = buscar(empresaId, id);
        if (!List.of("ERRO", "INDISPONIVEL").contains(registro.getStatus())) return;
        preparar(registro, "PENDENTE");
    }

    public EmpresaComprovante buscar(Long empresaId, Long id) {
        return comprovantes.findByIdAndEmpresaId(id, empresaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado para esta empresa."));
    }

    private Empresa buscarEmpresa(Long id) {
        return empresas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
    }

    private void identificar(EmpresaComprovante registro, Long empresaId, String cnpj, String tipo) {
        registro.setEmpresaId(empresaId);
        registro.setCnpj(cnpj);
        registro.setTipo(tipo);
    }

    private void preparar(EmpresaComprovante registro, String status) {
        registro.setStatus(status);
        registro.setMensagem(null);
        registro.setConteudo(null);
        registro.setDriveArquivoId(null);
        registro.setGeradoEm(null);
        registro.setAtualizadoEm(LocalDateTime.now());
        comprovantes.save(registro);
    }
}
