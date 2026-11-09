package org.example.sto.model;

import java.time.LocalDateTime;

public record Client(int id, String fullName, String phone, String email, LocalDateTime createdAt) {
}
