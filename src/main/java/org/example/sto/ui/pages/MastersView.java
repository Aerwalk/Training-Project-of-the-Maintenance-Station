package org.example.sto.ui.pages;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.sto.model.Master;
import org.example.sto.ui.AppContext;
import org.example.sto.ui.components.Icon;
import org.example.sto.ui.components.StatCard;
import org.example.sto.ui.components.Ui;
import org.example.sto.ui.util.Fmt;

import java.util.List;
import java.util.Map;

/**
 * Мастера сервиса и их текущая загрузка.
 */
public final class MastersView implements PageView {

    private record MastersData(List<Master> masters, Map<Integer, Integer> workload) {
    }

    private final AppContext context;
    private final ScrollPane root;
    private final FlowPane grid = new FlowPane();

    private final StatCard totalCard = new StatCard("Всего мастеров", "в команде", "masters", "#7c3aed", "#f0eafd");
    private final StatCard activeCard = new StatCard("Активных", "принимают заявки", "check", "#10b981", "#e7f8f1");
    private final StatCard busyCard = new StatCard("Занято заявками", "в работе сейчас", "clock", "#f59e0b", "#fef5e3");

    public MastersView(AppContext context) {
        this.context = context;

        Button refresh = Ui.ghost("Обновить", Icon.create("refresh", 15));
        refresh.setOnAction(event -> load());

        grid.getStyleClass().add("master-grid");
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPrefWrapLength(900);

        HBox stats = new HBox(14, totalCard, activeCard, busyCard);
        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(activeCard, Priority.ALWAYS);
        HBox.setHgrow(busyCard, Priority.ALWAYS);

        VBox content = Views.page(
                Ui.pageHeader("Команда", "Мастера", "Специализации и текущая загрузка", refresh),
                stats, grid);
        root = Views.scroll(content);
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        context.async(
                () -> new MastersData(context.catalog().masters(), context.orders().masterWorkload()),
                data -> {
                    totalCard.setValue(String.valueOf(data.masters().size()));
                    long active = data.masters().stream().filter(Master::active).count();
                    activeCard.setValue(String.valueOf(active));
                    int busy = data.workload().values().stream().mapToInt(Integer::intValue).sum();
                    busyCard.setValue(String.valueOf(busy));
                    render(data);
                },
                error -> context.toast().error(AppContext.errorText(error)));
    }

    private void render(MastersData data) {
        grid.getChildren().clear();
        if (data.masters().isEmpty()) {
            grid.getChildren().add(Ui.empty("masters", "Мастеров пока нет",
                    "Добавьте сотрудников в базу данных."));
            return;
        }
        for (Master master : data.masters()) {
            int workload = data.workload().getOrDefault(master.id(), 0);
            grid.getChildren().add(masterCard(master, workload));
        }
    }

    private VBox masterCard(Master master, int workload) {
        Label avatar = Ui.label(Fmt.initials(master.fullName()), "avatar", "avatar-lg");
        Label name = Ui.label(master.fullName(), "strong");
        Label specialization = Ui.label(Fmt.nullSafe(master.specialization()), "small", "muted");
        VBox person = new VBox(1, name, specialization);
        HBox.setHgrow(person, Priority.ALWAYS);

        Label state = master.active()
                ? Ui.label("активен", "badge", "badge-done")
                : Ui.label("неактивен", "badge", "badge-muted");
        HBox head = new HBox(11, avatar, person, state);
        head.setAlignment(Pos.CENTER_LEFT);

        Label workloadLabel = Ui.label(
                Fmt.plural(workload, "активная заявка", "активные заявки", "активных заявок"), "small");
        Label workloadValue = Ui.label(String.valueOf(workload), "strong");
        HBox workloadRow = new HBox(8, workloadLabel, Ui.spacer(), workloadValue);
        workloadRow.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progress = new ProgressBar(Math.min(1.0, workload / 5.0));
        progress.setMaxWidth(Double.MAX_VALUE);

        Label hint = Ui.label(workload == 0 ? "Свободен для новых заявок" : "Работает с текущими заявками", "timeline-meta");

        VBox card = new VBox(10, head, new javafx.scene.control.Separator(), workloadRow, progress, hint);
        card.getStyleClass().add("master-card");
        card.setPrefWidth(292);
        card.setMinWidth(270);
        card.setMaxWidth(320);
        return card;
    }
}
