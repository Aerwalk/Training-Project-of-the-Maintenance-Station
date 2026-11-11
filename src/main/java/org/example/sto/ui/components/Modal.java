package org.example.sto.ui.components;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Модальное окно с затемнением фона и закрытием по Esc.
 */
public final class Modal extends StackPane {

    private final VBox card = new VBox(14);
    private final VBox content = new VBox(14);
    private final HBox footer = new HBox(9);
    private final ScrollPane scroll = new ScrollPane(content);

    private StackPane host;
    private EventHandler<KeyEvent> escapeHandler;
    private Runnable onHide;

    public Modal(String title, String subtitle, double width) {
        getStyleClass().add("modal-overlay");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label titleLabel = Ui.label(title, "modal-title");
        Label subLabel = Ui.label(subtitle == null ? "" : subtitle, "modal-sub");
        Ui.setShown(subLabel, subtitle != null && !subtitle.isBlank());
        VBox headText = new VBox(2, titleLabel, subLabel);

        Button close = Ui.iconButton("close", "Закрыть");
        close.setOnAction(event -> hide());
        HBox header = new HBox(12, headText, Ui.spacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        content.setFillWidth(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("modal-scroll");
        scroll.setMaxHeight(560);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        footer.getStyleClass().add("modal-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        card.getStyleClass().add("modal-card");
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.getChildren().addAll(header, scroll);

        setAlignment(Pos.CENTER);
        getChildren().add(card);
    }

    public Modal setContent(Node node) {
        content.getChildren().setAll(node);
        return this;
    }

    public Modal setFooter(Node... nodes) {
        footer.getChildren().setAll(nodes);
        if (!card.getChildren().contains(footer) && nodes.length > 0) {
            card.getChildren().add(footer);
        }
        if (nodes.length == 0) {
            card.getChildren().remove(footer);
        }
        return this;
    }

    public Modal onHide(Runnable onHide) {
        this.onHide = onHide;
        return this;
    }

    public void show(StackPane host) {
        this.host = host;
        if (!host.getChildren().contains(this)) {
            host.getChildren().add(this);
        }
        if (host.getScene() != null) {
            escapeHandler = event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    hide();
                }
            };
            host.getScene().addEventFilter(KeyEvent.KEY_PRESSED, escapeHandler);
        }
        requestFocus();
    }

    public void hide() {
        if (host != null && host.getScene() != null && escapeHandler != null) {
            host.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, escapeHandler);
        }
        escapeHandler = null;
        if (host != null) {
            host.getChildren().remove(this);
        }
        if (onHide != null) {
            onHide.run();
        }
    }

    public static Modal confirm(StackPane host, String title, String message,
                                String confirmText, Runnable action) {
        Modal modal = new Modal(title, "Действие нельзя отменить", 440);
        Label text = new Label(message);
        text.setWrapText(true);
        text.getStyleClass().add("page-sub");
        modal.setContent(text);

        Button cancel = Ui.ghost("Отмена", null);
        cancel.setOnAction(event -> modal.hide());
        Button confirm = Ui.danger(confirmText, null);
        confirm.setOnAction(event -> {
            modal.hide();
            action.run();
        });
        modal.setFooter(cancel, confirm);
        modal.show(host);
        return modal;
    }
}
