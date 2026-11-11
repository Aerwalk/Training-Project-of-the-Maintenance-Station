package org.example.sto.ui.components;

import javafx.scene.control.Label;
import org.example.sto.ui.util.Fmt;

public final class StatusBadge {

    private StatusBadge() {
    }

    public static Label create(String status) {
        Label label = new Label(status == null ? "—" : status);
        label.getStyleClass().addAll("badge", "badge-" + Fmt.statusClass(status));
        return label;
    }
}
