package org.example.sto.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.User;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.Navigation;
import org.example.sto.ui.util.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Тёмное боковое меню с профилем пользователя.
 */
public final class Sidebar extends VBox {

    private record NavItem(Navigation navigation, Button button, StackPane icon) {
    }

    private final AppContext context;
    private final List<NavItem> items = new ArrayList<>();

    public Sidebar(AppContext context, Consumer<Navigation> onNavigate) {
        this.context = context;
        getStyleClass().add("sidebar");

        Label logoBadge = new Label();
        logoBadge.getStyleClass().add("brand-badge");
        logoBadge.setGraphic(Icon.create("car", 22, Color.WHITE));
        VBox brandText = new VBox(1,
                Ui.label("СТО · CRM", "brand-name"),
                Ui.label("управление автосервисом", "brand-caption"));
        HBox brand = new HBox(11, logoBadge, brandText);
        brand.getStyleClass().add("brand");
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox nav = new VBox(4, Ui.label("РАЗДЕЛЫ", "nav-title"));
        nav.getStyleClass().add("nav");
        for (Navigation navigation : Navigation.visibleFor(context.permissions())) {
            StackPane icon = Icon.create(navigation.icon(), 18, Color.web("#aab8cc"));
            Button button = new Button(navigation.title());
            button.setGraphic(icon);
            button.getStyleClass().add("nav-btn");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> onNavigate.accept(navigation));
            items.add(new NavItem(navigation, button, icon));
            nav.getChildren().add(button);
        }

        User user = context.user();
        Label avatar = new Label(user == null ? "?" : Fmt.initials(user.fullName()));
        avatar.getStyleClass().addAll("avatar", "avatar-dark");
        VBox userText = new VBox(1,
                Ui.label(user == null ? "Гость" : user.fullName(), "user-name"),
                Ui.label(user == null ? "—" : Fmt.role(user.role()), "user-role"));
        Button logout = new Button();
        logout.getStyleClass().add("logout-btn");
        logout.setGraphic(Icon.create("logout", 17, Color.web("#8798b0")));
        logout.setTooltip(new javafx.scene.control.Tooltip("Выйти из аккаунта"));
        logout.setOnAction(event -> context.logout());

        HBox userCard = new HBox(9, avatar, userText, Ui.spacer(), logout);
        userCard.getStyleClass().add("user-card");
        userCard.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(userText, Priority.ALWAYS);

        VBox footer = new VBox(8,
                Ui.label("PostgreSQL 16 · локальный стенд", "brand-caption"),
                userCard);
        footer.getStyleClass().add("sidebar-footer");

        getChildren().addAll(brand, nav, Ui.spacer(), footer);
        VBox.setVgrow(nav, Priority.NEVER);
    }

    public void setActive(Navigation active) {
        for (NavItem item : items) {
            boolean selected = item.navigation() == active;
            item.button().getStyleClass().remove("nav-btn-active");
            if (selected) {
                item.button().getStyleClass().add("nav-btn-active");
            }
            Icon.recolor(item.icon(), selected ? Color.WHITE : Color.web("#aab8cc"));
        }
    }
}
