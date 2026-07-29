package com.climb.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void deveResponderUpNoCaminhoConfiguradoNoRender() {
        assertEquals("UP", new HealthController().health().get("status"));
    }
}
