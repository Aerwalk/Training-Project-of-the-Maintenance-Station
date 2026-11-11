package org.example.sto.ui.components;

import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;
import org.example.sto.ui.util.Fmt;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Готовые ячейки таблиц: бейджи, суммы, номера и кнопки действий.
 */
public final class TableCells {

    private TableCells() {
    }

    public static <S> TableColumn<S, String> badge(String title, Function<S, String> value, double width) {
        TableColumn<S, String> column = Ui.column(title, value, width);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : StatusBadge.create(item));
            }
        });
        return column;
    }

    public static <S> TableColumn<S, String> chip(String title, Function<S, String> value, double width) {
        TableColumn<S, String> column = Ui.column(title, value, width);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label chip = new Label(item);
                    chip.getStyleClass().addAll("chip", "chip-plate");
                    setGraphic(chip);
                }
            }
        });
        return column;
    }

    public static <S> TableColumn<S, BigDecimal> money(String title, Function<S, BigDecimal> value, double width) {
        TableColumn<S, BigDecimal> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Fmt.money(item));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
            }
        });
        return column;
    }

    public static <S> TableColumn<S, Void> action(String title, Function<S, Node> factory, double width) {
        TableColumn<S, Void> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(factory.apply(getTableView().getItems().get(getIndex())));
                }
            }
        });
        return column;
    }
}
