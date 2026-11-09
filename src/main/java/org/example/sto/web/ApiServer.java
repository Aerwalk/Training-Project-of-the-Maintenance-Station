package org.example.sto.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.sto.db.ConnectionManager;
import org.example.sto.exception.AuthenticationException;
import org.example.sto.exception.ConnectionUnavailableException;
import org.example.sto.exception.DataIntegrityException;
import org.example.sto.exception.DatabaseException;
import org.example.sto.exception.EntityNotFoundException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Master;
import org.example.sto.model.Order;
import org.example.sto.model.OrderServiceItem;
import org.example.sto.model.User;
import org.example.sto.service.AuthService;
import org.example.sto.service.CatalogService;
import org.example.sto.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Небольшой REST API и статический сервер для Angular-интерфейса.
 *
 * <p>Запускается режимом {@code --web}. Использует те же сервисы и репозитории,
 * что и консольное приложение, поэтому бизнес-правила и права БД остаются
 * едиными для CLI и веб-интерфейса.
 */
public final class ApiServer {

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static final String FRONTEND_DIR = "frontend/dist/sto-frontend/browser";

    private final HttpServer server;
    private final ObjectMapper mapper;
    private final AuthService authService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final ConnectionManager connectionManager;
    private final CountDownLatch stopped = new CountDownLatch(1);

    public ApiServer(int port,
                     AuthService authService,
                     CatalogService catalogService,
                     OrderService orderService,
                     ConnectionManager connectionManager) throws IOException {
        this.authService = authService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.connectionManager = connectionManager;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/api", this::handleApi);
        this.server.createContext("/", this::handleStatic);
        this.server.setExecutor(Executors.newFixedThreadPool(6));
    }

    public void start() {
        server.start();
        log.info("REST API доступен на http://localhost:{}", server.getAddress().getPort());
        log.info("Angular-интерфейс: http://localhost:{}/", server.getAddress().getPort());
    }

    public void await() throws InterruptedException {
        stopped.await();
    }

    public void stop() {
        server.stop(0);
        stopped.countDown();
    }

    // ------------------------------------------------------------------ API

