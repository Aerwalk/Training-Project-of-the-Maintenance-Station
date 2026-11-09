package org.example.sto.model;

import java.math.BigDecimal;

public record Service(int id, String name, BigDecimal price, Integer durationMin) {
}
