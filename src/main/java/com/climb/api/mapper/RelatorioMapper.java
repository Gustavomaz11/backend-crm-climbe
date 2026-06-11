package com.climb.api.mapper;

import com.climb.api.model.Relatorio;
import com.climb.api.model.dto.RelatorioRequestDTO;
import com.climb.api.model.dto.RelatorioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RelatorioMapper {

    RelatorioMapper INSTANCE = Mappers.getMapper(RelatorioMapper.class);

    @Mapping(target = "contratoId", source = "contrato.idContrato")
    RelatorioResponseDTO toResponseDto(Relatorio relatorio);

    @Mapping(target = "idRelatorio", ignore = true)
    @Mapping(target = "contrato", ignore = true)
    @Mapping(target = "urlPdf", ignore = true)
    @Mapping(target = "dataEnvio", ignore = true)
    Relatorio toEntity(RelatorioRequestDTO relatorioRequestDTO);

    List<RelatorioResponseDTO> toResponseDto(List<Relatorio> relatorios);
}
