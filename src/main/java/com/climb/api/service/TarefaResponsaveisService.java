package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TarefaResponsaveisService {
    private final UsuarioRepository usuarios;
    public TarefaResponsaveisService(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    public Set<Usuario> resolver(List<Long> ids, Long legado, boolean obrigatorio) {
        List<Long> selecionados = ids != null ? ids : legado == null ? List.of() : List.of(legado);
        if (obrigatorio && selecionados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione pelo menos um responsável para a tarefa.");
        }
        Set<Usuario> resultado = new LinkedHashSet<>();
        for (Long id : new LinkedHashSet<>(selecionados)) {
            if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione responsáveis válidos para a tarefa.");
            Usuario usuario = usuarios.findById(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável da tarefa não encontrado."));
            if (!"ATIVO".equals(usuario.getSituacao())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável da tarefa precisa ser um usuário ativo.");
            }
            resultado.add(usuario);
        }
        return resultado;
    }

    public static Usuario daSubtarefa(Long id, Collection<Usuario> responsaveis) {
        if (id == null) return null;
        return responsaveis.stream().filter(usuario -> id.equals(usuario.getId())).findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "O responsável da subtarefa deve estar atribuído à tarefa principal."));
    }
}
