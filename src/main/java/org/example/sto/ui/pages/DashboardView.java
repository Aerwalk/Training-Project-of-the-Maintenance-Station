package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.Master;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Service;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.DonutChart;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.StatCard;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Сводный экран: показатели, распределение заявок и последние события.
 */
public final class DashboardView implements PageView {

    private record Data(
            List<OrderSummary> orders,
            List<Client> clients,
            List<Car> cars,
            List<Service> services,
            List<Master> masters,
            List<OrderStatus> statuses,
            List<OrderStatusLogEntry> log) {
    }

    private static final Map<String, Color> STATUS_COLORS = Map.of(
            "новая", Color.web("#2463eb"),
            "в работе", Color.web("#f59e0b"),
            "завершена", Color.web("#10b981"),
            "отменена", Color.web("#ef4444"));

    private final AppContext context;
    private final IntConsumer onOpenOrder;

    private final StatCard totalCard = new StatCard("Всего заявок", "за всё время", "orders", "#2463eb", "#e8efff");
    private final StatCard progressCard = new StatCard("В работе", "требуют внимания", "clock", "#f59e0b", "#fef5e3");
    private final StatCard doneCard = new StatCard("Завершено", "успешно закрыто", "check", "#10b981", "#e7f8f1");
    private final StatCard revenueCard = new StatCard("Выручка", "по всем заявкам", "ruble", "#7c3aed", "#f0eafd");

