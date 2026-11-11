package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.ClientOrder;
import org.example.sto.model.Permission;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.Modal;
import org.example.sto.ui.components.StatusBadge;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Клиенты и их автомобили.
 */
public final class ClientsView implements PageView {

    private record ClientsData(List<Client> clients, List<Car> cars) {
    }

    private final AppContext context;
    private final IntConsumer onNewOrder;
    private final ScrollPane root;

    private final TableView<Client> table = Ui.table();
    private final TextField search = new TextField();
    private final Label foot = Ui.label("", "table-foot");
    private ClientsData data = new ClientsData(List.of(), List.of());

    public ClientsView(AppContext context, IntConsumer onNewOrder) {
        this.context = context;
        this.onNewOrder = onNewOrder;

        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());
        Button create = Ui.primary("Новый клиент", Icon.create("plus", 15, Color.WHITE));
        create.setOnAction(event -> openCreate());

        List<Node> headerActions = new ArrayList<>();
        headerActions.add(refresh);
        if (context.can(Permission.CREATE_CLIENT)) {
            headerActions.add(create);
        }

        search.setPromptText("ФИО, телефон или e-mail…");
        search.getStyleClass().add("search-input");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icon.create("search", 15), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPrefWidth(320);
        search.textProperty().addListener((obs, old, value) -> applyFilter());

