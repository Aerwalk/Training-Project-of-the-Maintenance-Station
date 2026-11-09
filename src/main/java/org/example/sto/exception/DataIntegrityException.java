package org.example.sto.exception;

/**
 * Нарушение ограничения целостности БД (класс SQLState 23).
 */
public class DataIntegrityException extends DatabaseException {

    public DataIntegrityException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