    private final DonutChart donut = new DonutChart();
    private final VBox legend = new VBox(9);
    private final TableView<OrderSummary> activeTable = Ui.table();
    private final VBox timeline = new VBox();
    private final Label clientsValue = Ui.label("—", "strong");
    private final Label carsValue = Ui.label("—", "strong");
    private final Label servicesValue = Ui.label("—", "strong");
    private final Label mastersValue = Ui.label("—", "strong");
    private final Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));

    private List<OrderStatus> statuses = List.of();
    private final javafx.scene.control.ScrollPane root;

    public DashboardView(AppContext context, IntConsumer onOpenOrder) {
        this.context = context;
        this.onOpenOrder = onOpenOrder;

        refresh.setOnAction(event -> refresh());

        GridPane kpi = new GridPane();
        kpi.getStyleClass().add("kpi-grid");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(25);
            constraints.setHgrow(Priority.ALWAYS);
            kpi.getColumnConstraints().add(constraints);
        }
        kpi.add(totalCard, 0, 0);
        kpi.add(progressCard, 1, 0);
        kpi.add(doneCard, 2, 0);
        kpi.add(revenueCard, 3, 0);

        legend.setAlignment(Pos.CENTER_LEFT);
        HBox donutRow = new HBox(18, donut, legend);
        donutRow.setAlignment(Pos.CENTER_LEFT);
        VBox statusCard = Ui.card("Статусы заявок", "Распределение по всему сервису", donutRow);
        statusCard.setMinWidth(380);
        statusCard.setPrefWidth(410);
        statusCard.setMaxWidth(440);

        configureActiveTable();
        VBox activeCard = Ui.card("Активные заявки", "Незавершённые обращения, требующие работы", activeTable);
        HBox.setHgrow(activeCard, Priority.ALWAYS);

        HBox middle = new HBox(14, statusCard, activeCard);
        middle.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(statusCard, Priority.NEVER);

        VBox logCard = Ui.card("Последние события", "Журнал изменений, записанный триггерами БД", timeline);
        HBox.setHgrow(logCard, Priority.ALWAYS);

        VBox summaryCard = Ui.card("Справочники", "Наполнение базы данных",
                summaryRow("clients", "Клиенты", clientsValue, "#e8efff", "#2463eb"),
                summaryRow("car", "Автомобили", carsValue, "#e2f8f7", "#0ea5a5"),
                summaryRow("services", "Услуги", servicesValue, "#f0eafd", "#7c3aed"),
                summaryRow("masters", "Мастера", mastersValue, "#fff1e7", "#f97316"));
        summaryCard.setMinWidth(300);
        summaryCard.setPrefWidth(320);
        summaryCard.setMaxWidth(340);

        HBox bottom = new HBox(14, logCard, summaryCard);
        bottom.setAlignment(Pos.TOP_LEFT);

        VBox content = Views.page(
                Ui.pageHeader("Бизнес-обзор", "Обзор", "Главные показатели и события сервиса", refresh),
                kpi, middle, bottom);
        root = Views.scroll(content);
    }

    private void configureActiveTable() {
        activeTable.setPrefHeight(330);
        activeTable.getColumns().add(Ui.column("№", order -> "#" + order.orderId(), 55));
        activeTable.getColumns().add(Ui.column("Клиент", OrderSummary::client, 150));
        activeTable.getColumns().add(TableCells.chip("Автомобиль", OrderSummary::car, 120));
        activeTable.getColumns().add(TableCells.badge("Статус", OrderSummary::status, 95));
        activeTable.getColumns().add(TableCells.money("Стоимость", OrderSummary::totalCost, 105));
        activeTable.getColumns().add(TableCells.action("", order -> {
            Button open = Ui.soft("Открыть", null);
            open.getStyleClass().add("btn-sm");
            open.setOnAction(event -> onOpenOrder.accept(order.orderId()));
            return open;
        }, 95));
        activeTable.setRowFactory(ignored -> {
            TableRow<OrderSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onOpenOrder.accept(row.getItem().orderId());
                }
            });
            return row;
        });
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        refresh.setDisable(true);
        context.async(
                () -> new Data(
                        context.orders().listVisible(context.user()),
                        context.catalog().clients(),
                        context.catalog().cars(),
                        context.catalog().services(),
                        context.catalog().masters(),
                        context.catalog().orderStatuses(),
                        context.orders().statusLog(context.user(), null)),
                data -> {
                    refresh.setDisable(false);
                    render(data);
                },
                error -> {
                    refresh.setDisable(false);
                    context.toast().error(AppContext.errorText(error));
                });
    }

    private void render(Data data) {
        statuses = data.statuses();
        long total = data.orders().size();
        long inProgress = count(data.orders(), "в работе");
        long completed = count(data.orders(), "завершена");
        BigDecimal revenue = data.orders().stream()
                .map(OrderSummary::totalCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCard.setValue(String.valueOf(total));
        progressCard.setValue(String.valueOf(inProgress));
        doneCard.setValue(String.valueOf(completed));
        revenueCard.setValue(Fmt.money(revenue));

        List<DonutChart.Slice> slices = data.statuses().stream()
                .map(status -> new DonutChart.Slice(
                        status.name(),
                        count(data.orders(), status.name()),
                        STATUS_COLORS.getOrDefault(status.name(), Color.web("#94a3b8"))))
                .filter(slice -> slice.count() > 0)
                .toList();
        donut.setData(slices);

        legend.getChildren().clear();
        for (OrderStatus status : data.statuses()) {
            long value = count(data.orders(), status.name());
            Label dot = new Label();
            dot.setMinSize(9, 9);
            dot.setMaxSize(9, 9);
            dot.setStyle("-fx-background-radius: 999; -fx-background-color: "
                    + toHex(STATUS_COLORS.getOrDefault(status.name(), Color.web("#94a3b8"))) + ";");
            Label name = Ui.label(status.name(), "small");
            Label count = Ui.label(String.valueOf(value), "strong");
            HBox row = new HBox(8, dot, name, Ui.spacer(), count);
            row.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(row);
        }
        legend.getChildren().add(new javafx.scene.control.Separator());
        HBox totalRow = new HBox(8,
                Ui.label("Итого", "small"),
                Ui.spacer(),
                Ui.label(String.valueOf(total), "strong"));
        totalRow.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().add(totalRow);

        List<OrderSummary> active = data.orders().stream()
                .filter(order -> !"завершена".equals(order.status()) && !"отменена".equals(order.status()))
                .sorted((left, right) -> Integer.compare(right.orderId(), left.orderId()))
                .limit(6)
                .toList();
        activeTable.getItems().setAll(active);
        activeTable.setPlaceholder(Ui.empty("check", "Активных заявок нет", "Все обращения завершены или отменены."));

        renderTimeline(data.log());

        clientsValue.setText(Fmt.plural(data.clients().size(), "клиент", "клиента", "клиентов"));
        carsValue.setText(Fmt.plural(data.cars().size(), "автомобиль", "автомобиля", "автомобилей"));
        servicesValue.setText(Fmt.plural(data.services().size(), "услуга", "услуги", "услуг"));
        mastersValue.setText(Fmt.plural(data.masters().size(), "мастер", "мастера", "мастеров"));
    }

    private void renderTimeline(List<OrderStatusLogEntry> log) {
        timeline.getChildren().clear();
        timeline.getStyleClass().setAll("timeline");
        if (log.isEmpty()) {
            timeline.getChildren().add(Ui.empty("history", "Событий пока нет", "Журнал заполнится после изменения статуса заявки."));
            return;
        }
        List<OrderStatusLogEntry> preview = log.stream().limit(5).toList();
        for (int index = 0; index < preview.size(); index++) {
            OrderStatusLogEntry entry = preview.get(index);
            Label dot = new Label();
            dot.getStyleClass().add("timeline-dot");
            String color = entry.newStatusId() == null ? "#94a3b8" : statusColor(entry.newStatusId());
            dot.setStyle("-fx-background-color: " + color + ";");

            VBox rail = new VBox(0, dot);
            rail.getStyleClass().add("timeline-rail");
            rail.setAlignment(Pos.TOP_CENTER);
            if (index < preview.size() - 1) {
                javafx.scene.layout.Region line = new javafx.scene.layout.Region();
                line.getStyleClass().add("timeline-line");
                line.setMinHeight(34);
                line.setPrefHeight(34);
                rail.getChildren().add(line);
            }

            Label title = Ui.label("Заявка #" + entry.orderId(), "timeline-title");
            Label change = Ui.label(
                    statusName(entry.oldStatusId()) + "  →  " + statusName(entry.newStatusId()), "small");
            Label meta = Ui.label(Fmt.dateTime(entry.changedAt()) + " · " + Fmt.nullSafe(entry.changedBy()), "timeline-meta");
            VBox body = new VBox(2, title, change, meta);
            body.getStyleClass().add("timeline-body");

            HBox row = new HBox(12, rail, body);
            row.getStyleClass().add("timeline-row");
            HBox.setHgrow(body, Priority.ALWAYS);
            timeline.getChildren().add(row);
        }
    }

    private HBox summaryRow(String icon, String title, Label value, String soft, String color) {
        Label iconBox = new Label();
        iconBox.getStyleClass().add("kpi-icon");
        iconBox.setMinSize(32, 32);
        iconBox.setMaxSize(32, 32);
        iconBox.setGraphic(Icon.create(icon, 15, Color.web(color)));
        iconBox.setStyle("-fx-background-color: " + soft + ";");
        HBox row = new HBox(10, iconBox, Ui.label(title, "small"), Ui.spacer(), value);
        row.getStyleClass().add("stat-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private long count(List<OrderSummary> orders, String status) {
        return orders.stream().filter(order -> status.equals(order.status())).count();
    }

    private String statusName(Integer statusId) {
        if (statusId == null) {
            return "—";
        }
        return statuses.stream()
                .filter(status -> status.id() == statusId)
                .map(OrderStatus::name)
                .findFirst()
                .orElse("—");
    }

    private String statusColor(int statusId) {
        return statuses.stream()
                .filter(status -> status.id() == statusId)
                .map(status -> toHex(STATUS_COLORS.getOrDefault(status.name(), Color.web("#94a3b8"))))
                .findFirst()
                .orElse("#94a3b8");
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
