package org.example.sto.ui.pages;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Общие контейнеры страниц.
 */
public final class Views {

    private Views() {
    }

    public static VBox page(Node... children) {
        VBox box = new VBox(16, children);
        box.getStyleClass().addAll("page", "page-content");
        return box;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.getStyleClass().add("page-scroll");
        return pane;
    }
}
