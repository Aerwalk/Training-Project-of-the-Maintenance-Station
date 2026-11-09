package org.example.sto.model;

import java.math.BigDecimal;

/**
 * Оказанная услуга в заявке. Цена фиксируется на момент добавления
 * (order_services.price_at), поэтому изменение прайса не меняет историю.
 */
public record OrderServiceItem(
        int id,
        int serviceId,
        String serviceName,
        int quantity,
        BigDecimal priceAt,
        BigDecimal lineTotal
) {
}
