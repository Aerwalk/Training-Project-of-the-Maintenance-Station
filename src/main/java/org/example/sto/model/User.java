package org.example.sto.model;

/**
 * Пользователь системы. Поле passwordHash намеренно отсутствует:
 * хеш не покидает слой доступа к данным.
 *
 * <p>{@code clientId} заполнен только для учётных записей с ролью client
 * и связывает их с карточкой клиента. По этому полю клиент видит
 * исключительно собственные заявки.
 */
public record User(int id, String username, String fullName, String role, Integer clientId) {
}
