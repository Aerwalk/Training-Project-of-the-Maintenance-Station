package org.example.sto.model;

/**
 * Пользователь системы. Поле passwordHash намеренно отсутствует:
 * хеш не покидает слой доступа к данным.
 */
public record User(int id, String username, String fullName, String role) {
}
