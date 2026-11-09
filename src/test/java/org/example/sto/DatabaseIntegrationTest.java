package org.example.sto;

import org.example.sto.config.AppConfig;
import org.example.sto.config.DbSettings;
import org.example.sto.db.ConnectionManager;
import org.example.sto.db.RetryPolicy;
import org.example.sto.exception.AuthenticationException;
import org.example.sto.exception.DataIntegrityException;
import org.example.sto.exception.ForeignKeyViolationException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Order;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Service;
import org.example.sto.model.User;
import org.example.sto.repository.CarRepository;
import org.example.sto.repository.ClientRepository;
import org.example.sto.repository.MasterRepository;
import org.example.sto.repository.OrderRepository;
import org.example.sto.repository.ServiceRepository;
import org.example.sto.repository.UserRepository;
import org.example.sto.service.AuthService;
import org.example.sto.service.CatalogService;
import org.example.sto.service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты связки «Java-приложение — PostgreSQL».
 *
 * <p>Перед запуском должен быть поднят стенд:
 * {@code docker compose up -d}. Если БД недоступна, тесты помечаются
 * как пропущенные (Assumption), а не падают.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {

    private static ConnectionManager connectionManager;
    private static UserRepository userRepository;
    private static ClientRepository clientRepository;
    private static CarRepository carRepository;
    private static ServiceRepository serviceRepository;
    private static OrderRepository orderRepository;
    private static AuthService authService;
    private static CatalogService catalogService;
    private static OrderService orderService;

    @BeforeAll
    void connect() {
        DbSettings settings = AppConfig.load().dbSettings();
        ConnectionManager manager = new ConnectionManager(
                settings, new RetryPolicy(1, 0, 1.0, 0));
        try {
            manager.ping();
        } catch (RuntimeException e) {
            manager.close();
            Assumptions.assumeTrue(false,
                    "PostgreSQL недоступен (" + settings.url() + "). Запустите docker compose up -d. " + e.getMessage());
            return;
        }

        connectionManager = manager;
        userRepository = new UserRepository(manager);
        clientRepository = new ClientRepository(manager);
        carRepository = new CarRepository(manager);
        MasterRepository masterRepository = new MasterRepository(manager);
        serviceRepository = new ServiceRepository(manager);
        orderRepository = new OrderRepository(manager);

        authService = new AuthService(userRepository);
        catalogService = new CatalogService(
                clientRepository, carRepository, serviceRepository, masterRepository, orderRepository);
        orderService = new OrderService(
                orderRepository, clientRepository, carRepository, masterRepository, serviceRepository);
    }

    @AfterAll
    void disconnect() {
        if (connectionManager != null) {
            connectionManager.close();
        }
    }

    @Test
    void authenticatesSeededAdmin() {
        User user = authService.login("admin", "admin123".toCharArray());

        assertEquals("admin", user.username());
        assertEquals("admin", user.role());
        assertTrue(user.fullName().contains("Поляков"));

        // Логин не зависит от регистра: удобно при вводе с клавиатуры.
        User upperCaseLogin = authService.login("Admin", "admin123".toCharArray());
        assertEquals("admin", upperCaseLogin.username());
    }

    @Test
    void rejectsWrongPassword() {
        assertThrows(AuthenticationException.class,
                () -> authService.login("admin", "wrong-password".toCharArray()));
    }

    @Test
    void sqlInjectionInLoginDoesNotBypassAuthentication() {
        assertThrows(AuthenticationException.class,
                () -> authService.login("' OR '1'='1", "anything".toCharArray()));
        assertThrows(AuthenticationException.class,
                () -> authService.login("admin' --", "anything".toCharArray()));
        assertThrows(AuthenticationException.class,
                () -> authService.login("admin", "' OR '1'='1".toCharArray()));
    }

    @Test
    void readsOrdersFromView() {
        List<OrderSummary> orders = orderService.listAll();

        assertFalse(orders.isEmpty());
        assertTrue(orders.stream().anyMatch(order -> order.status().equals("в работе")));
        assertTrue(orders.stream().allMatch(order -> order.totalCost() != null));
    }

    @Test
    void viewTotalEqualsStoredFunctionResult() {
        OrderSummary summary = orderService.listAll().stream()
                .filter(order -> order.totalCost().signum() > 0)
                .findFirst()
                .orElseThrow();

        assertEquals(0, summary.totalCost().compareTo(orderService.calculateTotal(summary.orderId())));
    }

    @Test
    void returnsClientOrdersThroughStoredFunction() {
        Client client = catalogService.findClientByPhone("+7-900-100-10-01");
        List<ClientOrder> orders = orderService.clientOrders(client.id());

        assertFalse(orders.isEmpty());
        assertTrue(orders.stream().allMatch(order -> order.carPlate() != null && !order.carPlate().isBlank()));
    }

    @Test
    void createsAddsServiceAndClosesOrder() throws SQLException {
        Client client = catalogService.findClientByPhone("+7-900-100-10-01");
        Car car = catalogService.clientCars(client.id()).get(0);
        Service service = catalogService.services().get(0);

        int orderId = orderService.createOrder(
                client.id(), car.id(), null, "Интеграционный тест: создание и закрытие заявки");
        try {
            orderService.addService(orderId, service.id(), 2);

            BigDecimal expected = service.price().multiply(BigDecimal.valueOf(2));
            assertEquals(0, expected.compareTo(orderService.calculateTotal(orderId)));

            orderService.closeOrder(orderId);
            Order closed = orderService.getOrder(orderId);
            assertEquals("завершена", closed.statusName());
            assertNotNull(closed.closedAt());

            assertTrue(orderService.statusLog(orderId).stream()
                    .anyMatch(entry -> entry.newStatusId() == 3));
        } finally {
            cleanupOrder(orderId);
        }
    }

    @Test
    void preventsAddingServiceToClosedOrder() {
        Order closedOrder = orderService.listAll().stream()
                .filter(order -> order.status().equals("завершена"))
                .findFirst()
                .map(summary -> orderService.getOrder(summary.orderId()))
                .orElseThrow();

        Service service = catalogService.services().get(0);
        assertThrows(ValidationException.class,
                () -> orderService.addService(closedOrder.id(), service.id(), 1));
    }

    @Test
    void createsClientAndCar() {
        String phone = "+7-999-000-00-88";
        deleteClientByPhone(phone);
        Client client = catalogService.createClient("Тестовый Владелец", phone, "owner@example.ru");
        try {
            Car car = catalogService.createCar(
                    client.id(), "Test", "Model", 2020, "Т888ТТ88", "TESTVIN1234567890");
            assertEquals(client.id(), car.clientId());
            assertEquals("Т888ТТ88", car.plateNum());
            assertFalse(catalogService.clientCars(client.id()).isEmpty());
        } finally {
            clientRepository.delete(client.id());
        }
    }

    @Test
    void createsOrderWithoutMaster() throws SQLException {
        Client client = catalogService.findClientByPhone("+7-900-100-10-01");
        Car car = catalogService.clientCars(client.id()).get(0);

        int orderId = orderService.createOrder(
                client.id(), car.id(), null, "Заявка без назначенного мастера");
        try {
            Order order = orderService.getOrder(orderId);
            assertEquals("новая", order.statusName());
            assertEquals(null, order.masterId());
        } finally {
            cleanupOrder(orderId);
        }
    }

    @Test
    void transactionRollsBackOnRuntimeException() {
        String phone = "+7-999-000-00-99";
        assertThrows(IllegalStateException.class, () ->
                connectionManager.inTransaction("test.rollback", connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO clients (full_name, phone) VALUES (?, ?)")) {
                        statement.setString(1, "Откат");
                        statement.setString(2, phone);
                        statement.executeUpdate();
                    }
                    throw new IllegalStateException("Искусственная ошибка после INSERT");
                }));

        assertTrue(clientRepository.findByPhone(phone).isEmpty());
    }

    @Test
    void databaseRejectsTooLongVarchar() {
        DataIntegrityException exception = assertThrows(DataIntegrityException.class, () ->
                connectionManager.withConnection("test.varchar", connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO clients (full_name, phone) VALUES ('Длина', ?)")) {
                        statement.setString(1, "1".repeat(21));
                        return statement.executeUpdate();
                    }
                }));

        assertEquals("22001", exception.getSqlState());
    }

    @Test
    void databaseRejectsInvalidYearCheckConstraint() {
        DataIntegrityException exception = assertThrows(DataIntegrityException.class, () ->
                carRepository.create(1, "Test", "Year", 1800, "У777УУ77", null));

        assertEquals("23514", exception.getSqlState());
    }

    @Test
    void rejectsDuplicateClientPhone() {
        String phone = "+7-999-000-00-77";
        deleteClientByPhone(phone);
        Client first = catalogService.createClient("Тестовый Клиент", phone, "test@example.ru");
        try {
            assertThrows(ValidationException.class,
                    () -> catalogService.createClient("Другой Клиент", phone, "other@example.ru"));
        } finally {
            clientRepository.delete(first.id());
        }
    }

    @Test
    void databaseRejectsForeignKeyViolation() {
        assertThrows(ForeignKeyViolationException.class,
                () -> carRepository.create(999_999, "Test", "FK", 2020, "Т999ТТ99", null));
    }

    @Test
    void databaseRejectsNullInNotNullColumn() {
        DataIntegrityException exception = assertThrows(DataIntegrityException.class,
                () -> connectionManager.withConnection("test.notNull", connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO clients (full_name, phone) VALUES ('Без телефона', NULL)")) {
                        return statement.executeUpdate();
                    }
                }));

        assertEquals("23502", exception.getSqlState());
    }

    private void cleanupOrder(int orderId) throws SQLException {
        connectionManager.inTransaction("test.cleanupOrder", connection -> {
            try (PreparedStatement services = connection.prepareStatement(
                    "DELETE FROM order_services WHERE order_id = ?");
                 PreparedStatement log = connection.prepareStatement(
                         "DELETE FROM order_status_log WHERE order_id = ?");
                 PreparedStatement order = connection.prepareStatement(
                         "DELETE FROM orders WHERE id = ?")) {
                services.setInt(1, orderId);
                services.executeUpdate();
                log.setInt(1, orderId);
                log.executeUpdate();
                order.setInt(1, orderId);
                order.executeUpdate();
            }
            return null;
        });
    }

    private void deleteClientByPhone(String phone) {
        connectionManager.withConnection("test.cleanupClient", connection -> {
            try (PreparedStatement services = connection.prepareStatement(
                    "DELETE FROM order_services WHERE order_id IN (SELECT id FROM orders WHERE client_id IN "
                            + "(SELECT id FROM clients WHERE phone = ?))");
                 PreparedStatement log = connection.prepareStatement(
                         "DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM orders WHERE client_id IN "
                                 + "(SELECT id FROM clients WHERE phone = ?))");
                 PreparedStatement orders = connection.prepareStatement(
                         "DELETE FROM orders WHERE client_id IN (SELECT id FROM clients WHERE phone = ?)");
                 PreparedStatement cars = connection.prepareStatement(
                         "DELETE FROM cars WHERE client_id IN (SELECT id FROM clients WHERE phone = ?)");
                 PreparedStatement clients = connection.prepareStatement(
                         "DELETE FROM clients WHERE phone = ?")) {
                for (PreparedStatement statement : List.of(services, log, orders, cars, clients)) {
                    statement.setString(1, phone);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }
}