    private void handleApi(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (path.equals("/api/health") && method.equals("GET")) {
                sendJson(exchange, 200, Map.of(
                        "status", "ok",
                        "database", connectionManager.ping()));
                return;
            }
            if (path.equals("/api/login") && method.equals("POST")) {
                LoginRequest request = readBody(exchange, LoginRequest.class);
                User user = authService.login(request.username(), request.password().toCharArray());
                sendJson(exchange, 200, user);
                return;
            }
            if (path.equals("/api/orders") && method.equals("GET")) {
                sendJson(exchange, 200, orderService.listAll());
                return;
            }
            if (path.equals("/api/orders") && method.equals("POST")) {
                CreateOrderRequest request = readBody(exchange, CreateOrderRequest.class);
                int id = orderService.createOrder(
                        request.clientId(), request.carId(), request.masterId(), request.description());
                sendJson(exchange, 201, Map.of("orderId", id));
                return;
            }
            if (path.equals("/api/clients") && method.equals("GET")) {
                sendJson(exchange, 200, catalogService.clients());
                return;
            }
            if (path.equals("/api/clients") && method.equals("POST")) {
                CreateClientRequest request = readBody(exchange, CreateClientRequest.class);
                Client client = catalogService.createClient(
                        request.fullName(), request.phone(), request.email());
                sendJson(exchange, 201, client);
                return;
            }
            Integer clientId = matchId(path, "/api/clients/");
            if (clientId != null && method.equals("GET") && path.equals("/api/clients/" + clientId)) {
                Client client = catalogService.clientById(clientId);
                sendJson(exchange, 200, new ClientDetail(
                        client,
                        catalogService.clientCars(clientId),
                        orderService.clientOrders(clientId)));
                return;
            }
            if (path.equals("/api/cars") && method.equals("GET")) {
                sendJson(exchange, 200, carViews());
                return;
            }
            if (path.equals("/api/cars") && method.equals("POST")) {
                CreateCarRequest request = readBody(exchange, CreateCarRequest.class);
                Car car = catalogService.createCar(
                        request.clientId(), request.brand(), request.model(),
                        request.year(), request.plateNum(), request.vin());
                sendJson(exchange, 201, new CarView(car, catalogService.clientById(car.clientId()).fullName()));
                return;
            }
            if (path.equals("/api/services") && method.equals("GET")) {
                sendJson(exchange, 200, catalogService.services());
                return;
            }
            if (path.equals("/api/services") && method.equals("POST")) {
                CreateServiceRequest request = readBody(exchange, CreateServiceRequest.class);
                org.example.sto.model.Service service = catalogService.createService(
                        request.name(), request.price(), request.durationMin());
                sendJson(exchange, 201, service);
                return;
            }
            if (path.equals("/api/masters/workload") && method.equals("GET")) {
                sendJson(exchange, 200, orderService.masterWorkload());
                return;
            }
            if (path.equals("/api/masters") && method.equals("GET")) {
                sendJson(exchange, 200, catalogService.masters());
                return;
            }
            if (path.equals("/api/statuses") && method.equals("GET")) {
                sendJson(exchange, 200, catalogService.orderStatuses());
                return;
            }
            if (path.equals("/api/log") && method.equals("GET")) {
                Integer orderId = intParam(exchange.getRequestURI(), "orderId");
                sendJson(exchange, 200, orderService.statusLog(orderId));
                return;
            }

            Integer orderId = matchId(path, "/api/orders/");
            if (orderId != null) {
                if (path.endsWith("/close") && method.equals("POST")) {
                    orderService.closeOrder(orderId);
                    sendJson(exchange, 200, Map.of("closed", true, "orderId", orderId));
                    return;
                }
                if (path.endsWith("/services") && method.equals("POST")) {
                    AddServiceRequest request = readBody(exchange, AddServiceRequest.class);
                    orderService.addService(orderId, request.serviceId(), request.quantity());
                    sendJson(exchange, 201, Map.of("added", true, "orderId", orderId));
                    return;
                }
                if (path.endsWith("/log") && method.equals("GET")) {
                    sendJson(exchange, 200, orderService.statusLog(orderId));
                    return;
                }
                if (path.equals("/api/orders/" + orderId) && method.equals("GET")) {
                    sendJson(exchange, 200, orderDetail(orderId));
                    return;
                }
            }

            sendJson(exchange, 404, new ApiError("Метод или ресурс не найден", "not_found"));
        } catch (AuthenticationException e) {
            sendJson(exchange, 401, new ApiError(e.getMessage(), "unauthorized"));
        } catch (EntityNotFoundException e) {
            sendJson(exchange, 404, new ApiError(e.getMessage(), "not_found"));
        } catch (ValidationException e) {
            sendJson(exchange, 400, new ApiError(e.getMessage(), "validation"));
        } catch (DataIntegrityException e) {
            sendJson(exchange, 409, new ApiError(e.getMessage(), sqlCode(e)));
        } catch (ConnectionUnavailableException e) {
            sendJson(exchange, 503, new ApiError(e.getMessage(), sqlCode(e)));
        } catch (DatabaseException e) {
            log.warn("Ошибка API {} {}: {}", method, path, e.getMessage());
            sendJson(exchange, 500, new ApiError(e.getMessage(), sqlCode(e)));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, new ApiError(e.getMessage(), "bad_request"));
        } catch (Exception e) {
            log.error("Непредвиденная ошибка API {} {}", method, path, e);
            sendJson(exchange, 500, new ApiError("Внутренняя ошибка сервера", "internal"));
        } finally {
            exchange.close();
        }
    }

    private List<CarView> carViews() {
        Map<Integer, String> owners = catalogService.clients().stream()
                .collect(Collectors.toMap(Client::id, Client::fullName));
        return catalogService.cars().stream()
                .map(car -> new CarView(car, owners.get(car.clientId())))
                .toList();
    }

    private OrderDetail orderDetail(int orderId) {
        Order order = orderService.getOrder(orderId);
        Client client = catalogService.clientById(order.clientId());
        Car car = catalogService.carById(order.carId());
        String masterName = order.masterId() == null ? null : catalogService.masters().stream()
                .filter(master -> master.id() == order.masterId())
                .map(Master::fullName)
                .findFirst()
                .orElse(null);
        List<OrderServiceItem> services = orderService.orderServices(orderId);
        BigDecimal total = orderService.calculateTotal(orderId);
        return new OrderDetail(order, client, car, masterName, total, services);
    }

    private static Integer matchId(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        String tail = path.substring(prefix.length());
        String idPart = tail.contains("/") ? tail.substring(0, tail.indexOf('/')) : tail;
        try {
            return Integer.valueOf(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name) && parts.length == 2 && !parts[1].isBlank()) {
                return Integer.valueOf(parts[1]);
            }
        }
        return null;
    }

    private <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length == 0) {
            throw new IllegalArgumentException("Тело запроса пустое");
        }
        return mapper.readValue(body, type);
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String sqlCode(DatabaseException e) {
        return e.getSqlState() == null ? "database" : e.getSqlState();
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    // -------------------------------------------------------------- frontend

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path base = Path.of(FRONTEND_DIR).toAbsolutePath().normalize();
        Path file = base.resolve(path.substring(1)).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            file = base.resolve("index.html");
        }
        if (!Files.isRegularFile(file)) {
            byte[] message = ("Angular-интерфейс не собран. Выполните: cd frontend && npm install && npm run build")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, message.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(message);
            }
            exchange.close();
            return;
        }
        String contentType = switch (extension(file.getFileName().toString())) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "png" -> "image/png";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, Files.size(file));
        try (OutputStream output = exchange.getResponseBody()) {
            Files.copy(file, output);
        }
        exchange.close();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    // ------------------------------------------------------------------ DTO

    public record LoginRequest(String username, String password) {
    }

    public record CreateOrderRequest(Integer clientId, Integer carId, Integer masterId, String description) {
    }

    public record CreateClientRequest(String fullName, String phone, String email) {
    }

    public record CreateCarRequest(Integer clientId, String brand, String model, Integer year,
                                   String plateNum, String vin) {
    }

    public record CreateServiceRequest(String name, BigDecimal price, Integer durationMin) {
    }

    public record AddServiceRequest(Integer serviceId, Integer quantity) {
    }

    public record CarView(int id, int clientId, String brand, String model, Integer year,
                          String plateNum, String vin, String clientName) {
        CarView(Car car, String clientName) {
            this(car.id(), car.clientId(), car.brand(), car.model(), car.year(),
                    car.plateNum(), car.vin(), clientName);
        }
    }

    public record OrderDetail(Order order, Client client, Car car, String masterName,
                              BigDecimal total, List<OrderServiceItem> services) {
    }

    public record ClientDetail(Client client, List<Car> cars, List<ClientOrder> orders) {
    }

    public record ApiError(String message, String code) {
    }
}
