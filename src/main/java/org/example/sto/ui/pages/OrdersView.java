package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.Master;
import org.example.sto.model.Order;
import org.example.sto.model.OrderServiceItem;
import org.example.sto.model.OrderStatus;
import org.example.sto.model.OrderStatusLogEntry;
import org.example.sto.model.OrderSummary;
import org.example.sto.model.Permission;
import org.example.sto.model.Service;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.Modal;
import org.example.sto.ui.components.StatusBadge;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран заявок: фильтры, создание, карточка, услуги, закрытие и журнал.
 */
public final class OrdersView implements PageView {

    private record OrdersData(
            List<OrderSummary> orders,
            List<Client> clients,
            List<Car> cars,
            List<Service> services,
            List<Master> masters,
            List<OrderStatus> statuses) {
    }

    private record OrderDetail(
            Order order,
            Client client,
            Car car,
            Master master,
            BigDecimal total,
            List<OrderServiceItem> services,
            List<OrderStatusLogEntry> log) {
    }

    private final AppContext context;
    private final ScrollPane root;

    private final TableView<OrderSummary> table = Ui.table();
    private final TextField search = new TextField();
    private final Label foot = Ui.label("", "table-foot");
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<String, Label> tabCounts = new LinkedHashMap<>();

    private OrdersData data = new OrdersData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private Modal detailModal;
    private int detailId;
    private boolean pendingCreate;
    private Integer pendingClientId;

    public OrdersView(AppContext context) {
        this.context = context;

        Button create = Ui.primary("Новая заявка", Icon.create("plus", 15, Color.WHITE));
        create.setOnAction(event -> openCreate(null));
        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());

        List<Node> headerActions = new ArrayList<>();
        headerActions.add(refresh);
        if (context.can(Permission.CREATE_ORDER)) {
            headerActions.add(create);
        }

        HBox tabs = new HBox(3);
        tabs.getStyleClass().add("tab-bar");
        tabs.getChildren().addAll(
                filterTab("Все", "all"),
                filterTab("Новые", "новая"),
                filterTab("В работе", "в работе"),
                filterTab("Завершённые", "завершена"),
                filterTab("Отменённые", "отменена"));
        filterGroup.selectToggle(filterGroup.getToggles().get(0));
        filterGroup.selectedToggleProperty().addListener((obs, old, value) -> applyFilter());

        search.setPromptText("Номер, клиент, автомобиль…");
        search.getStyleClass().add("search-input");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icon.create("search", 15), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPrefWidth(290);
        search.textProperty().addListener((obs, old, value) -> applyFilter());

