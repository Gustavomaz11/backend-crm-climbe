package com.climb.api.exception;

import com.climb.api.model.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ExceptionHandlerControllerTest {

    private final ExceptionHandlerController handler =
            new ExceptionHandlerController(mock(MessageSource.class));

    @Test
    void devePreservarStatusEMensagemDaExcecao() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Empresa possui registros vinculados")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Empresa possui registros vinculados", response.getBody().message());
    }
}
