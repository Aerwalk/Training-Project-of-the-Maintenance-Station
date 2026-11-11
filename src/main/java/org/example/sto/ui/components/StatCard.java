package org.example.sto.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public final class StatCard extends VBox {

    private final Label value = new Label("—");
    private final Label note = new Label("");

    public StatCard(String label, String noteText, String iconName, String colorHex, String softHex) {
        super(12);
        getStyleClass().add("kpi-card");

        Label iconBox = new Label();
        iconBox.getStyleClass().add("kpi-icon");
        iconBox.setGraphic(Icon.create(iconName, 18, Color.web(colorHex)));
        iconBox.setStyle("-fx-background-color: " + softHex + ";");

        value.getStyleClass().add("kpi-value");
        Label title = new Label(label);
        title.getStyleClass().add("kpi-label");
        note.getStyleClass().add("kpi-note");
        note.setText(noteText == null ? "" : noteText);

        HBox top = new HBox(10, iconBox, Ui.spacer(), note);
        top.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(top, value, title);
    }

    public void setValue(String text) {
        value.setText(text);
    }
}
