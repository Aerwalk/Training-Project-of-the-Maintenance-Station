package org.example.sto.model;

import java.math.BigDecimal;

/**
 * Строка представления v_order_total: заявка, клиент, автомобиль, статус, стоимость.
 */
public record OrderSummary(int orderId, String client, String car, String status, BigDecimal totalCost) {
}
