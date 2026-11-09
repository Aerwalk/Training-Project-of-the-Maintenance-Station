package org.example.sto.service;

import org.example.sto.exception.DuplicateKeyException;
import org.example.sto.exception.EntityNotFoundException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.Master;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.Service;
import org.example.sto.repository.CarRepository;
import org.example.sto.repository.ClientRepository;
import org.example.sto.repository.MasterRepository;
import org.example.sto.repository.OrderRepository;
import org.example.sto.repository.ServiceRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Бизнес-операции со справочниками: клиенты, автомобили, услуги, мастера.
 * Здесь выполняется валидация входных данных до обращения к БД.
 */
public final class CatalogService {

    private final ClientRepository clientRepository;
    private final CarRepository carRepository;
    private final ServiceRepository serviceRepository;
    private final MasterRepository masterRepository;
    private final OrderRepository orderRepository;

    public CatalogService(ClientRepository clientRepository,
                          CarRepository carRepository,
                          ServiceRepository serviceRepository,
                          MasterRepository masterRepository,
                          OrderRepository orderRepository) {
        this.clientRepository = clientRepository;
        this.carRepository = carRepository;
        this.serviceRepository = serviceRepository;
        this.masterRepository = masterRepository;
        this.orderRepository = orderRepository;
    }

    public List<Client> clients() {
        return clientRepository.findAll();
    }

    public List<Car> cars() {
        return carRepository.findAll();
    }

    public List<Car> clientCars(int clientId) {
        return carRepository.findByClientId(clientId);
    }

    public List<Service> services() {
        return serviceRepository.findAll();
    }

    public List<Master> masters() {
        return masterRepository.findAll();
    }

    public List<OrderStatus> orderStatuses() {
        return orderRepository.findStatuses();
    }

    public Client clientById(int id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с id=" + id + " не найден"));
    }

    public Car carById(int id) {
        return carRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Автомобиль с id=" + id + " не найден"));
    }

    public Service serviceById(int id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Услуга с id=" + id + " не найдена"));
    }

    public Client createClient(String fullName, String phone, String email) {
        requireText(fullName, "ФИО клиента", 150);
        requireText(phone, "Телефон", 20);
        if (!phone.matches("\\+?[0-9()\\-\\s]{5,20}")) {
            throw new ValidationException("Телефон содержит недопустимые символы");
        }
        if (email != null && !email.isBlank()) {
            requireText(email, "E-mail", 100);
            if (!email.contains("@")) {
                throw new ValidationException("E-mail должен содержать символ @");
            }
        }
        try {
            return clientRepository.create(fullName.trim(), phone.trim(), normalize(email));
        } catch (DuplicateKeyException e) {
            throw new ValidationException("Клиент с телефоном " + phone + " уже существует", e);
        }
    }

    public Car createCar(int clientId, String brand, String model, Integer year, String plateNum, String vin) {
        clientById(clientId);
        requireText(brand, "Марка", 50);
        requireText(model, "Модель", 50);
        requireText(plateNum, "Гос. номер", 15);
        if (year != null && (year < 1950 || year > 2100)) {
            throw new ValidationException("Год выпуска должен быть в диапазоне 1950..2100");
        }
        if (vin != null && !vin.isBlank() && vin.trim().length() > 17) {
            throw new ValidationException("VIN не может быть длиннее 17 символов");
        }
        try {
            return carRepository.create(clientId, brand.trim(), model.trim(), year, plateNum.trim(), normalize(vin));
        } catch (DuplicateKeyException e) {
            throw new ValidationException("Автомобиль с номером " + plateNum + " уже существует", e);
        }
    }

    public Service createService(String name, BigDecimal price, Integer durationMin) {
        requireText(name, "Наименование услуги", 150);
        if (price == null || price.signum() < 0) {
            throw new ValidationException("Стоимость услуги не может быть отрицательной");
        }
        if (durationMin != null && durationMin <= 0) {
            throw new ValidationException("Длительность услуги должна быть больше нуля");
        }
        return serviceRepository.create(name.trim(), price, durationMin);
    }

    public Client findClientByPhone(String phone) {
        return clientRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с телефоном " + phone + " не найден"));
    }

    public Car findCarByPlate(String plate) {
        return carRepository.findByPlate(plate)
                .orElseThrow(() -> new EntityNotFoundException("Автомобиль с номером " + plate + " не найден"));
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Поле «" + field + "» обязательно");
        }
        if (value.trim().length() > maxLength) {
            throw new ValidationException("Поле «" + field + "» длиннее " + maxLength + " символов");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
