package com.climb.api.service;

import com.climb.api.mapper.EmpresaMapper;
import com.climb.api.model.Empresa;
import com.climb.api.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository repository;

    @Mock
    private EmpresaMapper empresaMapper;

    @InjectMocks
    private EmpresaService service;

    @Test
    void deveExcluirEmpresaSemVinculos() {
        Empresa empresa = empresa(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(empresa));

        service.deletar(1L);

        verify(repository).delete(empresa);
        verify(repository).flush();
    }

    @Test
    void deveRetornarConflitoQuandoEmpresaPossuiRegistrosVinculados() {
        Empresa empresa = empresa(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(empresa));
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(repository).flush();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.deletar(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(
                "A empresa não pode ser excluída porque possui registros vinculados.",
                exception.getReason()
        );
    }

    private Empresa empresa(Long id) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(id);
        return empresa;
    }
}
