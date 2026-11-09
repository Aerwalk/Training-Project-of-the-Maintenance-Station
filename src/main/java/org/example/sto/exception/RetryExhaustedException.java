package org.example.sto.exception;

/**
 * Исчерпаны повторные попытки подключения к БД.
 */
public class RetryExhaustedException extends ConnectionUnavailableException {

    private final int attempts;

    public RetryExhaustedException(String message, int attempts, Throwable cause) {
        super(message, "connect", "08001", cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}
