package org.example.sto.ui;

import org.example.sto.model.Permission;
import org.example.sto.model.RolePermissions;

import java.util.Arrays;
import java.util.List;

/**
 * Разделы основного окна. Каждому разделу соответствует право доступа:
 * боковое меню и навигация показывают только разрешённые роли пункты.
 */
public enum Navigation {
    DASHBOARD("Обзор", "Главные показатели и события сервиса", "dashboard", Permission.VIEW_DASHBOARD),
    ORDERS("Заявки", "Приём, сопровождение и закрытие заказов", "orders", Permission.VIEW_ORDERS),
    CLIENTS("Клиенты", "База владельцев автомобилей", "clients", Permission.VIEW_CLIENTS),
    CARS("Автомобили", "Автопарк клиентов сервиса", "car", Permission.VIEW_CARS),
    SERVICES("Услуги", "Прайс-лист и длительность работ", "services", Permission.VIEW_SERVICES),
    MASTERS("Мастера", "Команда и специализации", "masters", Permission.VIEW_MASTERS),
    LOG("Журнал статусов", "История изменений, записанная триггерами БД", "history", Permission.VIEW_LOG);

    private final String title;
    private final String subtitle;
    private final String icon;
    private final Permission permission;

    Navigation(String title, String subtitle, String icon, Permission permission) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.permission = permission;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String icon() {
        return icon;
    }

    public Permission permission() {
        return permission;
    }

    /**
     * Разделы, доступные роли. Используется боковым меню и навигацией,
     * поэтому проверка прав выполняется в одном месте.
     */
    public static List<Navigation> visibleFor(RolePermissions permissions) {
        return Arrays.stream(values())
                .filter(navigation -> permissions.can(navigation.permission()))
                .toList();
    }

    /** Первый доступный раздел — с него открывается основное окно. */
    public static Navigation firstVisibleFor(RolePermissions permissions) {
        return visibleFor(permissions).stream().findFirst().orElse(null);
    }
}
