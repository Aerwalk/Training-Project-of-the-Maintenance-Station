package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Car;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CarRepository {

    private final ConnectionManager connectionManager;

    public CarRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<Car> findByClientId(int clientId) {
        return connectionManager.withConnection("cars.findByClientId", connection -> {
            String sql = """
                    SELECT id, client_id, brand, model, year, plate_num, vin
                    FROM cars
                    WHERE client_id = ?
                    ORDER BY id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, clientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Car> cars = new ArrayList<>();
                    while (resultSet.next()) {
                        cars.add(mapCar(resultSet));
                    }
                    return cars;
                }
            }
        });
    }

    public List<Car> findAll() {
        return connectionManager.withConnection("cars.findAll", connection -> {
            String sql = """
                    SELECT id, client_id, brand, model, year, plate_num, vin
                    FROM cars
                    ORDER BY id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<Car> cars = new ArrayList<>();
                while (resultSet.next()) {
                    cars.add(mapCar(resultSet));
                }
                return cars;
            }
        });
    }

    public Optional<Car> findById(int id) {
        return connectionManager.withConnection("cars.findById", connection -> {
            String sql = """
                    SELECT id, client_id, brand, model, year, plate_num, vin
                    FROM cars WHERE id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapCar(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Car> findByPlate(String plateNum) {
        return connectionManager.withConnection("cars.findByPlate", connection -> {
            String sql = """
                    SELECT id, client_id, brand, model, year, plate_num, vin
                    FROM cars WHERE plate_num = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, plateNum);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapCar(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public Car create(int clientId, String brand, String model, Integer year, String plateNum, String vin) {
        return connectionManager.withConnection("cars.create", connection -> {
            String sql = """
                    INSERT INTO cars (client_id, brand, model, year, plate_num, vin)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, client_id, brand, model, year, plate_num, vin
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, clientId);
                statement.setString(2, brand);
                statement.setString(3, model);
                if (year == null) {
                    statement.setNull(4, Types.INTEGER);
                } else {
                    statement.setInt(4, year);
                }
                statement.setString(5, plateNum);
                if (vin == null || vin.isBlank()) {
                    statement.setNull(6, Types.VARCHAR);
                } else {
                    statement.setString(6, vin);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return mapCar(resultSet);
                }
            }
        });
    }

    private Car mapCar(ResultSet resultSet) throws SQLException {
        int year = resultSet.getInt("year");
        Integer nullableYear = resultSet.wasNull() ? null : year;
        return new Car(
                resultSet.getInt("id"),
                resultSet.getInt("client_id"),
                resultSet.getString("brand"),
                resultSet.getString("model"),
                nullableYear,
                resultSet.getString("plate_num"),
                resultSet.getString("vin")
        );
    }
}