        HBox toolbar = new HBox(12, Ui.label("База клиентов", "section-title"), Ui.spacer(), searchBox);
        toolbar.getStyleClass().add("card");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = Ui.card(table, foot);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Справочник", "Клиенты", "База владельцев автомобилей",
                        headerActions.toArray(Node[]::new)),
                toolbar, tableCard);
        root = Views.scroll(content);
    }

    private void configureTable() {
        table.setPrefHeight(480);

        TableColumn<Client, String> person = new TableColumn<>("Клиент");
        person.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue().fullName()));
        person.setPrefWidth(280);
        person.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Client client = getTableView().getItems().get(getIndex());
                Label avatar = Ui.label(Fmt.initials(client.fullName()), "avatar");
                Label name = Ui.label(client.fullName(), "strong");
                Label email = Ui.label(Fmt.nullSafe(client.email()), "small", "muted");
                VBox text = new VBox(1, name, email);
                HBox row = new HBox(10, avatar, text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        table.getColumns().add(person);
        table.getColumns().add(Ui.column("Телефон", Client::phone, 150));
        table.getColumns().add(Ui.column("Автомобили", client -> {
            long count = data.cars().stream().filter(car -> car.clientId() == client.id()).count();
            return Fmt.plural(count, "авто", "авто", "авто");
        }, 110));
        table.getColumns().add(Ui.column("В базе с", client -> Fmt.shortDate(client.createdAt()), 120));
        table.getColumns().add(TableCells.action("", client -> {
            Button open = Ui.ghost("Открыть", Icon.create("eye", 14));
            open.getStyleClass().add("btn-sm");
            open.setOnAction(event -> openDetail(client));
            return open;
        }, 100));

        table.setRowFactory(ignored -> {
            TableRow<Client> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem());
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
                () -> new ClientsData(context.catalog().clients(), context.catalog().cars()),
                loaded -> {
                    data = loaded;
                    applyFilter();
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Client> filtered = data.clients().stream()
                .filter(client -> query.isBlank()
                        || contains(client.fullName(), query)
                        || contains(client.phone(), query)
                        || contains(client.email(), query))
                .toList();
        table.getItems().setAll(filtered);
        foot.setText("Показано " + filtered.size() + " из " + data.clients().size() + " "
                + Fmt.pluralWord(data.clients().size(), "клиента", "клиентов", "клиентов"));
        if (filtered.isEmpty()) {
            table.setPlaceholder(Ui.empty("clients", "Клиенты не найдены",
                    "Измените запрос или добавьте нового клиента."));
        }
    }

    private void openDetail(Client client) {
        Modal modal = new Modal(client.fullName(), "Карточка клиента", 720);
        List<Car> cars = data.cars().stream().filter(car -> car.clientId() == client.id()).toList();

        VBox carsBox = new VBox(7);
        if (cars.isEmpty()) {
            carsBox.getChildren().add(Ui.label("У клиента пока нет автомобилей.", "hint"));
        } else {
            for (Car car : cars) {
                Label chip = new Label(car.plateNum());
                chip.getStyleClass().addAll("chip", "chip-plate");
                HBox row = new HBox(9,
                        Ui.label(car.brand() + " " + car.model() + (car.year() == null ? "" : ", " + car.year()), "small"),
                        Ui.spacer(), chip);
                row.setAlignment(Pos.CENTER_LEFT);
                carsBox.getChildren().add(row);
            }
        }

        TableView<ClientOrder> ordersTable = Ui.table();
        ordersTable.setPrefHeight(190);
        ordersTable.getColumns().add(Ui.column("Заявка", order -> "#" + order.orderId(), 70));
        ordersTable.getColumns().add(TableCells.chip("Автомобиль", ClientOrder::carPlate, 110));
        ordersTable.getColumns().add(TableCells.badge("Статус", ClientOrder::status, 100));
        ordersTable.getColumns().add(TableCells.money("Стоимость", ClientOrder::totalCost, 105));
        ordersTable.getColumns().add(Ui.column("Создана", order -> Fmt.dateTime(order.createdAt()), 130));
        ordersTable.setPlaceholder(Ui.empty("orders", "Заявок нет", "У клиента ещё не было обращений."));

        Label loading = Ui.label("Загрузка заявок…", "hint");
        VBox content = new VBox(14,
                metaRow("clients", "Телефон", client.phone()),
                metaRow("mail", "E-mail", Fmt.nullSafe(client.email())),
                Ui.card("Автомобили", null, carsBox),
                Ui.card("Заявки клиента", null, loading));
        modal.setContent(content);

        Button close = Ui.ghost("Закрыть", null);
        close.setOnAction(event -> modal.hide());
        if (context.can(Permission.CREATE_ORDER)) {
            Button createOrder = Ui.primary("Новая заявка", Icon.create("plus", 14, Color.WHITE));
            createOrder.setOnAction(event -> {
                modal.hide();
                onNewOrder.accept(client.id());
            });
            modal.setFooter(close, createOrder);
        } else {
            modal.setFooter(close);
        }
        modal.show(context.overlay());

        context.async(
                () -> context.orders().clientOrders(client.id()),
                orders -> {
                    ordersTable.getItems().setAll(orders);
                    VBox updated = new VBox(14,
                            metaRow("clients", "Телефон", client.phone()),
                            metaRow("mail", "E-mail", Fmt.nullSafe(client.email())),
                            Ui.card("Автомобили", null, carsBox),
                            Ui.card("Заявки клиента", null, ordersTable));
                    modal.setContent(updated);
                },
                error -> modal.setContent(new VBox(14,
                        metaRow("clients", "Телефон", client.phone()),
                        metaRow("mail", "E-mail", Fmt.nullSafe(client.email())),
                        Ui.card("Автомобили", null, carsBox),
                        Ui.label("Не удалось загрузить заявки: " + AppContext.errorText(error), "error-text"))));
    }

    private HBox metaRow(String icon, String label, String value) {
        Label iconBox = new Label();
        iconBox.getStyleClass().add("kpi-icon");
        iconBox.setMinSize(32, 32);
        iconBox.setMaxSize(32, 32);
        iconBox.setGraphic(Icon.create(icon, 15, Color.web("#2463eb")));
        iconBox.setStyle("-fx-background-color: #e8efff;");
        HBox row = new HBox(10, iconBox, Ui.label(label, "small"), Ui.spacer(), Ui.label(value, "strong"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void openCreate() {
        if (!context.can(Permission.CREATE_CLIENT)) {
            context.toast().info("Добавлять клиентов может администратор или мастер-приёмщик.");
            return;
        }
        TextField fullName = Ui.input("Иванов Иван Иванович");
        TextField phone = Ui.input("+7 900 000-00-00");
        TextField email = Ui.input("client@example.ru");
        Label error = Ui.label("", "error-text");
        Ui.setShown(error, false);

        Modal modal = new Modal("Новый клиент", "Добавление в базу сервиса", 500);
        Button cancel = Ui.ghost("Отмена", null);
        cancel.setOnAction(event -> modal.hide());
        Button submit = Ui.primary("Добавить клиента", Icon.create("plus", 14, Color.WHITE));
        submit.setDefaultButton(true);

        VBox form = new VBox(12,
                Ui.field("ФИО", fullName),
                Ui.field("Телефон", phone),
                Ui.field("E-mail (необязательно)", email),
                error);
        modal.setContent(form);
        modal.setFooter(cancel, submit);
        modal.show(context.overlay());

        submit.setOnAction(event -> {
            String name = fullName.getText() == null ? "" : fullName.getText().trim();
            String tel = phone.getText() == null ? "" : phone.getText().trim();
            String mail = email.getText() == null ? "" : email.getText().trim();
            if (name.isBlank()) {
                Ui.showError(error, "Укажите ФИО клиента");
                return;
            }
            if (tel.isBlank()) {
                Ui.showError(error, "Укажите телефон");
                return;
            }
            submit.setDisable(true);
            submit.setText("Сохранение…");
            context.async(
                    () -> context.catalog().createClient(name, tel, mail),
                    client -> {
                        modal.hide();
                        context.toast().success("Клиент «" + client.fullName() + "» добавлен");
                        load();
                    },
                    throwable -> {
                        submit.setDisable(false);
                        submit.setText("Добавить клиента");
                        Ui.showError(error, AppContext.errorText(throwable));
                    });
        });
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
