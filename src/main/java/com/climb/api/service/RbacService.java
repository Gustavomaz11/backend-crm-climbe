package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RbacService {

    private final UsuarioRepository usuarioRepository;

    public RbacService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Set<PermissaoCodigo> getPermissoesDoUsuario(Long usuarioId) {

        List<String> codigos = usuarioRepository.findCodigosPermissoesById(usuarioId);
        if (codigos.isEmpty()) {
            throw new RuntimeException("Usuário não encontrado: id=" + usuarioId);
        }
        Set<PermissaoCodigo> permissoes = EnumSet.noneOf(PermissaoCodigo.class);
        for (String codigoPersistido : codigos) {
            if (codigoPersistido == null) continue;
            try {
                PermissaoCodigo codigo = PermissaoCodigo.valueOf(codigoPersistido);
                permissoes.add(codigo);
            } catch (IllegalArgumentException e) {
                // Código inválido no banco — ignora
            }
        }

        return permissoes;
    }

    public boolean temPermissao(Long usuarioId, PermissaoCodigo permissao) {
        return getPermissoesDoUsuario(usuarioId).contains(permissao);
    }

    public boolean temTodasPermissoes(Long usuarioId, PermissaoCodigo... permissoes) {
        Set<PermissaoCodigo> permissoesDoUsuario = getPermissoesDoUsuario(usuarioId);
        for (PermissaoCodigo p : permissoes) {
            if (!permissoesDoUsuario.contains(p)) return false;
        }
        return true;
    }

    public boolean temAlgumaPermissao(Long usuarioId, PermissaoCodigo... permissoes) {
        Set<PermissaoCodigo> permissoesDoUsuario = getPermissoesDoUsuario(usuarioId);
        for (PermissaoCodigo p : permissoes) {
            if (permissoesDoUsuario.contains(p)) return true;
        }
        return false;
    }
}
