package org.example.sto.exception;

/**
 * Нет доступного соединения с БД (SQLState 08xxx, 57P03, таймаут пула).
 */
public class ConnectionUnavailableException extends DatabaseException {

    public ConnectionUnavailableException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
