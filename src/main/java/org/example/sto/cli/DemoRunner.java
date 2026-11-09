package org.example.sto.cli;

import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Order;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Service;
import org.example.sto.model.User;
import org.example.sto.service.AuthService;
import org.example.sto.service.CatalogService;
import org.example.sto.service.OrderService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Автоматический smoke-сценарий: логин, чтение, создание заявки,
 * добавление услуг, расчёт стоимости, закрытие и чтение журнала.
 * Используется для демонстрации и проверки работоспособности модуля.
 */
public final class DemoRunner {

    private final AuthService authService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final ConnectionManager connectionManager;

    public DemoRunner(AuthService authService,
                      CatalogService catalogService,
                      OrderService orderService,
                      ConnectionManager connectionManager) {
        this.authService = authService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.connectionManager = connectionManager;
    }

    public void run() {
        System.out.println("=== ДЕМОНСТРАЦИОННЫЙ СЦЕНАРИЙ ===");
        System.out.println("Версия PostgreSQL: " + connectionManager.ping());

        User admin = authService.login("admin", "admin123".toCharArray());
        System.out.printf("Аутентификация: %s (%s), роль %s%n",
                admin.fullName(), admin.username(), admin.role());

        List<OrderSummary> orders = orderService.listAll();
        System.out.println("\nЗаявки до начала сценария:");
        printOrders(orders);

        Client client = catalogService.findClientByPhone("+7-900-100-10-01");
        Car car = catalogService.clientCars(client.id()).get(0);
        System.out.printf("%nКлиент: %s, автомобиль: %s %s (%s)%n",
                client.fullName(), car.brand(), car.model(), car.plateNum());

        List<Service> services = catalogService.services();
        Service diagnostics = findService(services, "Компьютерная диагностика");
        Service oil = findService(services, "Замена моторного масла");

        int orderId = orderService.createOrder(
                client.id(), car.id(), null,
                "Демонстрационная заявка: диагностика и замена масла");
        System.out.println("Создана заявка №" + orderId);

        orderService.addService(orderId, diagnostics.id(), 1);
        orderService.addService(orderId, oil.id(), 1);
        BigDecimal totalBeforeClose = orderService.calculateTotal(orderId);
        System.out.println("Стоимость после добавления услуг: " + totalBeforeClose);

        orderService.closeOrder(orderId);
        Order order = orderService.getOrder(orderId);
        System.out.println("Статус заявки: " + order.statusName() + ", closed_at=" + order.closedAt());
        System.out.println("Итоговая стоимость: " + orderService.calculateTotal(orderId));

        System.out.println("\nЖурнал статусов по заявке №" + orderId + ":");
        List<OrderStatusLogEntry> log = orderService.statusLog(orderId);
        for (OrderStatusLogEntry entry : log) {
            System.out.printf("  %s: %s -> %s (%s)%n",
                    entry.changedAt(), entry.oldStatusId(), entry.newStatusId(), entry.changedBy());
        }

        System.out.println("\nЗаявки клиента " + client.fullName() + ":");
        List<ClientOrder> clientOrders = orderService.clientOrders(client.id());
        for (ClientOrder clientOrder : clientOrders) {
            System.out.printf("  №%d, %s, %s, %s, создана %s%n",
                    clientOrder.orderId(), clientOrder.carPlate(), clientOrder.status(),
                    clientOrder.totalCost(), clientOrder.createdAt());
        }

        System.out.println("\nЗаявки после сценария:");
        printOrders(orderService.listAll());
        System.out.println("=== СЦЕНАРИЙ ЗАВЕРШЁН УСПЕШНО ===");
    }

    private void printOrders(List<OrderSummary> orders) {
        System.out.printf("%-4s %-32s %-13s %-13s %12s%n", "ID", "Клиент", "Авто", "Статус", "Стоимость");
        for (OrderSummary order : orders) {
            System.out.printf("%-4d %-32s %-13s %-13s %12s%n",
                    order.orderId(), order.client(), order.car(), order.status(), order.totalCost());
        }
    }

    private Service findService(List<Service> services, String name) {
        return services.stream()
                .filter(service -> service.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В БД отсутствует услуга «" + name + "»"));
    }
}
