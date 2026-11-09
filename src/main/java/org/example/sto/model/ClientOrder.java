package org.example.sto.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Результат хранимой функции get_client_orders.
 */
public record ClientOrder(
        int orderId,
        String carPlate,
        String status,
        BigDecimal totalCost,
        LocalDateTime createdAt
) {
}
