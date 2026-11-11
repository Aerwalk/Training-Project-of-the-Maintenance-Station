package org.example.sto.service;

import org.example.sto.exception.AccessDeniedException;
import org.example.sto.exception.EntityNotFoundException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Master;
import org.example.sto.model.Order;
import org.example.sto.model.OrderServiceItem;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Permission;
import org.example.sto.model.RolePermissions;
import org.example.sto.model.Service;
import org.example.sto.model.User;
import org.example.sto.repository.CarRepository;
import org.example.sto.repository.ClientRepository;
import org.example.sto.repository.MasterRepository;
import org.example.sto.repository.OrderRepository;
import org.example.sto.repository.ServiceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Бизнес-логика работы с заявками.
 *
 * <p>Часть правил продублирована в БД (триггеры и хранимые функции), чтобы
 * их нельзя было обойти в обход приложения. Приложение проверяет данные
 * заранее, чтобы выдавать понятные сообщения пользователю.
 */
public final class OrderService {

    private static final Set<String> CLOSED_STATUSES = Set.of("завершена", "отменена");

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final CarRepository carRepository;
    private final MasterRepository masterRepository;
    private final ServiceRepository serviceRepository;

    public OrderService(OrderRepository orderRepository,
                        ClientRepository clientRepository,
                        CarRepository carRepository,
                        MasterRepository masterRepository,
                        ServiceRepository serviceRepository) {
        this.orderRepository = orderRepository;
        this.clientRepository = clientRepository;
        this.carRepository = carRepository;
        this.masterRepository = masterRepository;
        this.serviceRepository = serviceRepository;
    }

    public List<OrderSummary> listAll() {
        return orderRepository.findAllSummaries();
    }

    /**
     * Заявки, видимые текущему пользователю: сотрудники видят все,
     * клиент — только свои.
     */
    public List<OrderSummary> listVisible(User user) {
        if (RolePermissions.of(user.role()).can(Permission.VIEW_ALL_ORDERS)) {
            return listAll();
        }
        if (user.clientId() == null) {
            throw new AccessDeniedException(
                    "Учётная запись не привязана к карточке клиента — заявки недоступны");
        }
        return orderRepository.findSummariesByClientId(user.clientId());
    }

    public Order getOrder(int orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заявка с id=" + orderId + " не найдена"));
    }

    /**
     * Возвращает заявку, только если она доступна пользователю.
     * Клиент не может открыть заявку другого клиента по номеру.
     */
    public Order getOrderVisible(User user, int orderId) {
        Order order = getOrder(orderId);
        if (!RolePermissions.of(user.role()).can(Permission.VIEW_ALL_ORDERS)
                && (user.clientId() == null || order.clientId() != user.clientId())) {
            throw new AccessDeniedException(
                    "Заявка №" + orderId + " недоступна: она принадлежит другому клиенту");
        }
        return order;
    }

    /** Создание заявки с проверкой права CREATE_ORDER. */
    public int createOrder(User user, int clientId, int carId, Integer masterId, String description) {
        require(user, Permission.CREATE_ORDER);
        return createOrder(clientId, carId, masterId, description);
    }

    public int createOrder(int clientId, int carId, Integer masterId, String description) {
        if (clientRepository.findById(clientId).isEmpty()) {
            throw new EntityNotFoundException("Клиент с id=" + clientId + " не найден");
        }
        var car = carRepository.findById(carId)
                .orElseThrow(() -> new EntityNotFoundException("Автомобиль с id=" + carId + " не найден"));
        if (car.clientId() != clientId) {
            throw new ValidationException(
                    "Автомобиль " + car.plateNum() + " принадлежит другому клиенту (id=" + car.clientId() + ")");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Описание неисправности обязательно");
        }
        if (description.trim().length() > 2000) {
            throw new ValidationException("Описание не должно превышать 2000 символов");
        }
        if (masterId != null) {
            Master master = masterRepository.findById(masterId)
                    .orElseThrow(() -> new EntityNotFoundException("Мастер с id=" + masterId + " не найден"));
            if (!master.active()) {
                throw new ValidationException("Мастер " + master.fullName() + " неактивен");
            }
        }
        return orderRepository.createOrder(clientId, carId, masterId, description.trim());
    }

