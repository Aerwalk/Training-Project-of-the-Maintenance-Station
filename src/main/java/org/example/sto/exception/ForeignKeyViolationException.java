package org.example.sto.exception;

/**
 * Нарушение внешнего ключа (SQLState 23503).
 */
public class ForeignKeyViolationException extends DataIntegrityException {

    public ForeignKeyViolationException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
