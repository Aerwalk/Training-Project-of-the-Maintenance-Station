package org.example.sto.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbSettingsTest {

    @Test
    void readsClasspathConfiguration() {
        DbSettings settings = AppConfig.load().dbSettings();

        assertTrue(settings.url().startsWith("jdbc:postgresql://"));
        assertEquals("sto_admin", settings.username());
        assertEquals(5, settings.maximumPoolSize());
        assertEquals(4, settings.retryMaxAttempts());
    }

    @Test
    void doesNotExposePasswordInSafeString() {
        DbSettings settings = new DbSettings(
                "jdbc:postgresql://localhost:5434/sto", "sto_admin", "secret",
                "org.postgresql.Driver", 5, 1, 3000, 1000, 60000, 600000,
                4, 250, 2.0, 4000);

        assertTrue(settings.toSafeString().contains("sto_admin"));
        assertTrue(!settings.toSafeString().contains("secret"));
    }

    @Test
    void validatesMandatoryParameters() {
        assertThrows(IllegalArgumentException.class, () -> new DbSettings(
                "", "user", "pass", "org.postgresql.Driver", 5, 1, 3000, 1000,
                60000, 600000, 4, 250, 2.0, 4000));

        assertThrows(IllegalArgumentException.class, () -> new DbSettings(
                "jdbc:postgresql://localhost/sto", "user", "pass", "org.postgresql.Driver",
                0, 0, 3000, 1000, 60000, 600000, 4, 250, 2.0, 4000));
    }
}
