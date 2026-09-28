package com.nexus.web.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("local")
@Tag("dev")
@Tag("prod")
class AppAuthSmsCodeTest {
    @Test
    void echoedCodeMatchesFourDigitAppInput() {
        for (int i = 0; i < 20; i++) {
            assertTrue(AppAuthController.generateSmsCode().matches("\\d{4}"));
        }
    }
}
