package com.climb.api.mapper;

import com.climb.api.model.Usuario;
import com.climb.api.model.dto.UsuarioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "cargoNome", source = "cargo.nome")
    UsuarioResponseDTO toResponse(Usuario usuario);
}
