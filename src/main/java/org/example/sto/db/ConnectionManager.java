package org.example.sto.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.sto.config.DbSettings;
import org.example.sto.exception.DatabaseException;
import org.example.sto.exception.RetryExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Центральная точка доступа к БД.
 *
 * <p>Отвечает за:
 * <ul>
 *     <li>пул соединений HikariCP;</li>
 *     <li>повторные попытки получить соединение при временной недоступности БД;</li>
 *     <li>транзакции (commit/rollback);</li>
 *     <li>трансляцию SQLException в предметные исключения.</li>
 * </ul>
 *
 * <p>Экземпляр класса потокобезопасен и должен использоваться как singleton
 * на всё приложение.
 */
public final class ConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final HikariDataSource dataSource;
    private final DbSettings settings;
    private final RetryPolicy retryPolicy;

    public ConnectionManager(DbSettings settings) {
        this(settings, new RetryPolicy(
                settings.retryMaxAttempts(),
                settings.retryInitialDelayMs(),
                settings.retryMultiplier(),
                settings.retryMaxDelayMs()));
    }

    public ConnectionManager(DbSettings settings, RetryPolicy retryPolicy) {
        this.settings = settings;
        this.retryPolicy = retryPolicy;

        HikariConfig config = new HikariConfig();
        config.setPoolName("sto-pool");
        config.setJdbcUrl(settings.url());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setDriverClassName(settings.driver());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setMinimumIdle(settings.minimumIdle());
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setValidationTimeout(settings.validationTimeoutMs());
        config.setIdleTimeout(settings.idleTimeoutMs());
        config.setMaxLifetime(settings.maxLifetimeMs());
        config.setAutoCommit(true);
        // Не падаем на старте, если БД ещё не поднялась: приложение выполнит retry
        config.setInitializationFailTimeout(-1);
        config.setRegisterMbeans(false);

        this.dataSource = new HikariDataSource(config);
        log.info("Пул соединений создан: {}", settings.toSafeString());
    }

    public DbSettings settings() {
        return settings;
    }

    /**
     * Получает соединение с повторными попытками.
     *
     * @throws RetryExhaustedException если все попытки исчерпаны
     */
    public Connection getConnection() {
        SQLException lastError = null;
        int attempts = retryPolicy.maxAttempts();

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Connection connection = dataSource.getConnection();
                if (isValid(connection)) {
                    return connection;
                }
                connection.close();
                lastError = new SQLException("Соединение не прошло проверку isValid()", "08006");
            } catch (SQLException e) {
                lastError = e;
                log.warn("Попытка подключения {}/{} не удалась: {}", attempt, attempts, e.getMessage());
            }

            if (attempt == attempts || !RetryPolicy.isRetryable(lastError)) {
                break;
            }

            try {
                long delay = retryPolicy.delayMillis(attempt);
                log.info("Повтор подключения через {} мс", delay);
                retryPolicy.sleepBeforeRetry(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new RetryExhaustedException(
                "Не удалось подключиться к БД за " + attempts + " попыт(ки). "
                        + "Проверьте, что контейнер PostgreSQL запущен и параметры db.* корректны.",
                attempts,
                lastError);
    }

    private boolean isValid(Connection connection) {
        try {
            int timeoutSeconds = (int) Math.max(1, settings.validationTimeoutMs() / 1000);
            return connection.isValid(timeoutSeconds);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Выполняет операцию с автоматическим закрытием соединения.
     */
    public <T> T withConnection(String operation, SqlWork<T> work) {
        try (Connection connection = getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e, operation);
        }
    }

    /**
     * Выполняет операцию в транзакции. При исключении выполняется rollback.
     */
    public <T> T inTransaction(String operation, SqlWork<T> work) {
        Connection connection = getConnection();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException e) {
            closeQuietly(connection);
            throw SqlExceptionTranslator.translate(e, operation);
        }

        try {
            connection.setAutoCommit(false);
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(connection, e);
            throw SqlExceptionTranslator.translate(e, operation);
        } catch (RuntimeException | Error e) {
            rollbackQuietly(connection, e);
            throw e;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // Соединение уже может быть невалидным - Hikari его утилизирует
            }
            closeQuietly(connection);
        }
    }

    /**
     * Проверка доступности БД (SELECT 1).
     */
    public String ping() {
        return withConnection("ping", connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT version()")) {
                resultSet.next();
                return resultSet.getString(1);
            }
        });
    }

    private void rollbackQuietly(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // ничего не делаем: соединение возвращается пулу/утилизируется
        }
    }

    @Override
    public void close() {
        dataSource.close();
        log.info("Пул соединений закрыт");
    }

    /**
     * Функциональный интерфейс операции с БД, допускающей SQLException.
     */
    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
