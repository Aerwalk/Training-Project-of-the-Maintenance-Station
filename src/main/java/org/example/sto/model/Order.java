package org.example.sto.model;

import java.time.LocalDateTime;

public record Order(
        int id,
        int clientId,
        int carId,
        Integer masterId,
        int statusId,
        String statusName,
        String description,
        LocalDateTime createdAt,
        LocalDateTime closedAt
) {
}
