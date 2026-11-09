package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Client;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClientRepository {

    private final ConnectionManager connectionManager;

    public ClientRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<Client> findAll() {
        return connectionManager.withConnection("clients.findAll", connection -> {
            String sql = "SELECT id, full_name, phone, email, created_at FROM clients ORDER BY id";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<Client> clients = new ArrayList<>();
                while (resultSet.next()) {
                    clients.add(mapClient(resultSet));
                }
                return clients;
            }
        });
    }

    public Optional<Client> findById(int id) {
        return connectionManager.withConnection("clients.findById", connection -> {
            String sql = "SELECT id, full_name, phone, email, created_at FROM clients WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapClient(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Client> findByPhone(String phone) {
        return connectionManager.withConnection("clients.findByPhone", connection -> {
            String sql = "SELECT id, full_name, phone, email, created_at FROM clients WHERE phone = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, phone);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapClient(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public Client create(String fullName, String phone, String email) {
        return connectionManager.withConnection("clients.create", connection -> {
            String sql = """
                    INSERT INTO clients (full_name, phone, email)
                    VALUES (?, ?, ?)
                    RETURNING id, full_name, phone, email, created_at
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName);
                statement.setString(2, phone);
                if (email == null || email.isBlank()) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, email);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return mapClient(resultSet);
                }
            }
        });
    }

    public boolean update(int id, String fullName, String phone, String email) {
        return connectionManager.withConnection("clients.update", connection -> {
            String sql = "UPDATE clients SET full_name = ?, phone = ?, email = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName);
                statement.setString(2, phone);
                if (email == null || email.isBlank()) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, email);
                }
                statement.setInt(4, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean delete(int id) {
        return connectionManager.withConnection("clients.delete", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clients WHERE id = ?")) {
                statement.setInt(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Client mapClient(ResultSet resultSet) throws SQLException {
        return new Client(
                resultSet.getInt("id"),
                resultSet.getString("full_name"),
                resultSet.getString("phone"),
                resultSet.getString("email"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
