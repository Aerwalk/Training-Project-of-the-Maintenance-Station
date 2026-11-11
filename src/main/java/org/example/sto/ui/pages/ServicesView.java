package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.Permission;
import org.example.sto.model.Service;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.Modal;
import org.example.sto.ui.components.StatCard;
import org.example.sto.ui.components.TableCells;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Прайс-лист услуг сервиса.
 */
public final class ServicesView implements PageView {

    private final AppContext context;
    private final ScrollPane root;

    private final TableView<Service> table = Ui.table();
    private final TextField search = new TextField();
    private final Label foot = Ui.label("", "table-foot");
    private final StatCard countCard = new StatCard("Услуг в прайсе", "активных позиций", "services", "#2463eb", "#e8efff");
    private final StatCard averageCard = new StatCard("Средняя цена", "по прайс-листу", "ruble", "#10b981", "#e7f8f1");
    private final StatCard durationCard = new StatCard("Средняя длительность", "минут на услугу", "clock", "#f59e0b", "#fef5e3");

    private List<Service> services = List.of();

    public ServicesView(AppContext context) {
        this.context = context;

        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());
        Button create = Ui.primary("Новая услуга", Icon.create("plus", 15, Color.WHITE));
        create.setOnAction(event -> openCreate());

        List<Node> headerActions = new java.util.ArrayList<>();
        headerActions.add(refresh);
        if (context.can(Permission.CREATE_SERVICE)) {
            headerActions.add(create);
        }

        search.setPromptText("Название услуги…");
        search.getStyleClass().add("search-input");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icon.create("search", 15), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPrefWidth(320);
        search.textProperty().addListener((obs, old, value) -> applyFilter());

        HBox toolbar = new HBox(12, Ui.label("Прайс-лист", "section-title"), Ui.spacer(), searchBox);
        toolbar.getStyleClass().add("card");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = Ui.card(table, foot);
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox stats = new HBox(14, countCard, averageCard, durationCard);
        HBox.setHgrow(countCard, Priority.ALWAYS);
        HBox.setHgrow(averageCard, Priority.ALWAYS);
        HBox.setHgrow(durationCard, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Справочник", "Услуги", "Прайс-лист и длительность работ",
                        headerActions.toArray(Node[]::new)),
                stats, toolbar, tableCard);
        root = Views.scroll(content);
    }

    private void configureTable() {
        table.setPrefHeight(420);
        table.getColumns().add(Ui.column("Услуга", Service::name, 340));
        table.getColumns().add(Ui.column("Длительность", service ->
                service.durationMin() == null ? "—" : service.durationMin() + " мин", 140));
        table.getColumns().add(TableCells.money("Стоимость", Service::price, 130));

        table.setRowFactory(ignored -> {
            TableRow<Service> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openCreate();
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
                context.catalog()::services,
                loaded -> {
                    services = loaded;
                    renderStats();
                    applyFilter();
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void renderStats() {
        countCard.setValue(String.valueOf(services.size()));
        BigDecimal average = services.isEmpty()
                ? BigDecimal.ZERO
                : services.stream()
                .map(Service::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(services.size()), 0, RoundingMode.HALF_UP);
        averageCard.setValue(Fmt.money(average));
        double avgDuration = services.stream()
                .filter(service -> service.durationMin() != null)
                .mapToInt(Service::durationMin)
                .average()
                .orElse(0);
        durationCard.setValue(Math.round(avgDuration) + " мин");
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Service> filtered = services.stream()
                .filter(service -> query.isBlank() || contains(service.name(), query))
                .toList();
        table.getItems().setAll(filtered);
        foot.setText("Показано " + filtered.size() + " из " + services.size() + " "
                + Fmt.pluralWord(services.size(), "услуги", "услуг", "услуг"));
        if (filtered.isEmpty()) {
            table.setPlaceholder(Ui.empty("services", "Услуги не найдены",
                    "Измените запрос или добавьте новую услугу."));
        }
    }

    private void openCreate() {
        if (!context.can(Permission.CREATE_SERVICE)) {
            context.toast().info("Прайс-лист изменяет только администратор.");
            return;
        }
        TextField name = Ui.input("Например, компьютерная диагностика");
        TextField price = Ui.input("2500");
        TextField duration = Ui.input("45");
        Label error = Ui.label("", "error-text");
        Ui.setShown(error, false);

        Modal modal = new Modal("Новая услуга", "Добавление позиции в прайс-лист", 500);
        Button cancel = Ui.ghost("Отмена", null);
        cancel.setOnAction(event -> modal.hide());
        Button submit = Ui.primary("Добавить услугу", Icon.create("plus", 14, Color.WHITE));
        submit.setDefaultButton(true);

        HBox priceRow = new HBox(12, Ui.field("Стоимость, ₽", price), Ui.field("Длительность, мин", duration));
        HBox.setHgrow(priceRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(priceRow.getChildren().get(1), Priority.ALWAYS);

        VBox form = new VBox(12,
                Ui.field("Наименование", name),
                priceRow,
                error);
        modal.setContent(form);
        modal.setFooter(cancel, submit);
        modal.show(context.overlay());

        submit.setOnAction(event -> {
            String serviceName = name.getText() == null ? "" : name.getText().trim();
            if (serviceName.isBlank()) {
                Ui.showError(error, "Укажите наименование услуги");
                return;
            }
            BigDecimal servicePrice;
            try {
                servicePrice = Fmt.parseMoney(price.getText());
            } catch (NumberFormatException exception) {
                Ui.showError(error, "Стоимость должна быть числом");
                return;
            }
            if (servicePrice == null || servicePrice.signum() < 0) {
                Ui.showError(error, "Укажите неотрицательную стоимость");
                return;
            }
            Integer durationValue;
            try {
                durationValue = Fmt.parseInteger(duration.getText());
            } catch (NumberFormatException exception) {
                Ui.showError(error, "Длительность должна быть целым числом");
                return;
            }
            if (durationValue != null && durationValue <= 0) {
                Ui.showError(error, "Длительность должна быть больше нуля");
                return;
            }

            submit.setDisable(true);
            submit.setText("Сохранение…");
            context.async(
                    () -> context.catalog().createService(serviceName, servicePrice, durationValue),
                    service -> {
                        modal.hide();
                        context.toast().success("Услуга «" + service.name() + "» добавлена");
                        load();
                    },
                    throwable -> {
                        submit.setDisable(false);
                        submit.setText("Добавить услугу");
                        Ui.showError(error, AppContext.errorText(throwable));
                    });
        });
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
