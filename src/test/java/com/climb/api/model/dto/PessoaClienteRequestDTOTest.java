package com.climb.api.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PessoaClienteRequestDTOTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveExigirCpfPreenchido(String cpf) {
        var violations = validator.validate(request(cpf));

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("cpf")
                        && violation.getMessage().equals("Por favor, informe o CPF da pessoa.")));
    }

    @Test
    void deveAceitarCpfValido() {
        assertTrue(validator.validate(request("529.982.247-25")).isEmpty());
    }

    @Test
    void deveContinuarRejeitandoCpfInvalido() {
        assertTrue(validator.validate(request("111.111.111-11")).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("cpf")));
    }

    private PessoaClienteRequestDTO request(String cpf) {
        return new PessoaClienteRequestDTO("Maria Silva", cpf, null, null,
                null, null, true, Set.of(1L));
    }
}
