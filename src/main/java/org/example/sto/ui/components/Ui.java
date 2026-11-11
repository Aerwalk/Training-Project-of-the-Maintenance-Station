package org.example.sto.ui.components;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Небольшие фабрики элементов, чтобы экраны собирались единообразно.
 */
public final class Ui {

    private Ui() {
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Label fieldLabel(String text) {
        return label(text, "field-label");
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox col(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Button button(String text, String styleClass, Node icon) {
        Button button = new Button(text);
        button.getStyleClass().addAll("btn", styleClass);
        if (icon != null) {
            button.setGraphic(icon);
        }
        return button;
    }

    public static Button primary(String text, Node icon) {
        return button(text, "btn-primary", icon);
    }

    public static Button ghost(String text, Node icon) {
        return button(text, "btn-ghost", icon);
    }

    public static Button soft(String text, Node icon) {
        return button(text, "btn-soft", icon);
    }

    public static Button danger(String text, Node icon) {
        return button(text, "btn-danger", icon);
    }

    public static Button iconButton(String iconName, String tooltip) {
        Button button = new Button();
        button.getStyleClass().add("icon-btn");
        button.setGraphic(Icon.create(iconName, 16));
        if (tooltip != null) {
            button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        }
        return button;
    }

    public static TextField input(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    public static HBox searchField(String prompt, UnaryOperator<String> onSearch) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        input.getStyleClass().add("search-input");
        input.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(input, Priority.ALWAYS);
        input.textProperty().addListener((obs, old, value) -> onSearch.apply(value == null ? "" : value));

        HBox box = new HBox(8, Icon.create("search", 15), input);
        box.getStyleClass().add("search-box");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox card(Node... children) {
        VBox card = new VBox(14, children);
        card.getStyleClass().add("card");
        return card;
    }

    public static VBox card(String title, String subtitle, Node... children) {
        VBox head = new VBox(3, label(title, "card-title"));
        if (subtitle != null && !subtitle.isBlank()) {
            head.getChildren().add(label(subtitle, "card-sub"));
        }
        VBox card = card(children);
        card.getChildren().add(0, head);
        return card;
    }

    public static HBox pageHeader(String eyebrow, String title, String subtitle, Node... actions) {
        VBox meta = new VBox(4,
                label(eyebrow, "eyebrow"),
                label(title, "page-title"),
                label(subtitle, "page-sub"));
        HBox header = new HBox(16, meta, spacer());
        header.setAlignment(Pos.CENTER_LEFT);
        if (actions.length > 0) {
            HBox actionBox = new HBox(8, actions);
            actionBox.setAlignment(Pos.CENTER_RIGHT);
            header.getChildren().add(actionBox);
        }
        return header;
    }

    public static VBox field(String label, Node control) {
        VBox box = new VBox(5, fieldLabel(label), control);
        VBox.setVgrow(control, Priority.NEVER);
        return box;
    }

    public static <T> ComboBox<T> combo(List<T> items, StringConverter<T> converter, String prompt) {
        ObservableList<T> list = FXCollections.observableArrayList(items);
        ComboBox<T> combo = new ComboBox<>(list);
        combo.setConverter(converter);
        combo.setPromptText(prompt);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    public static <S> TableView<S> table() {
        TableView<S> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(label("Данных пока нет", "empty-text"));
        return table;
    }

    public static <S> TableColumn<S, String> column(String title, Function<S, String> value, double width) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        if (width > 0) {
            column.setPrefWidth(width);
        }
        return column;
    }

    public static VBox empty(String icon, String title, String text) {
        VBox box = new VBox(7);
        box.getStyleClass().add("empty");
        box.setAlignment(Pos.CENTER);
        javafx.scene.layout.StackPane iconBox = new javafx.scene.layout.StackPane(Icon.create(icon, 20));
        iconBox.getStyleClass().add("empty-icon");
        box.getChildren().addAll(iconBox, label(title, "empty-title"), label(text, "empty-text"));
        return box;
    }

    public static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    public static void showError(Label label, String text) {
        label.setText(text == null ? "" : text);
        setShown(label, text != null && !text.isBlank());
    }
}
