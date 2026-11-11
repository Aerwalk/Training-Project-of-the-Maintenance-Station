package org.example.sto.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionsTest {

    @Test
    void adminCanDoEverything() {
        RolePermissions admin = RolePermissions.of("admin");

        for (Permission permission : Permission.values()) {
            assertTrue(admin.can(permission), "admin должен иметь право " + permission);
        }
    }

    @Test
    void managerManagesOrdersAndCatalogsButNotPriceList() {
        RolePermissions manager = RolePermissions.of("manager");

        assertTrue(manager.can(Permission.CREATE_ORDER));
        assertTrue(manager.can(Permission.CREATE_CLIENT));
        assertTrue(manager.can(Permission.CREATE_CAR));
        assertTrue(manager.can(Permission.VIEW_LOG));
        assertFalse(manager.can(Permission.CREATE_SERVICE));
    }

    @Test
    void masterWorksWithOrdersButDoesNotManageCatalogs() {
        RolePermissions master = RolePermissions.of("master");

        assertTrue(master.can(Permission.VIEW_ORDERS));
        assertTrue(master.can(Permission.ADD_ORDER_SERVICE));
        assertTrue(master.can(Permission.CLOSE_ORDER));
        assertFalse(master.can(Permission.CREATE_ORDER));
        assertFalse(master.can(Permission.CREATE_CLIENT));
        assertFalse(master.can(Permission.CREATE_CAR));
        assertFalse(master.can(Permission.CREATE_SERVICE));
        assertFalse(master.can(Permission.VIEW_LOG));
    }

    @Test
    void clientSeesOnlyOwnOrdersAndPriceList() {
        RolePermissions client = RolePermissions.of("client");

        assertTrue(client.can(Permission.VIEW_ORDERS));
        assertTrue(client.can(Permission.VIEW_SERVICES));
        assertFalse(client.can(Permission.VIEW_ALL_ORDERS));
        assertFalse(client.can(Permission.VIEW_DASHBOARD));
        assertFalse(client.can(Permission.VIEW_CLIENTS));
        assertFalse(client.can(Permission.VIEW_CARS));
        assertFalse(client.can(Permission.VIEW_MASTERS));
        assertFalse(client.can(Permission.VIEW_LOG));
        assertFalse(client.can(Permission.CREATE_ORDER));
        assertFalse(client.can(Permission.ADD_ORDER_SERVICE));
        assertFalse(client.can(Permission.CLOSE_ORDER));
    }

    @Test
    void unknownRoleHasNoAccess() {
        RolePermissions guest = RolePermissions.of("unknown");

        assertFalse(guest.hasAnySection());
        for (Permission permission : Permission.values()) {
            assertFalse(guest.can(permission));
        }
    }
}
