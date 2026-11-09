package org.example.sto.cli;

import org.example.sto.db.ConnectionManager;
import org.example.sto.exception.DatabaseException;
import org.example.sto.exception.EntityNotFoundException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Master;
import org.example.sto.model.Order;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Service;
import org.example.sto.model.User;
import org.example.sto.service.AuthService;
import org.example.sto.service.CatalogService;
import org.example.sto.service.OrderService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Консольный интерфейс учебного приложения СТО.
 */
public final class ConsoleMenu {

    private static final Set<String> STAFF_ROLES = Set.of("admin", "manager", "master");

    private final AuthService authService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final ConnectionManager connectionManager;

    private final BufferedReader reader;
    private final PrintStream out;

    public ConsoleMenu(AuthService authService,
                       CatalogService catalogService,
                       OrderService orderService,
                       ConnectionManager connectionManager,
                       PrintStream out) {
        this.authService = authService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.connectionManager = connectionManager;
        this.out = out;
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    public void run() {
        printBanner();
        User user = login();
        if (user == null) {
            return;
        }

        out.println();
        out.printf("Вы вошли как %s (%s, роль: %s)%n", user.fullName(), user.username(), user.role());

        boolean staff = STAFF_ROLES.contains(user.role());
        boolean running = true;
        while (running) {
            printMenu(staff, user.role());
            int choice = readInt("Выберите пункт: ", -1);
            try {
                switch (choice) {
                    case 1 -> showOrders();
                    case 2 -> { if (staff) showClients(); else unknown(); }
                    case 3 -> { if (staff) showClientCars(); else unknown(); }
                    case 4 -> showServices();
                    case 5 -> { if (staff) createOrder(user); else unknown(); }
                    case 6 -> { if (staff) addService(); else unknown(); }
                    case 7 -> { if (staff) closeOrder(); else unknown(); }
                    case 8 -> showTotal();
                    case 9 -> showClientOrders();
                    case 10 -> { if (staff) showStatusLog(); else unknown(); }
                    case 11 -> ping();
                    case 0 -> running = false;
                    default -> out.println("Неизвестный пункт меню.");
                }
            } catch (DatabaseException e) {
                out.println("Ошибка БД: " + e.getMessage());
                if (e.getSqlState() != null) {
                    out.println("SQLState: " + e.getSqlState() + ", операция: " + e.getOperation());
                }
            } catch (ValidationException | EntityNotFoundException e) {
                out.println("Ошибка: " + e.getMessage());
            }
        }
        out.println("Работа завершена.");
    }

    private User login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            out.print("Логин: ");
            out.flush();
            String username;
            try {
                username = reader.readLine();
            } catch (Exception e) {
                return null;
            }
            if (username == null) {
                return null;
            }
            char[] password = readPassword();
            try {
                return authService.login(username, password);
            } catch (RuntimeException e) {
                out.println("Не удалось войти: " + e.getMessage());
                out.println("Попытка " + attempt + " из 3.");
            }
        }
        out.println("Превышено количество попыток входа.");
        return null;
    }

    private char[] readPassword() {
        // Приглашение печатаем через PrintStream с UTF-8: readPassword(prompt)
        // пишет напрямую в консоль в кодировке ОС, из-за чего кириллица ломается.
        out.print("Пароль: ");
        out.flush();
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword();
            return password == null ? new char[0] : password;
        }
        try {
            String line = reader.readLine();
            return line == null ? new char[0] : line.toCharArray();
        } catch (Exception e) {
            return new char[0];
        }
    }

    private void printBanner() {
        out.println("==============================================================");
        out.println("  Модуль доступа к БД СТО (PostgreSQL + JDBC + HikariCP)");
        out.println("  Учебная практика УП.01.02, ПМ.01");
        out.println("==============================================================");
    }

    private void printMenu(boolean staff, String role) {
        out.println();
        out.println("--- Главное меню (" + role + ") ---");
        out.println(" 1. Список заявок (представление v_order_total)");
        if (staff) {
            out.println(" 2. Список клиентов");
            out.println(" 3. Автомобили клиента");
        }
        out.println(" 4. Список услуг");
        if (staff) {
            out.println(" 5. Создать заявку (create_order)");
            out.println(" 6. Добавить услугу (add_service_to_order)");
            out.println(" 7. Закрыть заявку (close_order)");
        }
        out.println(" 8. Рассчитать стоимость (calculate_order_total)");
        out.println(" 9. Заявки клиента (get_client_orders)");
        if (staff) {
            out.println("10. Журнал статусов");
        }
        out.println("11. Проверить соединение с БД");
        out.println(" 0. Выход");
    }

    private void showOrders() {
        List<OrderSummary> orders = orderService.listAll();
        out.println();
        out.printf("%-4s %-32s %-13s %-13s %12s%n", "ID", "Клиент", "Авто", "Статус", "Стоимость");
        out.println("-".repeat(80));
        for (OrderSummary order : orders) {
            out.printf("%-4d %-32s %-13s %-13s %12s%n",
                    order.orderId(), order.client(), order.car(), order.status(), order.totalCost());
        }
        if (orders.isEmpty()) {
            out.println("Заявок нет.");
        }
    }

    private void showClients() {
        List<Client> clients = catalogService.clients();
        out.println();
        out.printf("%-4s %-32s %-18s %-24s%n", "ID", "ФИО", "Телефон", "E-mail");
        out.println("-".repeat(82));
        for (Client client : clients) {
            out.printf("%-4d %-32s %-18s %-24s%n",
                    client.id(), client.fullName(), client.phone(),
                    client.email() == null ? "—" : client.email());
        }
    }

    private void showClientCars() {
        showClients();
        int clientId = readInt("ID клиента: ", -1);
        List<Car> cars = catalogService.clientCars(clientId);
        out.println();
        out.printf("%-4s %-12s %-12s %-6s %-12s %-18s%n", "ID", "Марка", "Модель", "Год", "Номер", "VIN");
        out.println("-".repeat(70));
        for (Car car : cars) {
            out.printf("%-4d %-12s %-12s %-6s %-12s %-18s%n",
                    car.id(), car.brand(), car.model(),
                    car.year() == null ? "—" : car.year().toString(),
                    car.plateNum(), car.vin() == null ? "—" : car.vin());
        }
        if (cars.isEmpty()) {
            out.println("У клиента нет автомобилей.");
        }
    }

    private void showServices() {
        List<Service> services = catalogService.services();
        out.println();
        out.printf("%-4s %-34s %10s %8s%n", "ID", "Наименование", "Цена", "Мин.");
        out.println("-".repeat(62));
        for (Service service : services) {
            out.printf("%-4d %-34s %10s %8s%n",
                    service.id(), service.name(), service.price(),
                    service.durationMin() == null ? "—" : service.durationMin());
        }
    }

    private void createOrder(User user) {
        showClients();
        int clientId = readInt("ID клиента: ", -1);
        List<Car> cars = catalogService.clientCars(clientId);
        if (cars.isEmpty()) {
            out.println("У клиента нет автомобилей. Сначала добавьте автомобиль.");
            return;
        }
        out.printf("%-4s %-12s %-12s %-12s%n", "ID", "Марка", "Модель", "Номер");
        for (Car car : cars) {
            out.printf("%-4d %-12s %-12s %-12s%n", car.id(), car.brand(), car.model(), car.plateNum());
        }
        int carId = readInt("ID автомобиля: ", -1);

        List<Master> masters = catalogService.masters();
        out.println("Мастера:");
        for (Master master : masters) {
            out.printf("  %d — %s (%s)%n", master.id(), master.fullName(),
                    master.active() ? "активен" : "неактивен");
        }
        int masterId = readInt("ID мастера (0 — без мастера): ", 0);

        out.print("Описание неисправности: ");
        out.flush();
        String description = readLine();

        int orderId = orderService.createOrder(clientId, carId, masterId == 0 ? null : masterId, description);
        out.println("Создана заявка №" + orderId + " (пользователь: " + user.username() + ")");
    }

    private void addService() {
        showServices();
        int orderId = readInt("ID заявки: ", -1);
        int serviceId = readInt("ID услуги: ", -1);
        int quantity = readInt("Количество (1..100): ", 1);
        orderService.addService(orderId, serviceId, quantity);
        out.println("Услуга добавлена в заявку №" + orderId);
    }

    private void closeOrder() {
        int orderId = readInt("ID заявки для закрытия: ", -1);
        orderService.closeOrder(orderId);
        BigDecimal total = orderService.calculateTotal(orderId);
        out.println("Заявка №" + orderId + " завершена. Итоговая стоимость: " + total);
    }

    private void showTotal() {
        int orderId = readInt("ID заявки: ", -1);
        out.println("Стоимость заявки №" + orderId + ": " + orderService.calculateTotal(orderId));
    }

    private void showClientOrders() {
        int clientId = readInt("ID клиента: ", -1);
        List<ClientOrder> orders = orderService.clientOrders(clientId);
        out.println();
        out.printf("%-4s %-14s %-14s %12s %-20s%n", "ID", "Авто", "Статус", "Стоимость", "Создана");
        out.println("-".repeat(72));
        for (ClientOrder order : orders) {
            out.printf("%-4d %-14s %-14s %12s %-20s%n",
                    order.orderId(), order.carPlate(), order.status(), order.totalCost(), order.createdAt());
        }
        if (orders.isEmpty()) {
            out.println("У клиента нет заявок.");
        }
    }

    private void showStatusLog() {
        out.print("ID заявки (Enter — весь журнал): ");
        out.flush();
        String value = readLine();
        Integer orderId = null;
        if (value != null && !value.isBlank()) {
            try {
                orderId = Integer.valueOf(value.trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("ID заявки должен быть целым числом");
            }
        }
        List<OrderStatusLogEntry> entries = orderService.statusLog(orderId);
        out.println();
        out.printf("%-4s %-6s %-8s %-8s %-20s %-20s%n", "ID", "Заявка", "Было", "Стало", "Когда", "Кто");
        out.println("-".repeat(76));
        for (OrderStatusLogEntry entry : entries) {
            out.printf("%-4d %-6d %-8s %-8s %-20s %-20s%n",
                    entry.id(), entry.orderId(),
                    entry.oldStatusId() == null ? "—" : entry.oldStatusId(),
                    entry.newStatusId() == null ? "—" : entry.newStatusId(),
                    entry.changedAt(), entry.changedBy());
        }
    }

    private void ping() {
        String version = connectionManager.ping();
        out.println("Соединение с БД установлено. Версия сервера: " + version);
    }

    private void unknown() {
        out.println("Недостаточно прав для этой операции.");
    }

    private int readInt(String prompt, int defaultValue) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line = readLine();
            if (line == null || line.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("Введите целое число.");
            }
        }
    }

    private String readLine() {
        try {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}
