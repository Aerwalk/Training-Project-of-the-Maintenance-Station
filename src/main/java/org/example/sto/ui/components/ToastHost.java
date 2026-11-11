package org.example.sto.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Всплывающие уведомления в правом нижнем углу окна.
 */
public final class ToastHost extends StackPane {

    private final VBox stack = new VBox(9);

    public ToastHost() {
        setPickOnBounds(false);
        setMouseTransparent(true);
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        stack.setPadding(new Insets(0, 24, 22, 0));
        stack.setPickOnBounds(false);
        stack.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        StackPane.setAlignment(stack, Pos.BOTTOM_RIGHT);
        getChildren().add(stack);
    }

    public void success(String message) {
        show("success", message, "check", Color.web("#10b981"));
    }

    public void error(String message) {
        show("error", message, "alert", Color.web("#ef4444"));
    }

    public void info(String message) {
        show("info", message, "spark", Color.web("#2463eb"));
    }

    private void show(String type, String message, String icon, Color color) {
        if (message == null || message.isBlank()) {
            return;
        }
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(340);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox toast = new HBox(10, Icon.create(icon, 18, color), text);
        toast.getStyleClass().addAll("toast", "toast-" + type);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(420);

        stack.getChildren().add(toast);
        while (stack.getChildren().size() > 4) {
            stack.getChildren().remove(0);
        }

        toast.setOpacity(0);
        toast.setTranslateY(12);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
        fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(220), toast);
        slideIn.setToY(0);
        fadeIn.play();
        slideIn.play();

        PauseTransition wait = new PauseTransition(Duration.seconds(4.2));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), toast);
        fadeOut.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(wait, fadeOut);
        sequence.setOnFinished(event -> stack.getChildren().remove(toast));
        sequence.play();
    }
}
