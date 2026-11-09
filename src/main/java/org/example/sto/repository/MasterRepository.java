package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Master;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MasterRepository {

    private final ConnectionManager connectionManager;

    public MasterRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<Master> findAll() {
        return connectionManager.withConnection("masters.findAll", connection -> {
            String sql = """
                    SELECT m.id, m.user_id, u.full_name, m.specialization, m.is_active
                    FROM masters m
                    JOIN users u ON u.id = m.user_id
                    ORDER BY m.id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<Master> masters = new ArrayList<>();
                while (resultSet.next()) {
                    masters.add(mapMaster(resultSet));
                }
                return masters;
            }
        });
    }

    public Optional<Master> findById(int id) {
        return connectionManager.withConnection("masters.findById", connection -> {
            String sql = """
                    SELECT m.id, m.user_id, u.full_name, m.specialization, m.is_active
                    FROM masters m
                    JOIN users u ON u.id = m.user_id
                    WHERE m.id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapMaster(resultSet)) : Optional.empty();
                }
            }
        });
    }

    private Master mapMaster(ResultSet resultSet) throws SQLException {
        return new Master(
                resultSet.getInt("id"),
                resultSet.getInt("user_id"),
                resultSet.getString("full_name"),
                resultSet.getString("specialization"),
                resultSet.getBoolean("is_active")
        );
    }
}
