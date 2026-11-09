package org.example.sto.exception;

/**
 * Синтаксическая ошибка SQL или обращение к несуществующему объекту (класс SQLState 42).
 */
public class QueryException extends DatabaseException {

    public QueryException(String message, String operation, String sqlState, Throwable cause) {
        super(message, operation, sqlState, cause);
    }
}
