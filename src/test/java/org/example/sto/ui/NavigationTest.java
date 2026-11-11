package org.example.sto.ui;

import org.example.sto.model.RolePermissions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest {

    @Test
    void adminSeesEverySection() {
        List<Navigation> sections = Navigation.visibleFor(RolePermissions.of("admin"));

        assertEquals(Navigation.values().length, sections.size());
        assertEquals(Navigation.DASHBOARD, Navigation.firstVisibleFor(RolePermissions.of("admin")));
    }

    @Test
    void managerSeesAllSections() {
        List<Navigation> sections = Navigation.visibleFor(RolePermissions.of("manager"));

        assertEquals(7, sections.size());
        assertTrue(sections.contains(Navigation.LOG));
    }

    @Test
    void masterHasNoStatusLog() {
        List<Navigation> sections = Navigation.visibleFor(RolePermissions.of("master"));

        assertEquals(6, sections.size());
        assertTrue(sections.contains(Navigation.ORDERS));
        assertTrue(sections.contains(Navigation.SERVICES));
        assertTrue(!sections.contains(Navigation.LOG));
    }

    @Test
    void clientSeesOnlyOrdersAndPriceList() {
        List<Navigation> sections = Navigation.visibleFor(RolePermissions.of("client"));

        assertEquals(List.of(Navigation.ORDERS, Navigation.SERVICES), sections);
        assertEquals(Navigation.ORDERS, Navigation.firstVisibleFor(RolePermissions.of("client")));
    }

    @Test
    void unknownRoleHasNoSections() {
        assertTrue(Navigation.visibleFor(RolePermissions.of("unknown")).isEmpty());
        assertNull(Navigation.firstVisibleFor(RolePermissions.of("unknown")));
    }
}
