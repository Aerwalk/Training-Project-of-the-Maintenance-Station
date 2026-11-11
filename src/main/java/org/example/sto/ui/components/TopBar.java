package org.example.sto.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.ui.Navigation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Верхняя панель: заголовок раздела, дата, поиск и индикатор базы.
 */
public final class TopBar extends HBox {

    private final Label title = Ui.label("Обзор", "page-title");
    private final Label subtitle = Ui.label("", "page-sub");
    private final TextField search = new TextField();
    private final Consumer<String> onSearch;

    public TopBar(Consumer<String> onSearch) {
        this.onSearch = onSearch;
        getStyleClass().add("topbar");

        VBox meta = new VBox(1, title, subtitle);
        meta.getStyleClass().add("topbar-meta");

        search.setPromptText("Номер заявки, клиент, автомобиль…");
        search.getStyleClass().add("search-input");
        search.setMaxWidth(Double.MAX_VALUE);
        search.setOnAction(event -> {
            String query = search.getText() == null ? "" : search.getText().trim();
            search.clear();
            onSearch.accept(query);
        });
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icon.create("search", 15), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);

        Label date = Ui.label(
                LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("ru", "RU"))),
                "small");
        HBox dateChip = new HBox(7, Icon.create("calendar", 14), date);
        dateChip.getStyleClass().add("date-chip");
        dateChip.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label();
        dot.getStyleClass().addAll("db-dot", "db-dot-on");
        HBox dbStatus = new HBox(7, dot, Ui.label("PostgreSQL", "small"));
        dbStatus.getStyleClass().add("db-status");
        dbStatus.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.control.Tooltip.install(
                dbStatus, new javafx.scene.control.Tooltip("Соединение с базой данных установлено"));

        HBox right = new HBox(10, searchBox, dateChip, dbStatus);
        right.getStyleClass().add("topbar-right");
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        getChildren().addAll(meta, Ui.spacer(), right);
        setAlignment(Pos.CENTER_LEFT);
    }

    public void setMeta(Navigation navigation) {
        title.setText(navigation.title());
        subtitle.setText(navigation.subtitle());
    }
}
