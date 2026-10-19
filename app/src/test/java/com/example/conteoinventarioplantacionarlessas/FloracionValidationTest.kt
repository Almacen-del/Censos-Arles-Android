package com.example.conteoinventarioplantacionarlessas

import com.example.conteoinventarioplantacionarlessas.ui.FloracionValidation
import org.junit.Assert.*
import org.junit.Test

class FloracionValidationTest {
    @Test fun deadTreePreservesUnknownSexWithoutMeasurements() {
        assertTrue(FloracionValidation.complete(null, null, null, muerto=true))
        assertTrue(FloracionValidation.complete("Hembra", null, null, muerto=true))
        assertFalse(FloracionValidation.complete("Hembra", 10, 2, muerto=true))
        assertFalse(FloracionValidation.complete(null, null, null))
    }
    @Test fun missingIsNotZero() {
        assertFalse(FloracionValidation.complete("Hembra", null, null))
        assertTrue(FloracionValidation.complete("Hembra", 0, null))
        assertFalse(FloracionValidation.complete("Hembra", 0, 3))
    }
    @Test fun sampleRequiredWhenThereAreInflorescences() {
        assertFalse(FloracionValidation.complete("Hembra", 10, null))
        assertTrue(FloracionValidation.complete("Hembra", 10, 0))
        assertTrue(FloracionValidation.complete("Hembra", 10, 5))
        assertFalse(FloracionValidation.complete(null, 10, 5))
    }
    @Test fun rejectNegativeDecimalOverflowAndText() {
        listOf("-1", "1.5", "abc", "2147483648").forEach { assertFalse(FloracionValidation.validNumber(it)) }
        listOf("", "0", "2147483647").forEach { assertTrue(FloracionValidation.validNumber(it)) }
        assertFalse(FloracionValidation.complete("Hembra", -1, 1))
        assertFalse(FloracionValidation.complete("Hembra", 1, -1))
    }
    @Test fun maleDoesNotSilentlySkipMeasurements() {
        assertFalse(FloracionValidation.complete("Macho", null, null))
        assertTrue(FloracionValidation.complete("Macho", 1, 0))
    }
}
