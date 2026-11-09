package org.example.sto.config;

/**
 * Параметры подключения и пула соединений, прочитанные из application.properties
 * с учётом переменных окружения.
 */
public record DbSettings(
        String url,
        String username,
        String password,
        String driver,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long validationTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs,
        int retryMaxAttempts,
        long retryInitialDelayMs,
        double retryMultiplier,
        long retryMaxDelayMs
) {

    public DbSettings {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Не задан параметр db.url");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Не задан параметр db.username");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("db.pool.maximumPoolSize должен быть >= 1");
        }
        if (retryMaxAttempts < 1) {
            throw new IllegalArgumentException("db.retry.maxAttempts должен быть >= 1");
        }
    }

    /** Безопасное представление настроек для журнала (пароль не выводится). */
    public String toSafeString() {
        return "url=" + url
                + ", username=" + username
                + ", poolSize=" + maximumPoolSize
                + ", connectionTimeoutMs=" + connectionTimeoutMs
                + ", retryMaxAttempts=" + retryMaxAttempts;
    }
}
