package org.example.sto.exception;

/**
 * Временный сбой: сериализация транзакции или взаимная блокировка (SQLState 40001/40P01).
 * Такую операцию безопасно повторить.
 */
public class TransientDatabaseException extends DatabaseException {

    public TransientDatabaseException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
