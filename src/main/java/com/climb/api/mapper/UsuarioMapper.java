package com.climb.api.mapper;

import com.climb.api.model.Usuario;
import com.climb.api.model.dto.UsuarioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class UsuarioMapper {
    @Mapping(target = "cargoNome", source = "cargo.nome")
    @Mapping(target = "fotoPerfil", source = "fotoPerfilUrl")
    public abstract UsuarioResponseDTO toResponse(Usuario usuario);
}
