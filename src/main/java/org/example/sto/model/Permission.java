package org.example.sto.model;

/**
 * Права доступа к разделам и операциям приложения.
 * Набор прав для каждой роли задаётся в {@link RolePermissions}.
 */
public enum Permission {
    VIEW_DASHBOARD,
    VIEW_ORDERS,
    VIEW_CLIENTS,
    VIEW_CARS,
    VIEW_SERVICES,
    VIEW_MASTERS,
    VIEW_LOG,
    CREATE_ORDER,
    ADD_ORDER_SERVICE,
    CLOSE_ORDER,
    CREATE_CLIENT,
    CREATE_CAR,
    CREATE_SERVICE,
    VIEW_ALL_ORDERS
}
