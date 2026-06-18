package com.climb.api.mapper;

import com.climb.api.model.Documento;
import com.climb.api.model.dto.DocumentoResponseDTO;
import com.climb.api.model.dto.DocumentoSolicitacaoRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentoMapper {

    DocumentoMapper INSTANCE = Mappers.getMapper(DocumentoMapper.class);

    @Mapping(target = "id",           source = "idDocumento")
    @Mapping(target = "empresaId",    source = "empresa.idEmpresa")
    @Mapping(target = "nomeEmpresa",  source = "empresa.nomeFantasia")
    @Mapping(target = "analistaId",   source = "analista.id")
    @Mapping(target = "nomeAnalista", source = "analista.nomeCompleto")
    DocumentoResponseDTO toResponseDto(Documento documento);

    @Mapping(target = "idDocumento", ignore = true)
    @Mapping(target = "empresa",     ignore = true)
    @Mapping(target = "analista",    ignore = true)
    @Mapping(target = "url",         ignore = true)
    @Mapping(target = "validado",    ignore = true)
    @Mapping(target = "emailDestinatario", ignore = true)
    @Mapping(target = "tokenUpload", ignore = true)
    @Mapping(target = "tokenExpiraEm", ignore = true)
    @Mapping(target = "dataSolicitacao", ignore = true)
    @Mapping(target = "dataEnvio", ignore = true)
    Documento toEntity(DocumentoSolicitacaoRequestDTO dto);

    List<DocumentoResponseDTO> toResponseDto(List<Documento> documentos);
}
