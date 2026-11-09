package org.example.sto.repository;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Order;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderServiceItem;
import org.example.sto.model.OrderSummary;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Доступ к заявкам. Операции изменения состояния выполняются через хранимые
 * функции PostgreSQL (create_order, add_service_to_order, close_order),
 * что гарантирует соблюдение бизнес-правил на стороне БД.
 */
public final class OrderRepository {

    private final ConnectionManager connectionManager;

    public OrderRepository(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<OrderSummary> findAllSummaries() {
        return connectionManager.withConnection("orders.findAllSummaries", connection -> {
            String sql = """
                    SELECT order_id, client, car, status, total_cost
                    FROM v_order_total
                    ORDER BY order_id DESC
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<OrderSummary> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new OrderSummary(
                            resultSet.getInt("order_id"),
                            resultSet.getString("client"),
                            resultSet.getString("car"),
                            resultSet.getString("status"),
                            resultSet.getBigDecimal("total_cost")
                    ));
                }
                return result;
            }
        });
    }

    public Optional<Order> findById(int orderId) {
        return connectionManager.withConnection("orders.findById", connection -> {
            String sql = """
                    SELECT o.id, o.client_id, o.car_id, o.master_id, o.status_id,
                           s.name AS status_name, o.description, o.created_at, o.closed_at
                    FROM orders o
                    JOIN order_statuses s ON s.id = o.status_id
                    WHERE o.id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, orderId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapOrder(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public List<Order> findByClientId(int clientId) {
        return connectionManager.withConnection("orders.findByClientId", connection -> {
            String sql = """
                    SELECT o.id, o.client_id, o.car_id, o.master_id, o.status_id,
                           s.name AS status_name, o.description, o.created_at, o.closed_at
                    FROM orders o
                    JOIN order_statuses s ON s.id = o.status_id
                    WHERE o.client_id = ?
                    ORDER BY o.created_at DESC
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, clientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Order> orders = new ArrayList<>();
                    while (resultSet.next()) {
                        orders.add(mapOrder(resultSet));
                    }
                    return orders;
                }
            }
        });
    }

    /**
     * Оказанные услуги заявки с зафиксированной ценой и суммой по строке.
     */
    public List<OrderServiceItem> findServices(int orderId) {
        return connectionManager.withConnection("orders.findServices", connection -> {
            String sql = """
                    SELECT osv.id, osv.service_id, s.name, osv.quantity, osv.price_at,
                           osv.price_at * osv.quantity AS line_total
                    FROM order_services osv
                    JOIN services s ON s.id = osv.service_id
                    WHERE osv.order_id = ?
                    ORDER BY osv.id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, orderId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<OrderServiceItem> result = new ArrayList<>();
                    while (resultSet.next()) {
                        result.add(new OrderServiceItem(
                                resultSet.getInt("id"),
                                resultSet.getInt("service_id"),
                                resultSet.getString("name"),
                                resultSet.getInt("quantity"),
                                resultSet.getBigDecimal("price_at"),
                                resultSet.getBigDecimal("line_total")
                        ));
                    }
                    return result;
                }
            }
        });
    }

    /**
     * Вызывает хранимую функцию create_order и возвращает id новой заявки.
     */
    public int createOrder(int clientId, int carId, Integer masterId, String description) {
        return connectionManager.withConnection("orders.createOrder", connection -> {
            String sql = "{? = call create_order(?, ?, ?, ?)}";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.INTEGER);
                statement.setInt(2, clientId);
                statement.setInt(3, carId);
                if (masterId == null) {
                    statement.setNull(4, Types.INTEGER);
                } else {
                    statement.setInt(4, masterId);
                }
                statement.setString(5, description);
                statement.execute();
                return statement.getInt(1);
            }
        });
    }

    /**
     * Вызывает add_service_to_order. Цена фиксируется на момент добавления (price_at).
     */
    public void addService(int orderId, int serviceId, int quantity) {
        connectionManager.withConnection("orders.addService", connection -> {
            String sql = "{call add_service_to_order(?, ?, ?)}";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.setInt(1, orderId);
                statement.setInt(2, serviceId);
                statement.setInt(3, quantity);
                statement.execute();
                return null;
            }
        });
    }

