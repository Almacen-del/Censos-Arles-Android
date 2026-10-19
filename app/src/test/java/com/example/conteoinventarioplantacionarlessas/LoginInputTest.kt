package com.example.conteoinventarioplantacionarlessas

import com.example.conteoinventarioplantacionarlessas.ui.LoginInput
import org.junit.Assert.*
import org.junit.Test

class LoginInputTest {
    @Test fun acceptsWorkerAccounts() {
        assertTrue(LoginInput.validEmail("auxiliar1@censosarles.sas"))
        assertTrue(LoginInput.validEmail("auxiliar2@censosarles.sas"))
        assertEquals("auxiliar1@censosarles.sas", LoginInput.email("  auxiliar1@censosarles.sas  "))
    }
    @Test fun rejectsIncompleteAndWhitespaceAddresses() {
        listOf("", "auxiliar1", "auxiliar1@", "a b@censosarles.sas", "a@@censosarles.sas").forEach {
            assertFalse(LoginInput.validEmail(it))
        }
    }
}
