package org.example.sto.db;

import org.example.sto.exception.ConnectionUnavailableException;
import org.example.sto.exception.DataIntegrityException;
import org.example.sto.exception.DatabaseException;
import org.example.sto.exception.DuplicateKeyException;
import org.example.sto.exception.ForeignKeyViolationException;
import org.example.sto.exception.QueryException;
import org.example.sto.exception.TransientDatabaseException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExceptionTranslatorTest {

    @Test
    void translatesUniqueViolation() {
        DatabaseException result = SqlExceptionTranslator.translate(
                new SQLException("duplicate key value violates unique constraint \"clients_phone_key\"", "23505"),
                "clients.create");

        assertInstanceOf(DuplicateKeyException.class, result);
        assertEquals("23505", result.getSqlState());
        assertEquals("clients.create", result.getOperation());
    }

    @Test
    void translatesForeignKeyViolation() {
        DatabaseException result = SqlExceptionTranslator.translate(
                new SQLException("insert or update violates foreign key", "23503"), "cars.create");
        assertInstanceOf(ForeignKeyViolationException.class, result);
    }

    @Test
    void translatesNotNullAndLengthViolationsToIntegrityError() {
        assertInstanceOf(DataIntegrityException.class,
                SqlExceptionTranslator.translate(new SQLException("null value", "23502"), "test"));
        assertInstanceOf(DataIntegrityException.class,
                SqlExceptionTranslator.translate(new SQLException("value too long", "22001"), "test"));
    }

    @Test
    void translatesConnectionProblems() {
        assertInstanceOf(ConnectionUnavailableException.class,
                SqlExceptionTranslator.translate(new SQLException("connection refused", "08001"), "connect"));
        assertInstanceOf(ConnectionUnavailableException.class,
                SqlExceptionTranslator.translate(new SQLException("starting up", "57P03"), "connect"));
    }

    @Test
    void translatesSyntaxErrors() {
        DatabaseException result = SqlExceptionTranslator.translate(
                new SQLException("syntax error at or near \"SELEC\"", "42601"), "query");
        assertInstanceOf(QueryException.class, result);
        assertTrue(result.getMessage().contains("SELEC"));
    }

    @Test
    void translatesSerializationFailure() {
        assertInstanceOf(TransientDatabaseException.class,
                SqlExceptionTranslator.translate(new SQLException("could not serialize", "40001"), "update"));
    }

    @Test
    void fallsBackToGenericDatabaseException() {
        DatabaseException result = SqlExceptionTranslator.translate(
                new SQLException("unknown", "XX000"), "unknown.operation");
        assertEquals(DatabaseException.class, result.getClass());
        assertEquals("XX000", result.getSqlState());
    }

    @Test
    void usesSqlStateFromNextExceptionChain() {
        SQLException wrapper = new SQLException("batch failed", "99999");
        wrapper.setNextException(new SQLException("duplicate", "23505"));

        DatabaseException result = SqlExceptionTranslator.translate(wrapper, "batch");
        assertInstanceOf(DuplicateKeyException.class, result);
    }
}