        HBox toolbar = new HBox(14, tabs, Ui.spacer(), searchBox);
        toolbar.getStyleClass().add("card");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = Ui.card(table, foot);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Рабочий процесс", "Заявки сервиса",
                        "Полный цикл: приём автомобиля, услуги и закрытие с записью в журнал",
                        headerActions.toArray(Node[]::new)),
                toolbar, tableCard);
        root = Views.scroll(content);
    }

    private ToggleButton filterTab(String title, String key) {
        Label count = Ui.label("0", "tab-count");
        ToggleButton tab = new ToggleButton(title);
        tab.setUserData(key);
        tab.setToggleGroup(filterGroup);
        tab.getStyleClass().add("tab");
        tab.setGraphic(count);
        tab.setContentDisplay(ContentDisplay.RIGHT);
        tab.setGraphicTextGap(6);
        tabCounts.put(key, count);
        return tab;
    }

    private void configureTable() {
        table.setPrefHeight(460);
        table.getColumns().add(Ui.column("Заявка", order -> "#" + order.orderId(), 70));
        table.getColumns().add(Ui.column("Клиент", OrderSummary::client, 170));
        table.getColumns().add(TableCells.chip("Автомобиль", OrderSummary::car, 115));
        table.getColumns().add(TableCells.badge("Статус", OrderSummary::status, 100));
        table.getColumns().add(TableCells.money("Стоимость", OrderSummary::totalCost, 110));
        table.getColumns().add(TableCells.action("", order -> {
            Button open = Ui.ghost("Открыть", Icon.create("eye", 14));
            open.getStyleClass().add("btn-sm");
            open.setOnAction(event -> openDetail(order.orderId()));
            return open;
        }, 105));

        table.setRowFactory(ignored -> {
            TableRow<OrderSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem().orderId());
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
        load();
    }

    private void load() {
        context.async(
                () -> new OrdersData(
                        context.orders().listVisible(context.user()),
                        context.catalog().clients(),
                        context.catalog().cars(),
                        context.catalog().services(),
                        context.catalog().masters(),
                        context.catalog().orderStatuses()),
                loaded -> {
                    data = loaded;
                    updateCounts();
                    applyFilter();
                    if (pendingCreate) {
                        pendingCreate = false;
                        Integer clientId = pendingClientId;
                        pendingClientId = null;
                        if (data.clients().isEmpty()) {
                            context.toast().info(
                                    "В базе нет клиентов — сначала добавьте клиента в разделе «Клиенты».");
                        } else {
                            openCreate(clientId);
                        }
                    }
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void updateCounts() {
        tabCounts.get("all").setText(String.valueOf(data.orders().size()));
        for (String key : List.of("новая", "в работе", "завершена", "отменена")) {
            long count = data.orders().stream().filter(order -> key.equals(order.status())).count();
            tabCounts.get(key).setText(String.valueOf(count));
        }
    }

    private void applyFilter() {
        ToggleButton selected = (ToggleButton) filterGroup.getSelectedToggle();
        String filter = selected == null ? "all" : String.valueOf(selected.getUserData());
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();

        List<OrderSummary> filtered = data.orders().stream()
                .filter(order -> "all".equals(filter) || filter.equals(order.status()))
                .filter(order -> query.isBlank()
                        || String.valueOf(order.orderId()).contains(query)
                        || contains(order.client(), query)
                        || contains(order.car(), query)
                        || contains(order.status(), query))
                .toList();

        table.getItems().setAll(filtered);
        foot.setText("Показано " + filtered.size() + " из " + data.orders().size() + " "
                + Fmt.pluralWord(data.orders().size(), "заявки", "заявок", "заявок"));
        if (filtered.isEmpty()) {
            table.setPlaceholder(Ui.empty("orders", "Заявки не найдены",
                    "Измените фильтр или создайте новую заявку."));
        }
    }

    public void applySearch(String query) {
        search.setText(query == null ? "" : query);
        applyFilter();
    }

    public void openById(int orderId) {
        openDetail(orderId);
    }

    public void openCreate(Integer preselectClientId) {
        if (!context.can(Permission.CREATE_ORDER)) {
            context.toast().info("Создавать заявки может администратор или мастер-приёмщик.");
            return;
        }
        if (data.clients().isEmpty()) {
            pendingCreate = true;
            pendingClientId = preselectClientId;
            load();
            return;
        }

        ComboBox<Client> clientCombo = Ui.combo(data.clients(), new StringConverter<>() {
            @Override
            public String toString(Client client) {
                return client == null ? "" : client.fullName() + " · " + client.phone();
            }

            @Override
            public Client fromString(String string) {
                return null;
            }
        }, "Выберите клиента");

        ComboBox<Car> carCombo = Ui.combo(List.of(), new StringConverter<>() {
            @Override
            public String toString(Car car) {
                return car == null ? "" : car.brand() + " " + car.model() + " · " + car.plateNum();
            }

            @Override
            public Car fromString(String string) {
                return null;
            }
        }, "Сначала выберите клиента");

        List<Master> masterItems = new ArrayList<>();
        masterItems.add(new Master(0, 0, "Не назначать", null, true));
        masterItems.addAll(data.masters());
        ComboBox<Master> masterCombo = Ui.combo(masterItems, new StringConverter<>() {
            @Override
            public String toString(Master master) {
                if (master == null) {
                    return "";
                }
                if (master.id() == 0) {
                    return master.fullName();
                }
                return master.fullName() + (master.specialization() == null ? "" : " · " + master.specialization());
            }

            @Override
            public Master fromString(String string) {
                return null;
            }
        }, "Не назначен");
        masterCombo.getSelectionModel().selectFirst();

        TextArea description = new TextArea();
        description.setPromptText("Опишите неисправность или пожелания клиента");
        description.setWrapText(true);
        description.setPrefRowCount(3);

        Label error = Ui.label("", "error-text");
        Ui.setShown(error, false);

        clientCombo.valueProperty().addListener((obs, old, client) -> {
            List<Car> cars = client == null ? List.of() : data.cars().stream()
                    .filter(car -> car.clientId() == client.id())
                    .toList();
            carCombo.getItems().setAll(cars);
            carCombo.setPromptText(cars.isEmpty() ? "У клиента нет автомобилей" : "Выберите автомобиль");
            if (cars.size() == 1) {
                carCombo.getSelectionModel().selectFirst();
            } else {
                carCombo.getSelectionModel().clearSelection();
            }
        });

        if (preselectClientId != null) {
            data.clients().stream()
                    .filter(client -> client.id() == preselectClientId)
                    .findFirst()
                    .ifPresent(clientCombo::setValue);
        }

        Modal modal = new Modal("Новая заявка", "Приём автомобиля на обслуживание", 560);
        Button cancel = Ui.ghost("Отмена", null);
        cancel.setOnAction(event -> modal.hide());
        Button submit = Ui.primary("Создать заявку", Icon.create("plus", 15, Color.WHITE));
        submit.setDefaultButton(true);

        VBox form = new VBox(12,
                Ui.field("Клиент", clientCombo),
                Ui.field("Автомобиль", carCombo),
                Ui.field("Мастер", masterCombo),
                Ui.field("Описание неисправности", description),
                error);
        modal.setContent(form);
        modal.setFooter(cancel, submit);

        submit.setOnAction(event -> {
            Client client = clientCombo.getValue();
            Car car = carCombo.getValue();
            Master master = masterCombo.getValue();
            String text = description.getText() == null ? "" : description.getText().trim();

            if (client == null) {
                Ui.showError(error, "Выберите клиента");
                return;
            }
            if (car == null) {
                Ui.showError(error, "Выберите автомобиль клиента");
                return;
            }
            if (text.isBlank()) {
                Ui.showError(error, "Опишите неисправность");
                return;
            }
            if (text.length() > 2000) {
                Ui.showError(error, "Описание не должно превышать 2000 символов");
                return;
            }

            Integer masterId = master == null || master.id() == 0 ? null : master.id();
            submit.setDisable(true);
            submit.setText("Создание…");
            context.async(
                    () -> context.orders().createOrder(
                            context.user(), client.id(), car.id(), masterId, text),
                    orderId -> {
                        modal.hide();
                        context.toast().success("Заявка #" + orderId + " создана");
                        load();
                        openDetail(orderId);
                    },
                    throwable -> {
                        submit.setDisable(false);
                        submit.setText("Создать заявку");
                        Ui.showError(error, AppContext.errorText(throwable));
                    });
        });

        modal.show(context.overlay());
    }

    private void openDetail(int orderId) {
        if (detailModal != null) {
            detailModal.hide();
        }
        detailId = orderId;
        Modal modal = new Modal("Заявка #" + orderId, "Карточка обращения", 820);
        modal.onHide(() -> {
            if (detailId == orderId) {
                detailModal = null;
            }
        });
        detailModal = modal;

        StackPane loading = new StackPane(new ProgressIndicator());
        loading.setMinHeight(160);
        modal.setContent(loading);
        Button close = Ui.ghost("Закрыть", null);
        close.setOnAction(event -> modal.hide());
        modal.setFooter(close);
        modal.show(context.overlay());
        reloadDetail(orderId);
    }

    private void reloadDetail(int orderId) {
        if (detailModal == null) {
            return;
        }
        StackPane loading = new StackPane(new ProgressIndicator());
        loading.setMinHeight(160);
        detailModal.setContent(loading);
        context.async(
                () -> fetchDetail(orderId),
                detail -> {
                    if (detailModal == null || detailId != orderId) {
                        return;
                    }
                    detailModal.setContent(buildDetail(detail));
                    detailModal.setFooter(buildDetailFooter(detail));
                },
                error -> {
                    if (detailModal != null && detailId == orderId) {
                        detailModal.hide();
                    }
                    context.toast().error(AppContext.errorText(error));
                });
    }

    private OrderDetail fetchDetail(int orderId) {
        Order order = context.orders().getOrderVisible(context.user(), orderId);
        Client client = context.catalog().clientById(order.clientId());
        Car car = context.catalog().carById(order.carId());
        Master master = order.masterId() == null ? null : context.catalog().masters().stream()
                .filter(item -> item.id() == order.masterId())
                .findFirst()
                .orElse(null);
        BigDecimal total = context.orders().calculateTotal(context.user(), orderId);
        List<OrderServiceItem> services = context.orders().orderServices(context.user(), orderId);
        List<OrderStatusLogEntry> log = context.orders().statusLog(context.user(), orderId);
        return new OrderDetail(order, client, car, master, total, services, log);
    }

    private VBox buildDetail(OrderDetail detail) {
        Order order = detail.order();
        Label badge = StatusBadge.create(order.statusName());
        Label total = Ui.label(Fmt.money(detail.total()), "amount-lg");
        HBox summary = new HBox(10, badge, Ui.spacer(), total);
        summary.setAlignment(Pos.CENTER_LEFT);

        GridPane meta = new GridPane();
        meta.getStyleClass().add("meta-grid");
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        meta.getColumnConstraints().addAll(left, right);
        meta.add(metaBox("Клиент", detail.client().fullName()
                + "\n" + detail.client().phone()
                + (detail.client().email() == null ? "" : "\n" + detail.client().email())), 0, 0);
        meta.add(metaBox("Автомобиль", detail.car().brand() + " " + detail.car().model()
                + "\n" + detail.car().plateNum()
                + (detail.car().vin() == null ? "" : "\nVIN " + detail.car().vin())), 1, 0);
        meta.add(metaBox("Мастер", detail.master() == null ? "Не назначен" : detail.master().fullName()), 0, 1);
        meta.add(metaBox("Создана", Fmt.dateTime(order.createdAt())), 1, 1);
        meta.add(metaBox("Закрыта", order.closedAt() == null ? "—" : Fmt.dateTime(order.closedAt())), 0, 2);
        meta.add(metaBox("Итоговая сумма", Fmt.money(detail.total())), 1, 2);

        Label description = Ui.label(Fmt.nullSafe(order.description()), "small");
        description.setWrapText(true);
        VBox descriptionCard = Ui.card("Описание неисправности", null, description);

        TableView<OrderServiceItem> servicesTable = Ui.table();
        servicesTable.setPrefHeight(160);
        servicesTable.getColumns().add(Ui.column("Услуга", OrderServiceItem::serviceName, 260));
        servicesTable.getColumns().add(Ui.column("Кол-во", item -> String.valueOf(item.quantity()), 65));
        servicesTable.getColumns().add(TableCells.money("Цена", OrderServiceItem::priceAt, 95));
        servicesTable.getColumns().add(TableCells.money("Сумма", OrderServiceItem::lineTotal, 100));
        servicesTable.getItems().setAll(detail.services());
        servicesTable.setPlaceholder(Ui.empty("services", "Услуг пока нет",
                "Добавьте первую услугу в заявку."));

        boolean editable = !"завершена".equals(order.statusName()) && !"отменена".equals(order.statusName());
        VBox servicesBody = new VBox(10, servicesTable);
        if (editable && context.can(Permission.ADD_ORDER_SERVICE)) {
            servicesBody.getChildren().add(buildAddServiceRow(detail));
        }
        VBox servicesCard = Ui.card("Услуги и стоимость", "Цена фиксируется в момент добавления", servicesBody);
        VBox.setVgrow(servicesTable, Priority.ALWAYS);

        VBox logBox = new VBox(7);
        if (detail.log().isEmpty()) {
            logBox.getChildren().add(Ui.label("Изменений статуса пока не было.", "hint"));
        } else {
            for (OrderStatusLogEntry entry : detail.log()) {
                Label dot = new Label();
                dot.getStyleClass().add("timeline-dot");
                Label text = Ui.label(
                        Fmt.dateTime(entry.changedAt()) + " · "
                                + statusName(entry.oldStatusId()) + " → " + statusName(entry.newStatusId()),
                        "small");
                Label author = Ui.label(Fmt.nullSafe(entry.changedBy()), "timeline-meta");
                HBox row = new HBox(9, dot, text, Ui.spacer(), author);
                row.setAlignment(Pos.CENTER_LEFT);
                logBox.getChildren().add(row);
            }
        }
        VBox logCard = Ui.card("Журнал заявки", "Изменения, записанные триггерами БД", logBox);

        return new VBox(14, summary, meta, descriptionCard, servicesCard, logCard);
    }

    private VBox buildAddServiceRow(OrderDetail detail) {
        ComboBox<Service> serviceCombo = Ui.combo(data.services(), new StringConverter<>() {
            @Override
            public String toString(Service service) {
                return service == null ? "" : service.name() + " · " + Fmt.money(service.price());
            }

            @Override
            public Service fromString(String string) {
                return null;
            }
        }, "Выберите услугу");
        HBox.setHgrow(serviceCombo, Priority.ALWAYS);
        serviceCombo.setMaxWidth(Double.MAX_VALUE);

        Spinner<Integer> quantity = new Spinner<>(1, 100, 1);
        quantity.setPrefWidth(78);
        quantity.setEditable(false);

        Button add = Ui.primary("Добавить", Icon.create("plus", 14, Color.WHITE));
        add.getStyleClass().add("btn-sm");

        Label error = Ui.label("", "error-text");
        Ui.setShown(error, false);

        add.setOnAction(event -> {
            Service service = serviceCombo.getValue();
            if (service == null) {
                Ui.showError(error, "Выберите услугу");
                return;
            }
            add.setDisable(true);
            context.async(
                    () -> {
                        context.orders().addService(
                                context.user(), detail.order().id(), service.id(), quantity.getValue());
                        return null;
                    },
                    ignored -> {
                        context.toast().success("Услуга «" + service.name() + "» добавлена");
                        load();
                        reloadDetail(detail.order().id());
                    },
                    throwable -> {
                        add.setDisable(false);
                        Ui.showError(error, AppContext.errorText(throwable));
                    });
        });

        HBox row = new HBox(8, serviceCombo, quantity, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, row, error);
    }

    private HBox buildDetailFooter(OrderDetail detail) {
        Button close = Ui.ghost("Закрыть", null);
        close.setOnAction(event -> {
            if (detailModal != null) {
                detailModal.hide();
            }
        });

        boolean editable = !"завершена".equals(detail.order().statusName())
                && !"отменена".equals(detail.order().statusName());
        if (!editable || !context.can(Permission.CLOSE_ORDER)) {
            return new HBox(9, close);
        }

        Button finish = Ui.danger("Завершить заявку", Icon.create("check", 15));
        finish.setOnAction(event -> Modal.confirm(
                context.overlay(),
                "Завершить заявку #" + detail.order().id() + "?",
                "Итоговая сумма составит " + Fmt.money(detail.total())
                        + ". После закрытия добавить услуги будет нельзя.",
                "Завершить",
                () -> closeOrder(detail.order().id())));
        return new HBox(9, close, finish);
    }

    private void closeOrder(int orderId) {
        context.async(
                () -> {
                    context.orders().closeOrder(context.user(), orderId);
                    return null;
                },
                ignored -> {
                    context.toast().success("Заявка #" + orderId + " завершена");
                    load();
                    reloadDetail(orderId);
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private VBox metaBox(String label, String value) {
        Label valueLabel = Ui.label(value, "meta-value");
        valueLabel.setWrapText(true);
        return new VBox(2, Ui.label(label, "meta-label"), valueLabel);
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

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }
}