    /** Добавление услуги с проверкой права ADD_ORDER_SERVICE. */
    public void addService(User user, int orderId, int serviceId, int quantity) {
        require(user, Permission.ADD_ORDER_SERVICE);
        getOrderVisible(user, orderId);
        addService(orderId, serviceId, quantity);
    }

    public void addService(int orderId, int serviceId, int quantity) {
        if (quantity <= 0 || quantity > 100) {
            throw new ValidationException("Количество должно быть от 1 до 100");
        }
        Order order = getOrder(orderId);
        if (CLOSED_STATUSES.contains(order.statusName())) {
            throw new ValidationException(
                    "Нельзя добавить услугу в заявку со статусом «" + order.statusName() + "»");
        }
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Услуга с id=" + serviceId + " не найдена"));
        orderRepository.addService(orderId, service.id(), quantity);
    }

    /** Закрытие заявки с проверкой права CLOSE_ORDER. */
    public void closeOrder(User user, int orderId) {
        require(user, Permission.CLOSE_ORDER);
        getOrderVisible(user, orderId);
        closeOrder(orderId);
    }

    public void closeOrder(int orderId) {
        Order order = getOrder(orderId);
        if ("завершена".equals(order.statusName())) {
            throw new ValidationException("Заявка уже завершена");
        }
        if ("отменена".equals(order.statusName())) {
            throw new ValidationException("Нельзя завершить отменённую заявку");
        }
        orderRepository.closeOrder(orderId);
    }

    public BigDecimal calculateTotal(int orderId) {
        getOrder(orderId);
        return orderRepository.calculateTotal(orderId);
    }

    public BigDecimal calculateTotal(User user, int orderId) {
        getOrderVisible(user, orderId);
        return orderRepository.calculateTotal(orderId);
    }

    public List<OrderServiceItem> orderServices(int orderId) {
        getOrder(orderId);
        return orderRepository.findServices(orderId);
    }

    public List<OrderServiceItem> orderServices(User user, int orderId) {
        getOrderVisible(user, orderId);
        return orderRepository.findServices(orderId);
    }

    public List<ClientOrder> clientOrders(int clientId) {
        if (clientRepository.findById(clientId).isEmpty()) {
            throw new EntityNotFoundException("Клиент с id=" + clientId + " не найден");
        }
        return orderRepository.findClientOrders(clientId);
    }

    public List<OrderStatus> statuses() {
        return orderRepository.findStatuses();
    }

    public void changeStatus(int orderId, int statusId) {
        getOrder(orderId);
        boolean exists = statuses().stream().anyMatch(status -> status.id() == statusId);
        if (!exists) {
            throw new EntityNotFoundException("Статус с id=" + statusId + " не найден");
        }
        orderRepository.updateStatus(orderId, statusId);
    }

    public List<OrderStatusLogEntry> statusLog(Integer orderId) {
        return orderRepository.findStatusLog(orderId);
    }

    /**
     * Журнал статусов с учётом прав: клиент может смотреть историю только
     * собственных заявок, а весь журнал доступен лишь сотрудникам.
     */
    public List<OrderStatusLogEntry> statusLog(User user, Integer orderId) {
        if (orderId != null) {
            getOrderVisible(user, orderId);
        } else if (!RolePermissions.of(user.role()).can(Permission.VIEW_ALL_ORDERS)) {
            throw new AccessDeniedException("Журнал доступен только сотрудникам сервиса");
        }
        return orderRepository.findStatusLog(orderId);
    }

    private static void require(User user, Permission permission) {
        if (user == null || !RolePermissions.of(user.role()).can(permission)) {
            throw new AccessDeniedException("Недостаточно прав для этой операции");
        }
    }

    public Map<Integer, Integer> masterWorkload() {
        return orderRepository.activeOrdersByMaster();
    }
}
