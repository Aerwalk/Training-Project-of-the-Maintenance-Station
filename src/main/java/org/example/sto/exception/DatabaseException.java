package org.example.sto.exception;

/**
 * Базовое проверяемое предметно-ориентированное исключение модуля доступа к БД.
 * Все SQL-ошибки транслируются в наследников этого класса, чтобы прикладной код
 * не зависел от {@code java.sql.SQLException}.
 */
public class DatabaseException extends RuntimeException {

    private final String sqlState;
    private final String operation;

    public DatabaseException(String message, String operation, String sqlState, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.sqlState = sqlState;
    }

    public DatabaseException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public String getSqlState() {
        return sqlState;
    }

    public String getOperation() {
        return operation;
    }
}
