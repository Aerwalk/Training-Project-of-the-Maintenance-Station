package org.example.sto.ui;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.example.sto.model.Permission;
import org.example.sto.ui.components.Sidebar;
import org.example.sto.ui.components.TopBar;
import org.example.sto.ui.pages.CarsView;
import org.example.sto.ui.pages.ClientsView;
import org.example.sto.ui.pages.DashboardView;
import org.example.sto.ui.pages.MastersView;
import org.example.sto.ui.pages.OrdersView;
import org.example.sto.ui.pages.PageView;
import org.example.sto.ui.pages.ServicesView;
import org.example.sto.ui.pages.StatusLogView;

import java.util.EnumMap;
import java.util.Map;

/**
 * Основное окно: боковое меню, верхняя панель и область текущего раздела.
 * Набор доступных разделов зависит от роли пользователя.
 */
public final class Shell extends BorderPane {

    private final Map<Navigation, PageView> pages = new EnumMap<>(Navigation.class);
    private final Sidebar sidebar;
    private final TopBar topBar;
    private final StackPane content = new StackPane();
    private final AppContext context;

    public Shell(AppContext context) {
        this.context = context;
        sidebar = new Sidebar(context, this::navigate);
        topBar = new TopBar(this::searchOrders);

        pages.put(Navigation.DASHBOARD, new DashboardView(context, this::openOrder));
        pages.put(Navigation.ORDERS, new OrdersView(context));
        pages.put(Navigation.CLIENTS, new ClientsView(context, this::newOrderForClient));
        pages.put(Navigation.CARS, new CarsView(context));
        pages.put(Navigation.SERVICES, new ServicesView(context));
        pages.put(Navigation.MASTERS, new MastersView(context));
        pages.put(Navigation.LOG, new StatusLogView(context));

        setLeft(sidebar);
        setTop(topBar);
        setCenter(content);
        navigate(firstAvailable());
    }

    private Navigation firstAvailable() {
        Navigation first = Navigation.firstVisibleFor(context.permissions());
        return first == null ? Navigation.SERVICES : first;
    }

    private void navigate(Navigation navigation) {
        if (!context.can(navigation.permission())) {
            return;
        }
        PageView page = pages.get(navigation);
        if (page == null) {
            return;
        }
        topBar.setMeta(navigation);
        sidebar.setActive(navigation);
        content.getChildren().setAll(page.node());
        page.onShow();
    }

    private void openOrder(int orderId) {
        navigate(Navigation.ORDERS);
        ((OrdersView) pages.get(Navigation.ORDERS)).openById(orderId);
    }

    private void newOrderForClient(int clientId) {
        if (!context.can(Permission.CREATE_ORDER)) {
            return;
        }
        navigate(Navigation.ORDERS);
        ((OrdersView) pages.get(Navigation.ORDERS)).openCreate(clientId);
    }

    private void searchOrders(String query) {
        navigate(Navigation.ORDERS);
        ((OrdersView) pages.get(Navigation.ORDERS)).applySearch(query);
    }
}
