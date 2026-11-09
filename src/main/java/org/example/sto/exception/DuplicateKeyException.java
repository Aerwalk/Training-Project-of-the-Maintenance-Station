package org.example.sto.exception;

/**
 * Нарушение UNIQUE-ограничения (SQLState 23505): дубликат телефона, гос. номера и т.п.
 */
public class DuplicateKeyException extends DataIntegrityException {

    public DuplicateKeyException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
