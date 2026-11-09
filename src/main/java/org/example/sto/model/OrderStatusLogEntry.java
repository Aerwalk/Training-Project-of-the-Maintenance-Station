package org.example.sto.model;

import java.time.LocalDateTime;

/**
 * Запись журнала изменений статусов (заполняется триггером trigger_log_status_change).
 */
public record OrderStatusLogEntry(
        long id,
        int orderId,
        Integer oldStatusId,
        Integer newStatusId,
        LocalDateTime changedAt,
        String changedBy
) {
}
