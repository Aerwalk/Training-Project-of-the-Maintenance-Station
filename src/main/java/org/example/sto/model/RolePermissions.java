package org.example.sto.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Матрица прав ролей приложения.
 *
 * <p>Разграничение выполняется в интерфейсе (какие разделы и кнопки видны)
 * и повторно проверяется в коде страниц, чтобы действие нельзя было вызвать
 * в обход навигации. Права в PostgreSQL настроены отдельно в {@code sto.sql}
 * и демонстрируют ограничения на уровне самой базы данных.
 */
public final class RolePermissions {

    public static final String ADMIN = "admin";
    public static final String MANAGER = "manager";
    public static final String MASTER = "master";
    public static final String CLIENT = "client";

    private static final Map<String, Set<Permission>> RULES = Map.of(
            ADMIN, Set.copyOf(EnumSet.allOf(Permission.class)),
            MANAGER, Set.copyOf(EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_ORDERS,
                    Permission.VIEW_CLIENTS,
                    Permission.VIEW_CARS,
                    Permission.VIEW_SERVICES,
                    Permission.VIEW_MASTERS,
                    Permission.VIEW_LOG,
                    Permission.CREATE_ORDER,
                    Permission.ADD_ORDER_SERVICE,
                    Permission.CLOSE_ORDER,
                    Permission.CREATE_CLIENT,
                    Permission.CREATE_CAR,
                    Permission.VIEW_ALL_ORDERS)),
            MASTER, Set.copyOf(EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_ORDERS,
                    Permission.VIEW_CLIENTS,
                    Permission.VIEW_CARS,
                    Permission.VIEW_SERVICES,
                    Permission.VIEW_MASTERS,
                    Permission.ADD_ORDER_SERVICE,
                    Permission.CLOSE_ORDER,
                    Permission.VIEW_ALL_ORDERS)),
            CLIENT, Set.copyOf(EnumSet.of(
                    Permission.VIEW_ORDERS,
                    Permission.VIEW_SERVICES)));

    private static final RolePermissions GUEST = new RolePermissions("guest", Set.of());

    private final String role;
    private final Set<Permission> permissions;

    private RolePermissions(String role, Set<Permission> permissions) {
        this.role = role;
        this.permissions = permissions;
    }

    public static RolePermissions of(String role) {
        if (role == null || role.isBlank()) {
            return GUEST;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        Set<Permission> permissions = RULES.get(normalized);
        return permissions == null ? GUEST : new RolePermissions(normalized, permissions);
    }

    public String role() {
        return role;
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }

    /** Есть ли у роли хотя бы один доступный раздел. */
    public boolean hasAnySection() {
        return !permissions.isEmpty();
    }
}
