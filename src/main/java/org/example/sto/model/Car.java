package org.example.sto.model;

public record Car(int id, int clientId, String brand, String model, Integer year, String plateNum, String vin) {
}
