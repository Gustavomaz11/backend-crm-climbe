package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class DriveOrganizacaoService {
    private final EmpresaRepository empresas;
    private final EmpresaDriveRepository organizacoes;
    private final EmpresaDriveServicoRepository pastas;

    public DriveOrganizacaoService(EmpresaRepository empresas, EmpresaDriveRepository organizacoes,
            EmpresaDriveServicoRepository pastas) {
        this.empresas = empresas; this.organizacoes = organizacoes; this.pastas = pastas;
    }

    @Transactional
    public void agendarEmpresa(Long empresaId) {
        empresas.findByIdForUpdate(empresaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
        var organizacao = organizacoes.findById(empresaId).orElseGet(EmpresaDrive::new);
        organizacao.setEmpresaId(empresaId); organizacao.setPendente(true);
        organizacao.setTentarEm(LocalDateTime.now()); organizacao.setErro(null);
        organizacoes.save(organizacao);
    }

    @Transactional
    public void agendarContrato(Contrato contrato) {
        Long empresaId = contrato.getEmpresa().getIdEmpresa();
        agendarEmpresa(empresaId);
        for (var servico : servicos(contrato)) {
            if (pastas.findByEmpresaIdAndServico(empresaId, servico).isPresent()) continue;
            var pasta = new EmpresaDriveServico(); pasta.setEmpresaId(empresaId); pasta.setServico(servico);
            pastas.save(pasta);
        }
    }

    public List<ServicoComercial> servicos(Contrato contrato) {
        var servicos = new LinkedHashSet<ServicoComercial>();
        if (contrato.getProposta() != null) {
            contrato.getProposta().getServicos().forEach(item -> servicos.add(item.getServico()));
            if (servicos.isEmpty() && contrato.getProposta().getServico() != null) servicos.add(contrato.getProposta().getServico());
        }
        if (servicos.isEmpty() && contrato.getServico() != null) servicos.add(contrato.getServico());
        return List.copyOf(servicos);
    }

    public String nomeServico(ServicoComercial servico) {
        return switch (servico) {
            case BPO -> "BPO";
            case CFO -> "CFO";
            case CONTABILIDADE -> "Contabilidade";
            case VALUATION -> "Valuation";
            case FINANCE_SUPPORT -> "Finance Support";
            case CONSORCIO -> "Consórcio";
            case DESENVOLVIMENTO_SOFTWARE -> "Desenvolvimento de Software";
        };
    }

    public String nomeEmpresa(Empresa empresa) {
        String cnpj = empresa.getCnpj().replaceAll("\\D", "");
        if (cnpj.length() == 14) cnpj = "%s.%s.%s/%s-%s".formatted(cnpj.substring(0, 2), cnpj.substring(2, 5),
                cnpj.substring(5, 8), cnpj.substring(8, 12), cnpj.substring(12));
        String nome = empresa.getNomeFantasia();
        if (nome == null || nome.isBlank()) nome = empresa.getRazaoSocial();
        return nome.trim() + "-" + cnpj;
    }
}
