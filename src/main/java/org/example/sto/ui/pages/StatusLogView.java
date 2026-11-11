package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Журнал изменений статусов заявок.
 */
public final class StatusLogView implements PageView {

    private record LogData(
            List<OrderStatusLogEntry> log,
            List<OrderStatus> statuses,
            List<OrderSummary> orders) {
    }

    private final AppContext context;
    private final ScrollPane root;
    private final TableView<OrderStatusLogEntry> table = Ui.table();
    private final ComboBox<OrderSummary> filter = new ComboBox<>();
    private final Label foot = Ui.label("", "table-foot");

    private LogData data = new LogData(List.of(), List.of(), List.of());

    public StatusLogView(AppContext context) {
        this.context = context;

        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());

        filter.setConverter(new StringConverter<>() {
            @Override
            public String toString(OrderSummary order) {
                if (order == null) {
                    return "";
                }
                if (order.orderId() == 0) {
                    return "Все заявки";
                }
                return "#" + order.orderId() + " · " + order.client() + " · " + order.car();
            }

            @Override
            public OrderSummary fromString(String string) {
                return null;
            }
        });
        filter.setPrefWidth(320);
        filter.valueProperty().addListener((obs, old, value) -> applyFilter());

        HBox toolbar = new HBox(12,
                Ui.label("Фильтр по заявке", "section-title"),
                filter,
                Ui.spacer(),
                Ui.label("Каждое изменение фиксируется триггером PostgreSQL", "hint"));
        toolbar.getStyleClass().add("card");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = Ui.card(table, foot);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Аудит", "Журнал статусов",
                        "История изменений, записанная триггерами базы данных", refresh),
                toolbar, tableCard);
        root = Views.scroll(content);
    }

    private void configureTable() {
        table.setPrefHeight(500);
        table.getColumns().add(Ui.column("Время", entry -> Fmt.dateTime(entry.changedAt()), 145));
        table.getColumns().add(Ui.column("Заявка", entry -> "#" + entry.orderId(), 80));
        table.getColumns().add(TableCells.badge("Было", entry -> statusName(entry.oldStatusId()), 115));
        table.getColumns().add(TableCells.badge("Стало", entry -> statusName(entry.newStatusId()), 115));
        table.getColumns().add(Ui.column("Кто изменил", entry -> Fmt.nullSafe(entry.changedBy()), 180));
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        context.async(
                () -> new LogData(
                        context.orders().statusLog(context.user(), null),
                        context.catalog().orderStatuses(),
                        context.orders().listVisible(context.user())),
                loaded -> {
                    data = loaded;
                    List<OrderSummary> items = new ArrayList<>();
                    items.add(new OrderSummary(0, "Все заявки", "", "", BigDecimal.ZERO));
                    items.addAll(loaded.orders());
                    OrderSummary selected = filter.getValue();
                    filter.getItems().setAll(items);
                    if (selected == null || selected.orderId() == 0) {
                        filter.getSelectionModel().selectFirst();
                    } else {
                        items.stream()
                                .filter(order -> order.orderId() == selected.orderId())
                                .findFirst()
                                .ifPresent(filter::setValue);
                    }
                    applyFilter();
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void applyFilter() {
        OrderSummary selected = filter.getValue();
        int orderId = selected == null ? 0 : selected.orderId();
        List<OrderStatusLogEntry> filtered = data.log().stream()
                .filter(entry -> orderId == 0 || entry.orderId() == orderId)
                .toList();
        table.getItems().setAll(filtered);
        foot.setText("Показано " + filtered.size() + " из " + data.log().size() + " "
                + Fmt.pluralWord(data.log().size(), "события", "событий", "событий"));
        if (filtered.isEmpty()) {
            table.setPlaceholder(Ui.empty("history", "Событий нет",
                    "Журнал заполнится после изменения статуса заявки."));
        }
    }

    private String statusName(Integer statusId) {
        if (statusId == null) {
            return "—";
        }
        return data.statuses().stream()
                .filter(status -> status.id() == statusId)
                .map(OrderStatus::name)
                .findFirst()
                .orElse("—");
    }
}
