package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DrivePermissoesService {
    private final EmpresaDriveServicoRepository pastas;
    private final DriveAcessoService acesso;
    private final GoogleDriveClient drive;
    private final GoogleDrivePermissoesClient permissoes;
    private final GoogleDriveProperties properties;
    public DrivePermissoesService(EmpresaDriveServicoRepository pastas, DriveAcessoService acesso,
            GoogleDriveClient drive, GoogleDrivePermissoesClient permissoes, GoogleDriveProperties properties) {
        this.pastas = pastas; this.acesso = acesso; this.drive = drive; this.permissoes = permissoes; this.properties = properties;
    }
    @Transactional
    public void sincronizar(Long id) {
        var pasta = pastas.bloquear(id).orElse(null);
        if (pasta == null || pasta.getPastaId() == null || pasta.getPermissoesTentarEm().isAfter(LocalDateTime.now())) return;
        try {
            String papel = properties.getServiceRole();
            if (papel == null || !Set.of("reader", "writer").contains(papel)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O papel do Drive deve ser reader ou writer.");
            var emails = acesso.emails(pasta.getEmpresaId(), pasta.getServico());
            var identidade = permissoes.identidade();
            if (identidade.id() == null || identidade.email() == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível identificar a conta responsável pelo Drive.");
            boolean contaDireta = permissoes.listar(pasta.getPastaId()).stream().anyMatch(p ->
                    Objects.equals(p.id(), identidade.id()) && (p.administrativa() || !p.herdada() && !p.metadados()));
            if (!contaDireta) permissoes.conceder(pasta.getPastaId(), identidade.email(), "writer");
            // Nunca compartilha a pasta da empresa ou a raiz geral.
            permissoes.limitar(pasta.getPastaId(), true);
            reconciliar(pasta.getPastaId(), emails, identidade, papel, true);
            var pendentes = new ArrayDeque<String>(); pendentes.add(pasta.getPastaId());
            var visitados = new HashSet<String>();
            while (!pendentes.isEmpty()) {
                String atual = pendentes.removeFirst();
                if (!visitados.add(atual)) continue;
                if (visitados.size() > 10000) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A árvore de pastas excedeu o limite da sincronização de permissões.");
                for (var item : drive.listar(atual)) {
                    reconciliar(item.id(), emails, identidade, papel, false);
                    if (item.pasta()) { permissoes.limitar(item.id(), false); pendentes.add(item.id()); }
                }
            }
            pasta.setPermissoesErro(null); pasta.setPermissoesSincronizadasEm(LocalDateTime.now());
            pasta.setPermissoesTentarEm(LocalDateTime.now().plusHours(1));
        } catch (ResponseStatusException exception) {
            pasta.setPermissoesErro(exception.getReason()); pasta.setPermissoesTentarEm(LocalDateTime.now().plusMinutes(1));
        }
        pastas.save(pasta);
    }

    private void reconciliar(String id, Set<String> emails, GoogleDrivePermissoesClient.Identidade identidade,
            String papel, boolean raiz) {
        var presentes = new HashSet<String>();
        for (var p : permissoes.listar(id)) {
            if (p.administrativa() || Objects.equals(p.id(), identidade.id())) {
                if (p.email() != null) presentes.add(p.email().trim().toLowerCase(Locale.ROOT));
                continue;
            }
            if (p.herdada() || p.metadados()) continue;
            String email = p.email() == null ? "" : p.email().trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(p.tipo()) || !emails.contains(email)) { permissoes.remover(id, p.id()); continue; }
            presentes.add(email);
            if (!papel.equals(p.papel())) permissoes.alterar(id, p.id(), papel);
        }
        if (raiz) {
            presentes.add(identidade.email().toLowerCase(Locale.ROOT));
            for (String email : emails) if (!presentes.contains(email)) permissoes.conceder(id, email, papel);
        }
    }
}
