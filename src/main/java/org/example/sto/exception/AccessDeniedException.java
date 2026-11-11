package org.example.sto.exception;

/**
 * Действие недоступно текущей роли или учётной записи.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
