package org.example.sto.exception;

/**
 * Ошибка бизнес-валидации входных данных (не является ошибкой БД).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
