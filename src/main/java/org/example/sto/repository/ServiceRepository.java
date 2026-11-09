package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ServiceRepository {

    private final ConnectionManager connectionManager;

    public ServiceRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<Service> findAll() {
        return connectionManager.withConnection("services.findAll", connection -> {
            String sql = "SELECT id, name, price, duration_min FROM services ORDER BY id";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<Service> services = new ArrayList<>();
                while (resultSet.next()) {
                    services.add(mapService(resultSet));
                }
                return services;
            }
        });
    }

    public Optional<Service> findById(int id) {
        return connectionManager.withConnection("services.findById", connection -> {
            String sql = "SELECT id, name, price, duration_min FROM services WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapService(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public Service create(String name, java.math.BigDecimal price, Integer durationMin) {
        return connectionManager.withConnection("services.create", connection -> {
            String sql = """
                    INSERT INTO services (name, price, duration_min)
                    VALUES (?, ?, ?)
                    RETURNING id, name, price, duration_min
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                statement.setBigDecimal(2, price);
                if (durationMin == null) {
                    statement.setNull(3, Types.INTEGER);
                } else {
                    statement.setInt(3, durationMin);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return mapService(resultSet);
                }
            }
        });
    }

    private Service mapService(ResultSet resultSet) throws SQLException {
        int duration = resultSet.getInt("duration_min");
        Integer nullableDuration = resultSet.wasNull() ? null : duration;
        return new Service(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getBigDecimal("price"),
                nullableDuration
        );
    }
}
