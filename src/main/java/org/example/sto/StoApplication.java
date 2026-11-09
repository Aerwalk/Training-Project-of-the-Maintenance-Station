package org.example.sto;

import org.example.sto.cli.ConsoleMenu;
import org.example.sto.cli.DemoRunner;
import org.example.sto.config.AppConfig;
import org.example.sto.config.DbSettings;
import org.example.sto.db.ConnectionManager;
import org.example.sto.exception.DatabaseException;
import org.example.sto.model.OrderSummary;
import org.example.sto.repository.CarRepository;
import org.example.sto.repository.ClientRepository;
import org.example.sto.repository.MasterRepository;
import org.example.sto.repository.OrderRepository;
import org.example.sto.repository.ServiceRepository;
import org.example.sto.repository.UserRepository;
import org.example.sto.service.AuthService;
import org.example.sto.service.CatalogService;
import org.example.sto.service.OrderService;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Точка входа учебного приложения «СТО».
 *
 * <p>Режимы:
 * <pre>
 *   java -jar sto-db-module.jar              интерактивное меню
 *   java -jar sto-db-module.jar --demo       автоматический smoke-сценарий
 *   java -jar sto-db-module.jar --check      проверка соединения и версии PostgreSQL
 *   java -jar sto-db-module.jar --list-orders список заявок без входа
 *   java -jar sto-db-module.jar --help       справка
 * </pre>
 */
public final class StoApplication {

    private StoApplication() {
    }

    public static void main(String[] args) {
        configureUtf8Console();

        boolean demo = hasArg(args, "--demo");
        boolean check = hasArg(args, "--check");
        boolean listOrders = hasArg(args, "--list-orders");
        boolean help = hasArg(args, "--help") || hasArg(args, "-h");

        if (help) {
            printHelp();
            return;
        }

        DbSettings settings;
        try {
            settings = AppConfig.load().dbSettings();
        } catch (RuntimeException e) {
            System.err.println("Ошибка конфигурации: " + e.getMessage());
            System.exit(2);
            return;
        }

        try (ConnectionManager connectionManager = new ConnectionManager(settings)) {
            UserRepository userRepository = new UserRepository(connectionManager);
            ClientRepository clientRepository = new ClientRepository(connectionManager);
            CarRepository carRepository = new CarRepository(connectionManager);
            MasterRepository masterRepository = new MasterRepository(connectionManager);
            ServiceRepository serviceRepository = new ServiceRepository(connectionManager);
            OrderRepository orderRepository = new OrderRepository(connectionManager);

            AuthService authService = new AuthService(userRepository);
            CatalogService catalogService = new CatalogService(
                    clientRepository, carRepository, serviceRepository, masterRepository, orderRepository);
            OrderService orderService = new OrderService(
                    orderRepository, clientRepository, carRepository, masterRepository, serviceRepository);

            if (check) {
                String version = connectionManager.ping();
                System.out.println("Подключение успешно. " + settings.toSafeString());
                System.out.println("PostgreSQL: " + version);
                return;
            }

            if (listOrders) {
                printOrders(orderService.listAll());
                return;
            }

            if (demo) {
                new DemoRunner(authService, catalogService, orderService, connectionManager).run();
                return;
            }

            new ConsoleMenu(authService, catalogService, orderService, connectionManager, System.out).run();
        } catch (DatabaseException e) {
            System.err.println("Ошибка БД: " + e.getMessage());
            if (e.getSqlState() != null) {
                System.err.println("SQLState: " + e.getSqlState());
            }
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("Непредвиденная ошибка: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static boolean hasArg(String[] args, String expected) {
        return Arrays.asList(args).contains(expected);
    }

    /**
     * Единая кодировка вывода, чтобы кириллица корректно отображалась
     * в консоли Windows, Git Bash и IDE.
     */
    private static void configureUtf8Console() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    private static void printOrders(List<OrderSummary> orders) {
        System.out.printf("%-4s %-32s %-13s %-13s %12s%n", "ID", "Клиент", "Авто", "Статус", "Стоимость");
        for (OrderSummary order : orders) {
            System.out.printf("%-4d %-32s %-13s %-13s %12s%n",
                    order.orderId(), order.client(), order.car(), order.status(), order.totalCost());
        }
    }

    private static void printHelp() {
        System.out.println("""
                Модуль доступа к БД СТО (PostgreSQL).
                Параметры подключения: src/main/resources/application.properties
                или -Dconfig.file=<путь>, переменные STO_DB_URL / STO_DB_USERNAME / STO_DB_PASSWORD.

                Режимы:
                  --demo          автоматический сквозной сценарий
                  --check         проверить соединение и версию PostgreSQL
                  --list-orders   вывести заявки без аутентификации
                  --help, -h      эта справка

                Тестовые учётные записи:
                  admin/admin123, manager/manager123, master/master123, client/client123
                """);
    }
}
