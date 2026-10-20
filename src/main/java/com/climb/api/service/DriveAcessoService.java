package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.ContratoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class DriveAcessoService {
    private final ContratoEquipeService equipes;
    private final ContratoRepository contratos;
    private final GoogleDriveProperties properties;
    public DriveAcessoService(ContratoEquipeService equipes, ContratoRepository contratos, GoogleDriveProperties properties) {
        this.equipes = equipes; this.contratos = contratos; this.properties = properties;
    }
    public List<Usuario> usuarios(Contrato contrato, ServicoComercial servico) {
        if (!ContratoService.STATUS_APROVADO.equals(contrato.getStatus()) || ContratoServicos.encerrado(contrato, servico)
                || !ContratoServicos.listar(contrato).contains(servico)) return List.of();
        return equipes.membrosAtuais(contrato, servico).stream()
                .filter(u -> "ATIVO".equals(u.getSituacao()) && emailPermitido(u.getEmail())).toList();
    }
    @Transactional(readOnly = true)
    public Set<String> emails(Long empresaId, ServicoComercial servico) {
        var emails = new TreeSet<String>();
        contratos.findByEmpresa_IdEmpresaOrderByIdContratoDesc(empresaId).forEach(c ->
                usuarios(c, servico).forEach(u -> emails.add(u.getEmail().trim().toLowerCase(Locale.ROOT))));
        return emails;
    }
    public void exigir(Contrato contrato, ServicoComercial servico, Long usuarioId) {
        if (usuarios(contrato, servico).stream().noneMatch(u -> Objects.equals(u.getId(), usuarioId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O acesso ao Drive é exclusivo da equipe corporativa deste serviço.");
    }
    public boolean emailPermitido(String email) {
        if (email == null) return false;
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        int arroba = normalizado.indexOf('@');
        if (arroba <= 0 || arroba != normalizado.lastIndexOf('@')) return false;
        String dominio = normalizado.substring(arroba + 1);
        return properties.getAllowedDomains().stream().anyMatch(d -> dominio.equals(d.trim().toLowerCase(Locale.ROOT)));
    }
}
