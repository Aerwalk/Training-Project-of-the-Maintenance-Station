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
import org.example.sto.model.Permission;
import org.example.sto.model.RolePermissions;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Консольный интерфейс учебного приложения СТО.
 */
public final class ConsoleMenu {

    private record MenuItem(String title, Runnable action) {
    }

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

        List<MenuItem> items = menuItems(user);
        boolean running = true;
        while (running) {
            printMenu(user.role(), items);
            int choice = readInt("Выберите пункт: ", -1);
            try {
                if (choice == 0) {
                    running = false;
                } else if (choice < 1 || choice > items.size()) {
                    out.println("Неизвестный пункт меню.");
                } else {
                    items.get(choice - 1).action().run();
                }
            } catch (DatabaseException e) {
                out.println("Ошибка БД: " + e.getMessage());
                if (e.getSqlState() != null) {
                    out.println("SQLState: " + e.getSqlState() + ", операция: " + e.getOperation());
                }
            } catch (ValidationException | EntityNotFoundException e) {
                out.println("Ошибка: " + e.getMessage());
            } catch (RuntimeException e) {
                out.println("Ошибка: " + e.getMessage());
            }
        }
        out.println("Работа завершена.");
    }

    /**
     * Меню строится по правам роли: клиент не увидит операции сотрудников,
     * мастер — управление справочниками и журнал.
     */
    private List<MenuItem> menuItems(User user) {
        RolePermissions permissions = RolePermissions.of(user.role());
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Список заявок (представление v_order_total)",
                () -> showOrders(user)));
        if (permissions.can(Permission.VIEW_CLIENTS)) {
            items.add(new MenuItem("Список клиентов", this::showClients));
        }
        if (permissions.can(Permission.VIEW_CARS)) {
            items.add(new MenuItem("Автомобили клиента", this::showClientCars));
        }
        if (permissions.can(Permission.VIEW_SERVICES)) {
            items.add(new MenuItem("Список услуг", this::showServices));
        }
        if (permissions.can(Permission.CREATE_ORDER)) {
            items.add(new MenuItem("Создать заявку (create_order)", () -> createOrder(user)));
        }
        if (permissions.can(Permission.ADD_ORDER_SERVICE)) {
            items.add(new MenuItem("Добавить услугу (add_service_to_order)", () -> addService(user)));
        }
        if (permissions.can(Permission.CLOSE_ORDER)) {
            items.add(new MenuItem("Закрыть заявку (close_order)", () -> closeOrder(user)));
        }
        items.add(new MenuItem("Рассчитать стоимость (calculate_order_total)",
                () -> showTotal(user)));
        items.add(new MenuItem("Заявки клиента (get_client_orders)",
                () -> showClientOrders(user)));
        if (permissions.can(Permission.VIEW_LOG)) {
            items.add(new MenuItem("Журнал статусов", () -> showStatusLog(user)));
        }
        items.add(new MenuItem("Проверить соединение с БД", this::ping));
        return items;
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

    private void printMenu(String role, List<MenuItem> items) {
        out.println();
        out.println("--- Главное меню (" + role + ") ---");
        for (int index = 0; index < items.size(); index++) {
            out.printf("%2d. %s%n", index + 1, items.get(index).title());
        }
        out.println(" 0. Выход");
    }

    private void showOrders(User user) {
        List<OrderSummary> orders = orderService.listVisible(user);
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

    private void addService(User user) {
        showServices();
        int orderId = readInt("ID заявки: ", -1);
        int serviceId = readInt("ID услуги: ", -1);
        int quantity = readInt("Количество (1..100): ", 1);
        orderService.addService(user, orderId, serviceId, quantity);
        out.println("Услуга добавлена в заявку №" + orderId);
    }

    private void closeOrder(User user) {
        int orderId = readInt("ID заявки для закрытия: ", -1);
        orderService.closeOrder(user, orderId);
        BigDecimal total = orderService.calculateTotal(user, orderId);
        out.println("Заявка №" + orderId + " завершена. Итоговая стоимость: " + total);
    }

    private void showTotal(User user) {
        int orderId = readInt("ID заявки: ", -1);
        out.println("Стоимость заявки №" + orderId + ": " + orderService.calculateTotal(user, orderId));
    }

    private void showClientOrders(User user) {
        RolePermissions permissions = RolePermissions.of(user.role());
        int clientId;
        if (permissions.can(Permission.VIEW_ALL_ORDERS)) {
            showClients();
            clientId = readInt("ID клиента: ", -1);
        } else {
            if (user.clientId() == null) {
                out.println("Учётная запись не привязана к карточке клиента.");
                return;
            }
            clientId = user.clientId();
        }
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

    private void showStatusLog(User user) {
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
        List<OrderStatusLogEntry> entries = orderService.statusLog(user, orderId);
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
