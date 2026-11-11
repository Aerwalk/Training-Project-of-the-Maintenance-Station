package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.User;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Экран входа с демонстрационными учётными записями.
 */
public final class LoginView extends HBox {

    private final AppContext context;
    private final Runnable onSuccess;

    private final TextField username = Ui.input("Введите логин");
    private final PasswordField password = new PasswordField();
    private final TextField visiblePassword = new TextField();
    private final Label error = new Label();
    private final Button submit = Ui.primary("Войти в систему", Icon.create("arrow", 16, Color.WHITE));
    private boolean passwordVisible;

    public LoginView(AppContext context, Runnable onSuccess) {
        this.context = context;
        this.onSuccess = onSuccess;
        getStyleClass().add("login-root");
        setMinSize(900, 620);

        VBox brand = buildBrandPanel();
        StackPane form = buildFormPanel();
        getChildren().addAll(brand, form);
        HBox.setHgrow(form, Priority.ALWAYS);
    }

    private VBox buildBrandPanel() {
        Label logo = new Label();
        logo.getStyleClass().add("login-logo");
        logo.setGraphic(Icon.create("car", 25, Color.WHITE));
        VBox brandText = new VBox(1,
                Ui.label("СТО · CRM", "brand-name"),
                Ui.label("система управления автосервисом", "brand-caption"));
        HBox brand = new HBox(12, logo, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = Ui.label("УЧЕБНЫЙ ПРОЕКТ · ПМ.01", "eyebrow");
        eyebrow.setStyle("-fx-text-fill: #7fb0ff;");
        Label hero = Ui.label("Весь автосервис\nв одном окне", "login-hero");
        Label heroSub = Ui.label(
                "Клиенты, автомобили, прайс-лист, заявки и журнал изменений — "
                        + "данные хранятся в PostgreSQL и обновляются в реальном времени.",
                "login-hero-sub");
        heroSub.setWrapText(true);
        heroSub.setMaxWidth(430);

        VBox features = new VBox(12,
                feature("clients", "Клиенты и автопарк", "Единая карточка владельца и всех его машин"),
                feature("orders", "Полный цикл заявки", "От приёма автомобиля до закрытия и итоговой суммы"),
                feature("history", "Журнал статусов", "Каждое изменение фиксируется триггерами базы данных"));

        VBox footer = new VBox(5,
                Ui.label(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("ru", "RU"))),
                        "login-date"),
                Ui.label("PostgreSQL · " + context.dbSettings().url(), "login-date"));

        VBox panel = new VBox(24, brand, Ui.spacer(), eyebrow, hero, heroSub, features, Ui.spacer(), footer);
        panel.getStyleClass().add("login-left");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private HBox feature(String icon, String title, String text) {
        Label iconBox = new Label();
        iconBox.getStyleClass().add("login-feature-icon");
        iconBox.setGraphic(Icon.create(icon, 15, Color.web("#8db4f4")));
        Label titleLabel = Ui.label(title, "strong");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12.5px;");
        VBox textBox = new VBox(1, titleLabel, Ui.label(text, "login-feature-text"));
        HBox row = new HBox(11, iconBox, textBox);
        row.getStyleClass().add("login-feature");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private StackPane buildFormPanel() {
        Label eyebrow = Ui.label("ВХОД В СИСТЕМУ", "eyebrow");
        Label title = Ui.label("С возвращением", "login-title");
        Label subtitle = Ui.label("Войдите, чтобы продолжить работу с заявками.", "login-sub");
        subtitle.setWrapText(true);

        username.setPrefHeight(38);
        password.setPromptText("Введите пароль");
        password.setPrefHeight(38);
        password.getStyleClass().add("text-field");
        visiblePassword.setPromptText("Введите пароль");
        visiblePassword.setPrefHeight(38);
        visiblePassword.setManaged(false);
        visiblePassword.setVisible(false);
        StackPane passwordStack = new StackPane(password, visiblePassword);
        passwordStack.setAlignment(Pos.CENTER_LEFT);

        Button eye = Ui.iconButton("eye", "Показать пароль");
        eye.setOnAction(event -> togglePassword(eye));
        HBox passwordBox = new HBox(passwordStack, eye);
        passwordBox.getStyleClass().add("password-box");
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);
        passwordStack.setMaxWidth(Double.MAX_VALUE);

        error.getStyleClass().add("login-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        Ui.setShown(error, false);

        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setPrefHeight(40);
        submit.setOnAction(event -> login());
        username.setOnAction(event -> login());
        password.setOnAction(event -> login());
        visiblePassword.setOnAction(event -> login());

        HBox chips = new HBox(6,
                demoChip("admin", "admin123"),
                demoChip("manager", "manager123"),
                demoChip("master", "master123"),
                demoChip("client", "client123"));
        chips.getStyleClass().add("login-chips");

        VBox card = new VBox(12,
                eyebrow, title, subtitle,
                Ui.field("Логин", username),
                Ui.field("Пароль", passwordBox),
                error,
                submit,
                new javafx.scene.control.Separator(),
                Ui.label("Демо-доступы — нажмите, чтобы подставить", "hint"),
                chips,
                Ui.label("Пароли хранятся в PostgreSQL в виде bcrypt-хешей.", "hint"));
        card.getStyleClass().add("login-card");

        StackPane panel = new StackPane(card);
        panel.getStyleClass().add("login-right");
        // Панель должна занимать всё доступное место, карточка — центрироваться.
        panel.setMinWidth(420);
        return panel;
    }

    private Button demoChip(String login, String pass) {
        Button chip = new Button(login);
        chip.getStyleClass().add("login-chip");
        chip.setOnAction(event -> {
            username.setText(login);
            password.setText(pass);
            visiblePassword.setText(pass);
            Ui.showError(error, "");
            username.requestFocus();
        });
        return chip;
    }

    private void togglePassword(Button eye) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            visiblePassword.setText(password.getText());
            visiblePassword.setManaged(true);
            visiblePassword.setVisible(true);
            password.setManaged(false);
            password.setVisible(false);
            visiblePassword.requestFocus();
            eye.setGraphic(Icon.create("eyeOff", 16));
        } else {
            password.setText(visiblePassword.getText());
            password.setManaged(true);
            password.setVisible(true);
            visiblePassword.setManaged(false);
            visiblePassword.setVisible(false);
            password.requestFocus();
            eye.setGraphic(Icon.create("eye", 16));
        }
    }

    private void login() {
        String login = username.getText() == null ? "" : username.getText().trim();
        String pass = passwordVisible ? visiblePassword.getText() : password.getText();
        if (login.isBlank() || pass == null || pass.isBlank()) {
            Ui.showError(error, "Введите логин и пароль");
            return;
        }

        submit.setDisable(true);
        submit.setText("Проверка…");
        Ui.showError(error, "");

        context.async(
                () -> context.auth().login(login, pass.toCharArray()),
                (User user) -> {
                    submit.setDisable(false);
                    submit.setText("Войти в систему");
                    context.setUser(user);
                    password.clear();
                    visiblePassword.clear();
                    onSuccess.run();
                },
                throwable -> {
                    submit.setDisable(false);
                    submit.setText("Войти в систему");
                    Ui.showError(error, AppContext.errorText(throwable));
                    password.clear();
                    visiblePassword.clear();
                    password.requestFocus();
                });
    }
}
