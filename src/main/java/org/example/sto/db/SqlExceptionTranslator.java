package org.example.sto.db;

import org.example.sto.exception.ConnectionUnavailableException;
import org.example.sto.exception.DataIntegrityException;
import org.example.sto.exception.DatabaseException;
import org.example.sto.exception.DuplicateKeyException;
import org.example.sto.exception.ForeignKeyViolationException;
import org.example.sto.exception.QueryException;
import org.example.sto.exception.TransientDatabaseException;

import java.sql.SQLException;

/**
 * Преобразует {@link SQLException} в предметные исключения приложения.
 *
 * <p>Классы SQLState:
 * <ul>
 *     <li>08xxx, 57P03 — потеря/недоступность соединения;</li>
 *     <li>23xxx — нарушение целостности (23505 — UNIQUE, 23503 — FK, 23502 — NOT NULL);</li>
 *     <li>22xxx — ошибки данных (22001 — превышение длины);</li>
 *     <li>28xxx — аутентификация;</li>
 *     <li>42xxx — синтаксис SQL и несуществующие объекты;</li>
 *     <li>40001, 40P01 — временные сбои транзакций.</li>
 * </ul>
 */
public final class SqlExceptionTranslator {

    private SqlExceptionTranslator() {
    }

    public static DatabaseException translate(SQLException exception, String operation) {
        SQLException source = effectiveException(exception);
        String state = source.getSQLState();
        String details = trim(source.getMessage());

        if (state != null) {
            if (state.startsWith("08") || "57P03".equals(state)) {
                return new ConnectionUnavailableException(
                        "База данных недоступна. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if ("40001".equals(state) || "40P01".equals(state)) {
                return new TransientDatabaseException(
                        "Транзакция прервана из-за конкуренции (" + state + "). Операция: " + operation,
                        operation, state, exception);
            }
            if ("23505".equals(state)) {
                return new DuplicateKeyException(
                        "Нарушено ограничение уникальности. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if ("23503".equals(state)) {
                return new ForeignKeyViolationException(
                        "Нарушено ограничение внешнего ключа. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if (state.startsWith("23")) {
                return new DataIntegrityException(
                        "Нарушено ограничение целостности данных. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if ("22001".equals(state)) {
                return new DataIntegrityException(
                        "Превышена допустимая длина строкового значения. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if (state.startsWith("28")) {
                return new ConnectionUnavailableException(
                        "Ошибка аутентификации при подключении к БД. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
            if (state.startsWith("42")) {
                return new QueryException(
                        "Ошибка SQL-запроса. Операция: " + operation + ". Детали: " + details,
                        operation, state, exception);
            }
        }

        return new DatabaseException(
                "Ошибка выполнения операции с БД: " + operation + ". Детали: " + details,
                operation, state, exception);
    }

    /**
     * PostgreSQL может складывать исходную ошибку в цепочку nextException.
     */
    private static SQLException effectiveException(SQLException exception) {
        SQLException current = exception;
        SQLException result = exception;
        while (current != null) {
            if (current.getSQLState() != null) {
                result = current;
            }
            current = current.getNextException();
        }
        return result;
    }

    private static String trim(String message) {
        if (message == null || message.isBlank()) {
            return "без описания";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}
