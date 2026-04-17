package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.service.updater.InstallerArgValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hardens the installer-argument allow-list against shell-injection in
 * package manifests.  The validator is the choke-point that prevents a
 * compromised offline package from smuggling commands into
 * {@code msiexec}.
 */
class InstallerArgValidatorTest {

    @Test
    void acceptsStandardPublicProperties() {
        List<String> out = InstallerArgValidator.sanitize(List.of(
            "INSTALLDIR=C:\\EaglePoint",
            "ENVIRONMENT=prod",
            "REBOOT=ReallySuppress"
        ));
        assertEquals(3, out.size());
        assertEquals("INSTALLDIR=C:\\EaglePoint", out.get(0));
    }

    @Test
    void rejectsLowerCaseKey() {
        ValidationException ve = assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("installdir=C:\\ok")));
        assertTrue(ve.getMessage().toLowerCase().contains("upper-snake"));
    }

    @Test
    void rejectsMissingEquals() {
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("INSTALLDIR")));
    }

    @Test
    void rejectsShellMetacharsInValue() {
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("NAME=foo; rm -rf /")));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("NAME=foo | echo")));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("NAME=$(whoami)")));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of("NAME=foo`ls`")));
    }

    @Test
    void rejectsOversizedArg() {
        StringBuilder big = new StringBuilder("KEY=");
        big.append("x".repeat(260));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.sanitize(List.of(big.toString())));
    }

    @Test
    void nullListReturnsEmptyList() {
        assertEquals(0, InstallerArgValidator.sanitize(null).size());
    }

    @Test
    void validateProductCodeAcceptsBracedGuid() {
        InstallerArgValidator.validateProductCode("{12345678-1234-1234-1234-1234567890AB}");
        InstallerArgValidator.validateProductCode("12345678-1234-1234-1234-1234567890AB");
    }

    @Test
    void validateProductCodeRejectsGarbage() {
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.validateProductCode(null));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.validateProductCode("not-a-guid"));
        assertThrows(ValidationException.class,
            () -> InstallerArgValidator.validateProductCode("12345"));
    }
}
