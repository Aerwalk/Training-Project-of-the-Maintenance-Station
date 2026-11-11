package org.example.sto.ui.pages;

import javafx.scene.Node;

/**
 * Экран приложения: узел создаётся один раз, данные обновляются в onShow().
 */
public interface PageView {

    Node node();

    void onShow();
}
