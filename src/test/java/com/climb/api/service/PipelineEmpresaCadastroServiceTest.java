package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineEmpresaCadastroServiceTest {
    @Mock private EmpresaRepository empresaRepository;

    @Test
    void deveCriarEmpresaComDadosDoNovoNegocio() {
        PipelineEmpresaCadastroService service = new PipelineEmpresaCadastroService(empresaRepository);
        PipelineNegocioRequestDTO request = new PipelineNegocioRequestDTO(
                null, null, "Apex Ventures", "Maria Silva", "11999999999", "MARIA@APEX.COM",
                1L, null, LocalDateTime.now(), "Indicação", "Ativa", "BPO",
                new BigDecimal("1000"), null, List.of("BPO"), true, "11.222.333/0001-81"
        );
        when(empresaRepository.findFirstByCnpjIn(any())).thenReturn(Optional.empty());
        when(empresaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cadastrar(request);

        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        org.mockito.Mockito.verify(empresaRepository).saveAndFlush(captor.capture());
        Empresa empresa = captor.getValue();
        assertEquals("Apex Ventures", empresa.getNomeFantasia());
        assertEquals("11.222.333/0001-81", empresa.getCnpj());
        assertEquals("maria@apex.com", empresa.getEmail());
        assertEquals("Maria Silva", empresa.getRepresentanteNome());
    }
}
