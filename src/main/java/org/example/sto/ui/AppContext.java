package org.example.sto.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.example.sto.config.DbSettings;
import org.example.sto.db.ConnectionManager;
import org.example.sto.model.Permission;
import org.example.sto.model.RolePermissions;
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
import org.example.sto.ui.components.ToastHost;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Общие зависимости JavaFX-приложения: сервисы, фоновый исполнитель,
 * текущий пользователь и уведомления.
 */
public final class AppContext {

    private final ConnectionManager connectionManager;
    private final AuthService authService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final ExecutorService worker;
    private ToastHost toastHost;

    private Stage stage;
    private User user;
    private Runnable logoutHandler;

    private AppContext(ConnectionManager connectionManager,
                       AuthService authService,
                       CatalogService catalogService,
                       OrderService orderService) {
        this.connectionManager = connectionManager;
        this.authService = authService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "sto-ui-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = Executors.newFixedThreadPool(3, factory);
    }

    public static AppContext create(ConnectionManager connectionManager) {
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
        return new AppContext(connectionManager, authService, catalogService, orderService);
    }

    public static AppContext create(ConnectionManager connectionManager,
                                    AuthService authService,
                                    CatalogService catalogService,
                                    OrderService orderService) {
        return new AppContext(connectionManager, authService, catalogService, orderService);
    }

    public AuthService auth() {
        return authService;
    }

    public CatalogService catalog() {
        return catalogService;
    }

    public OrderService orders() {
        return orderService;
    }

    public DbSettings dbSettings() {
        return connectionManager.settings();
    }

    public String ping() {
        return connectionManager.ping();
    }

    public ToastHost toast() {
        if (toastHost == null) {
            toastHost = new ToastHost();
        }
        return toastHost;
    }

    public void clearToast() {
        toastHost = null;
    }

    public Stage stage() {
        return stage;
    }

    /** Корневой StackPane окна — используется для модальных окон и тостов. */
    public javafx.scene.layout.StackPane overlay() {
        if (stage == null || stage.getScene() == null
                || !(stage.getScene().getRoot() instanceof javafx.scene.layout.StackPane root)) {
            throw new IllegalStateException("Окно приложения ещё не готово");
        }
        return root;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public User user() {
        return user;
    }

    /** Права текущего пользователя. Для гостя набор прав пуст. */
    public RolePermissions permissions() {
        return RolePermissions.of(user == null ? null : user.role());
    }

    public boolean can(Permission permission) {
        return permissions().can(permission);
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setLogoutHandler(Runnable logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    public void logout() {
        user = null;
        if (logoutHandler != null) {
            logoutHandler.run();
        }
    }

    /**
     * Выполняет операцию с БД в фоне, результат передаёт в FX-поток.
     */
    public <T> void async(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        worker.submit(() -> {
            try {
                T result = task.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable error) {
                Throwable cause = unwrap(error);
                if (!worker.isShutdown()) {
                    Platform.runLater(() -> onError.accept(cause));
                }
            }
        });
    }

    public void shutdown() {
        worker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public static String errorText(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.ExecutionException
                || cause instanceof java.util.concurrent.CompletionException)) {
            cause = cause.getCause();
        }
        return cause;
    }
}
