package com.climb.api.mapper;

import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.UsuarioOAuthRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UsuarioMapper {

    @Autowired
    private UsuarioOAuthRepository usuarioOAuthRepository;

    @Mapping(target = "cargoNome", source = "cargo.nome")
    @Mapping(target = "fotoPerfil", source = "fotoPerfilUrl")
    public abstract UsuarioResponseDTO toResponse(Usuario usuario);

    @AfterMapping
    protected void preencherFotoPerfil(Usuario usuario, @MappingTarget UsuarioResponseDTO dto) {
        if (usuario == null || usuario.getId() == null) {
            return;
        }

        if (dto.getFotoPerfil() != null && !dto.getFotoPerfil().isBlank()) {
            return;
        }

        usuarioOAuthRepository.findByUsuarioIdAndProvider(usuario.getId(), OAuthProvider.GOOGLE)
                .map(vinculo -> vinculo.getAvatarUrl())
                .filter(avatarUrl -> avatarUrl != null && !avatarUrl.isBlank())
                .ifPresent(dto::setFotoPerfil);
    }
}
