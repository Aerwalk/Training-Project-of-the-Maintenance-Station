package org.example.sto.db;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

/**
 * Политика повторных попыток с экспоненциальной задержкой.
 *
 * <p>Повторяются только временные сбои: потеря/недоступность соединения,
 * перезапуск сервера БД, сериализационные ошибки и deadlock. Ошибки
 * целостности, синтаксиса и аутентификации не повторяются.
 */
public final class RetryPolicy {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double multiplier;
    private final long maxDelayMs;
    private final Sleeper sleeper;

    public RetryPolicy(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs) {
        this(maxAttempts, initialDelayMs, multiplier, maxDelayMs, Thread::sleep);
    }

    public RetryPolicy(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts должен быть >= 1");
        }
        if (initialDelayMs < 0 || maxDelayMs < 0) {
            throw new IllegalArgumentException("Задержки не могут быть отрицательными");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier должен быть >= 1.0");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
        this.sleeper = sleeper;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Задержка перед повтором после указанной неудачной попытки (нумерация с 1).
     */
    public long delayMillis(int failedAttempt) {
        if (failedAttempt < 1) {
            return initialDelayMs;
        }
        double delay = initialDelayMs * Math.pow(multiplier, failedAttempt - 1L);
        return Math.min(maxDelayMs, Math.max(0L, (long) delay));
    }

    public void sleepBeforeRetry(int failedAttempt) throws InterruptedException {
        long delay = delayMillis(failedAttempt);
        if (delay > 0) {
            sleeper.sleep(delay);
        }
    }

    /**
     * Определяет, является ли сбой временным и безопасным для повтора.
     */
    public static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (current instanceof SQLTransientConnectionException
                    || current instanceof SQLRecoverableException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (state != null && (state.startsWith("08")
                        || "57P03".equals(state)
                        || "40001".equals(state)
                        || "40P01".equals(state))) {
                    return true;
                }
            }
            if (current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