    /**
     * Вызывает close_order. Триггеры БД заполнят closed_at и запишут журнал статусов.
     */
    public void closeOrder(int orderId) {
        connectionManager.withConnection("orders.closeOrder", connection -> {
            String sql = "{call close_order(?)}";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.setInt(1, orderId);
                statement.execute();
                return null;
            }
        });
    }

    /**
     * Вызывает calculate_order_total.
     */
    public BigDecimal calculateTotal(int orderId) {
        return connectionManager.withConnection("orders.calculateTotal", connection -> {
            String sql = "{? = call calculate_order_total(?)}";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.NUMERIC);
                statement.setInt(2, orderId);
                statement.execute();
                return statement.getBigDecimal(1);
            }
        });
    }

    /**
     * Вызывает get_client_orders (RETURNS TABLE).
     */
    public List<ClientOrder> findClientOrders(int clientId) {
        return connectionManager.withConnection("orders.findClientOrders", connection -> {
            String sql = "SELECT order_id, car_plate, status, total_cost, created_at FROM get_client_orders(?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, clientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ClientOrder> result = new ArrayList<>();
                    while (resultSet.next()) {
                        result.add(new ClientOrder(
                                resultSet.getInt("order_id"),
                                resultSet.getString("car_plate"),
                                resultSet.getString("status"),
                                resultSet.getBigDecimal("total_cost"),
                                resultSet.getTimestamp("created_at").toLocalDateTime()
                        ));
                    }
                    return result;
                }
            }
        });
    }

    /**
     * Прямое изменение статуса. Нужно для демонстрации работы триггеров
     * trigger_set_closed_at и trigger_log_status_change.
     */
    public boolean updateStatus(int orderId, int statusId) {
        return connectionManager.withConnection("orders.updateStatus", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE orders SET status_id = ? WHERE id = ?")) {
                statement.setInt(1, statusId);
                statement.setInt(2, orderId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /**
     * Количество активных заявок по мастерам — для панели загрузки.
     */
    public Map<Integer, Integer> activeOrdersByMaster() {
        return connectionManager.withConnection("orders.activeByMaster", connection -> {
            String sql = """
                    SELECT o.master_id, COUNT(*) AS cnt
                    FROM orders o
                    JOIN order_statuses s ON s.id = o.status_id
                    WHERE o.master_id IS NOT NULL
                      AND s.name IN ('новая', 'в работе')
                    GROUP BY o.master_id
                    ORDER BY o.master_id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                Map<Integer, Integer> result = new HashMap<>();
                while (resultSet.next()) {
                    result.put(resultSet.getInt("master_id"), resultSet.getInt("cnt"));
                }
                return result;
            }
        });
    }

    public List<OrderStatus> findStatuses() {        return connectionManager.withConnection("orders.findStatuses", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, name FROM order_statuses ORDER BY id");
                 ResultSet resultSet = statement.executeQuery()) {
                List<OrderStatus> statuses = new ArrayList<>();
                while (resultSet.next()) {
                    statuses.add(new OrderStatus(resultSet.getInt("id"), resultSet.getString("name")));
                }
                return statuses;
            }
        });
    }

    /**
     * Журнал изменений статусов. Если orderId == null, возвращается весь журнал.
     */
    public List<OrderStatusLogEntry> findStatusLog(Integer orderId) {
        return connectionManager.withConnection("orders.findStatusLog", connection -> {
            String sql = """
                    SELECT id, order_id, old_status, new_status, changed_at, changed_by
                    FROM order_status_log
                    """ + (orderId == null ? "" : "WHERE order_id = ?") + " ORDER BY id DESC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (orderId != null) {
                    statement.setInt(1, orderId);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<OrderStatusLogEntry> result = new ArrayList<>();
                    while (resultSet.next()) {
                        int oldStatus = resultSet.getInt("old_status");
                        Integer oldStatusId = resultSet.wasNull() ? null : oldStatus;
                        int newStatus = resultSet.getInt("new_status");
                        Integer newStatusId = resultSet.wasNull() ? null : newStatus;
                        result.add(new OrderStatusLogEntry(
                                resultSet.getLong("id"),
                                resultSet.getInt("order_id"),
                                oldStatusId,
                                newStatusId,
                                resultSet.getTimestamp("changed_at").toLocalDateTime(),
                                resultSet.getString("changed_by")
                        ));
                    }
                    return result;
                }
            }
        });
    }

    private Order mapOrder(ResultSet resultSet) throws SQLException {
        int masterId = resultSet.getInt("master_id");
        Integer nullableMasterId = resultSet.wasNull() ? null : masterId;
        java.sql.Timestamp closedAt = resultSet.getTimestamp("closed_at");
        return new Order(
                resultSet.getInt("id"),
                resultSet.getInt("client_id"),
                resultSet.getInt("car_id"),
                nullableMasterId,
                resultSet.getInt("status_id"),
                resultSet.getString("status_name"),
                resultSet.getString("description"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                closedAt == null ? null : closedAt.toLocalDateTime()
        );
    }
}
