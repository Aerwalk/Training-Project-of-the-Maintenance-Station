package org.example.sto.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Загрузчик конфигурации приложения.
 *
 * <p>Порядок поиска файла:
 * <ol>
 *     <li>{@code -Dconfig.file=/path/to/file.properties};</li>
 *     <li>{@code application.properties} в classpath (src/main/resources);</li>
 *     <li>встроенные значения по умолчанию.</li>
 * </ol>
 *
 * <p>Переменные окружения имеют приоритет над файлом:
 * {@code STO_DB_URL}, {@code STO_DB_USERNAME}, {@code STO_DB_PASSWORD},
 * {@code STO_DB_MAX_POOL_SIZE}, {@code STO_DB_RETRY_MAX_ATTEMPTS}.
 */
public final class AppConfig {

    private static final String DEFAULT_RESOURCE = "application.properties";

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() {
        Properties properties = new Properties();

        String externalFile = System.getProperty("config.file");
        if (externalFile != null && !externalFile.isBlank()) {
            try (Reader reader = Files.newBufferedReader(Path.of(externalFile), StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать файл конфигурации: " + externalFile, e);
            }
        } else {
            try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Не найден " + DEFAULT_RESOURCE + " в classpath");
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать " + DEFAULT_RESOURCE, e);
            }
        }

        applyEnvironmentOverrides(properties);
        return new AppConfig(properties);
    }

    private static void applyEnvironmentOverrides(Properties properties) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("db.url", "STO_DB_URL");
        mapping.put("db.username", "STO_DB_USERNAME");
        mapping.put("db.password", "STO_DB_PASSWORD");
        mapping.put("db.pool.maximumPoolSize", "STO_DB_MAX_POOL_SIZE");
        mapping.put("db.retry.maxAttempts", "STO_DB_RETRY_MAX_ATTEMPTS");

        mapping.forEach((key, envName) -> {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                properties.setProperty(key, value);
            }
        });
    }

    public String getString(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + " должен быть целым числом, получено: " + value, e);
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + " должен быть числом, получено: " + value, e);
        }
    }

    public double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + " должен быть числом, получено: " + value, e);
        }
    }

    public DbSettings dbSettings() {
        return new DbSettings(
                getString("db.url", "jdbc:postgresql://localhost:5434/sto"),
                getString("db.username", "sto_admin"),
                getString("db.password", ""),
                getString("db.driver", "org.postgresql.Driver"),
                getInt("db.pool.maximumPoolSize", 5),
                getInt("db.pool.minimumIdle", 1),
                getLong("db.pool.connectionTimeoutMs", 3_000),
                getLong("db.pool.validationTimeoutMs", 1_000),
                getLong("db.pool.idleTimeoutMs", 60_000),
                getLong("db.pool.maxLifetimeMs", 600_000),
                getInt("db.retry.maxAttempts", 4),
                getLong("db.retry.initialDelayMs", 250),
                getDouble("db.retry.multiplier", 2.0),
                getLong("db.retry.maxDelayMs", 4_000)
        );
    }
}
