package org.example.sto.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.ToastHost;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.pages.LoginView;

/**
 * JavaFX-приложение: подключение к БД, вход и основное окно.
 */
public final class StoFxApp extends Application {

    private AppContext context;
    private StackPane root;
    private ToastHost toast;

    @Override
    public void start(Stage stage) {
        context = UiLauncher.context();
        if (context == null) {
            Platform.exit();
            return;
        }
        context.setStage(stage);
        toast = context.toast();
        context.setLogoutHandler(this::showLogin);

        root = new StackPane();
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, 1280, 820);
        String css = StoFxApp.class.getResource("/css/sto.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("СТО · управление автосервисом");
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.setOnCloseRequest(event -> context.shutdown());
        stage.show();
        stage.centerOnScreen();

        showLoading();
        context.async(context::ping, version -> showLogin(), this::showDatabaseError);
    }

    private void showLoading() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(42, 42);
        Label title = Ui.label("Подключение к PostgreSQL", "section-title");
        Label sub = Ui.label(context.dbSettings().toSafeString(), "page-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(430);
        Label hint = Ui.label("Проверяем доступность контейнера и пула соединений…", "hint");

        VBox card = new VBox(12, indicator, title, sub, hint);
        card.getStyleClass().add("loading-card");
        card.setAlignment(Pos.CENTER);

        StackPane wrapper = new StackPane(card);
        wrapper.setAlignment(Pos.CENTER);
        setContent(wrapper);
    }

    private void showDatabaseError(Throwable error) {
        Label icon = new Label();
        icon.getStyleClass().add("empty-icon");
        icon.setGraphic(Icon.create("database", 22, Color.web("#ef4444")));

        Label title = Ui.label("Нет подключения к базе данных", "page-title");
        Label message = Ui.label(AppContext.errorText(error), "page-sub");
        message.setWrapText(true);
        message.setMaxWidth(500);
        Label hint = Ui.label(
                "Проверьте, что контейнер запущен: docker compose up -d. "
                        + "Параметры подключения задаются в application.properties "
                        + "или переменными STO_DB_URL, STO_DB_USERNAME, STO_DB_PASSWORD.",
                "hint");
        hint.setWrapText(true);
        hint.setMaxWidth(500);

        Button retry = Ui.primary("Повторить подключение", Icon.create("refresh", 15, Color.WHITE));
        retry.setOnAction(event -> {
            showLoading();
            context.async(context::ping, version -> showLogin(), this::showDatabaseError);
        });
        Button exit = Ui.ghost("Закрыть приложение", null);
        exit.setOnAction(event -> Platform.exit());

        HBox actions = new HBox(9, retry, exit);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, icon, title, message, hint, actions);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(560);
        StackPane wrapper = new StackPane(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new javafx.geometry.Insets(40));
        setContent(wrapper);
    }

    private void showLogin() {
        LoginView login = new LoginView(context, this::showShell);
        setContent(login);
    }

    private void showShell() {
        Shell shell = new Shell(context);
        setContent(shell);
    }

    private void setContent(Node node) {
        root.getChildren().setAll(node, toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }
}
