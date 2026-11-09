package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Доступ к таблицам roles/users. Проверка пароля выполняется в PostgreSQL
 * функцией crypt() из расширения pgcrypto, поэтому хеш не загружается в Java.
 */
public final class UserRepository {

    private final ConnectionManager connectionManager;

    public UserRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public Optional<User> authenticate(String username, char[] password) {
        String passwordText = new String(password);
        try {
            String finalPassword = passwordText;
            return connectionManager.withConnection("users.authenticate", connection -> {
                String sql = """
                        SELECT u.id, u.username, u.full_name, r.name AS role
                        FROM users u
                        JOIN roles r ON r.id = u.role_id
                        WHERE u.username = ?
                          AND u.password_hash = crypt(?, u.password_hash)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, username);
                    statement.setString(2, finalPassword);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
                    }
                }
            });
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public List<User> findAll() {
        return connectionManager.withConnection("users.findAll", connection -> {
            String sql = """
                    SELECT u.id, u.username, u.full_name, r.name AS role
                    FROM users u
                    JOIN roles r ON r.id = u.role_id
                    ORDER BY u.id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (resultSet.next()) {
                    users.add(mapUser(resultSet));
                }
                return users;
            }
        });
    }

    private User mapUser(ResultSet resultSet) throws java.sql.SQLException {
        return new User(
                resultSet.getInt("id"),
                resultSet.getString("username"),
                resultSet.getString("full_name"),
                resultSet.getString("role")
        );
    }
}
