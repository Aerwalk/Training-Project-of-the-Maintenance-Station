package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.example.sto.model.Car;
import org.example.sto.model.Client;
import org.example.sto.model.Permission;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.Modal;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.util.List;
import java.util.Locale;

/**
 * Автомобили клиентов сервиса.
 */
public final class CarsView implements PageView {

    private record CarsData(List<Car> cars, List<Client> clients) {
    }

    private final AppContext context;
    private final ScrollPane root;

    private final TableView<Car> table = Ui.table();
    private final TextField search = new TextField();
    private final Label foot = Ui.label("", "table-foot");
    private CarsData data = new CarsData(List.of(), List.of());

    public CarsView(AppContext context) {
        this.context = context;

        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());
        Button create = Ui.primary("Добавить автомобиль", Icon.create("plus", 15, Color.WHITE));
        create.setOnAction(event -> openCreate());

        List<Node> headerActions = new java.util.ArrayList<>();
        headerActions.add(refresh);
        if (context.can(Permission.CREATE_CAR)) {
            headerActions.add(create);
        }

        search.setPromptText("Марка, модель, номер или владелец…");
        search.getStyleClass().add("search-input");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icon.create("search", 15), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPrefWidth(340);
        search.textProperty().addListener((obs, old, value) -> applyFilter());

        HBox toolbar = new HBox(12, Ui.label("Автопарк", "section-title"), Ui.spacer(), searchBox);
        toolbar.getStyleClass().add("card");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = Ui.card(table, foot);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Справочник", "Автомобили", "Автопарк клиентов сервиса",
                        headerActions.toArray(Node[]::new)),
                toolbar, tableCard);
        root = Views.scroll(content);
    }

    private void configureTable() {
        table.setPrefHeight(480);
        table.getColumns().add(Ui.column("Автомобиль", car -> car.brand() + " " + car.model(), 190));
        table.getColumns().add(TableCells.chip("Гос. номер", Car::plateNum, 120));
        table.getColumns().add(Ui.column("Год", car -> car.year() == null ? "—" : String.valueOf(car.year()), 70));
        table.getColumns().add(Ui.column("Владелец", this::clientName, 210));
        table.getColumns().add(Ui.column("VIN", car -> Fmt.nullSafe(car.vin()), 160));
    }

    private String clientName(Car car) {
        return data.clients().stream()
                .filter(client -> client.id() == car.clientId())
                .map(Client::fullName)
                .findFirst()
                .orElse("—");
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
                () -> new CarsData(context.catalog().cars(), context.catalog().clients()),
                loaded -> {
                    data = loaded;
                    applyFilter();
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Car> filtered = data.cars().stream()
                .filter(car -> query.isBlank()
                        || contains(car.brand(), query)
                        || contains(car.model(), query)
                        || contains(car.plateNum(), query)
                        || contains(car.vin(), query)
                        || contains(clientName(car), query))
                .toList();
        table.getItems().setAll(filtered);
        foot.setText("Показано " + filtered.size() + " из " + data.cars().size() + " "
                + Fmt.pluralWord(data.cars().size(), "автомобиля", "автомобилей", "автомобилей"));
        if (filtered.isEmpty()) {
            table.setPlaceholder(Ui.empty("car", "Автомобили не найдены",
                    "Измените запрос или добавьте автомобиль."));
        }
    }

    private void openCreate() {
        if (!context.can(Permission.CREATE_CAR)) {
            context.toast().info("Добавлять автомобили может администратор или мастер-приёмщик.");
            return;
        }
        if (data.clients().isEmpty()) {
            context.toast().info("Список клиентов ещё загружается.");
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
        }, "Выберите владельца");

        TextField brand = Ui.input("Например, Toyota");
        TextField model = Ui.input("Например, Camry");
        TextField year = Ui.input("2020");
        TextField plate = Ui.input("А123ВС777");
        TextField vin = Ui.input("17 символов, необязательно");
        Label error = Ui.label("", "error-text");
        Ui.setShown(error, false);

        Modal modal = new Modal("Новый автомобиль", "Добавление в автопарк клиента", 540);
        Button cancel = Ui.ghost("Отмена", null);
        cancel.setOnAction(event -> modal.hide());
        Button submit = Ui.primary("Добавить автомобиль", Icon.create("plus", 14, Color.WHITE));
        submit.setDefaultButton(true);

        HBox brandRow = new HBox(12, Ui.field("Марка", brand), Ui.field("Модель", model));
        HBox.setHgrow(brandRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(brandRow.getChildren().get(1), Priority.ALWAYS);
        HBox yearRow = new HBox(12, Ui.field("Год выпуска", year), Ui.field("Гос. номер", plate));
        HBox.setHgrow(yearRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(yearRow.getChildren().get(1), Priority.ALWAYS);

        VBox form = new VBox(12,
                Ui.field("Владелец", clientCombo),
                brandRow, yearRow,
                Ui.field("VIN", vin),
                error);
        modal.setContent(form);
        modal.setFooter(cancel, submit);
        modal.show(context.overlay());

        submit.setOnAction(event -> {
            Client client = clientCombo.getValue();
            if (client == null) {
                Ui.showError(error, "Выберите владельца автомобиля");
                return;
            }
            String brandText = brand.getText() == null ? "" : brand.getText().trim();
            String modelText = model.getText() == null ? "" : model.getText().trim();
            String plateText = plate.getText() == null ? "" : plate.getText().trim();
            if (brandText.isBlank() || modelText.isBlank()) {
                Ui.showError(error, "Укажите марку и модель");
                return;
            }
            if (plateText.isBlank()) {
                Ui.showError(error, "Укажите государственный номер");
                return;
            }
            Integer yearValue;
            try {
                yearValue = Fmt.parseInteger(year.getText());
            } catch (NumberFormatException exception) {
                Ui.showError(error, "Год выпуска должен быть числом");
                return;
            }

            submit.setDisable(true);
            submit.setText("Сохранение…");
            context.async(
                    () -> context.catalog().createCar(
                            client.id(), brandText, modelText, yearValue, plateText,
                            vin.getText() == null ? null : vin.getText().trim()),
                    car -> {
                        modal.hide();
                        context.toast().success("Автомобиль " + car.brand() + " " + car.model() + " добавлен");
                        load();
                    },
                    throwable -> {
                        submit.setDisable(false);
                        submit.setText("Добавить автомобиль");
                        Ui.showError(error, AppContext.errorText(throwable));
                    });
        });
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
