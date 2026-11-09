package org.example.sto.exception;

/**
 * Запрошенная сущность не найдена.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
