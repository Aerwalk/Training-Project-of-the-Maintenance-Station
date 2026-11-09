package org.example.sto.exception;

/**
 * Ошибка аутентификации: неверный логин/пароль (SQLState 28xxx либо пустой результат).
 */
public class AuthenticationException extends DatabaseException {

    public AuthenticationException(String message) {
        super(message, "authenticate", "28000", null);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, "authenticate", "28000", cause);
    }
}
