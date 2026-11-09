package org.example.sto.service;

import org.example.sto.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Тесты бизнес-валидации, не требующие подключения к БД:
 * проверки выполняются до обращения к репозиториям.
 */
class ValidationTest {

    private final CatalogService catalogService =
            new CatalogService(null, null, null, null, null);
    private final OrderService orderService =
            new OrderService(null, null, null, null, null);

    @Test
    void rejectsEmptyClientName() {
        assertThrows(ValidationException.class,
                () -> catalogService.createClient("  ", "+7-900-000-00-00", null));
    }

    @Test
    void rejectsInvalidPhone() {
        assertThrows(ValidationException.class,
                () -> catalogService.createClient("Иванов Иван", "телефон", null));
    }

    @Test
    void rejectsNullServicePrice() {
        assertThrows(ValidationException.class,
                () -> catalogService.createService("Услуга", null, 30));
    }

    @Test
    void rejectsNegativeServicePrice() {
        assertThrows(ValidationException.class,
                () -> catalogService.createService("Услуга", new BigDecimal("-1.00"), 30));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(ValidationException.class,
                () -> orderService.addService(1, 1, 0));
        assertThrows(ValidationException.class,
                () -> orderService.addService(1, 1, -5));
        assertThrows(ValidationException.class,
                () -> orderService.addService(1, 1, 101));
    }
}
