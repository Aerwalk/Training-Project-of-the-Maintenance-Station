package org.example.sto.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

import java.util.List;

/**
 * Кольцевая диаграмма распределения заявок по статусам.
 */
public final class DonutChart extends StackPane {

    public record Slice(String name, long count, Color color) {
    }

    private final Group segments = new Group();
    private final Label total = new Label("0");
    private final Label caption = new Label("заявок");

    public DonutChart() {
        setMinSize(190, 190);
        setPrefSize(190, 190);
        setMaxSize(190, 190);

        total.setStyle("-fx-font-size: 27px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        caption.getStyleClass().add("muted");
        VBox center = new VBox(1, total, caption);
        center.setAlignment(Pos.CENTER);

        getChildren().addAll(segments, center);
        setData(List.of());
    }

    public void setData(List<Slice> slices) {
        segments.getChildren().clear();

        Circle background = new Circle(0, 0, 68);
        background.setFill(Color.TRANSPARENT);
        background.setStroke(Color.web("#eef2f8"));
        background.setStrokeWidth(20);
        segments.getChildren().add(background);

        long sum = slices.stream().mapToLong(Slice::count).sum();
        total.setText(String.valueOf(sum));
        caption.setText(org.example.sto.ui.util.Fmt.pluralWord(sum, "заявка", "заявки", "заявок"));
        if (sum == 0) {
            return;
        }

        double consumed = 0;
        for (Slice slice : slices) {
            if (slice.count() <= 0) {
                continue;
            }
            double fraction = slice.count() / (double) sum;
            double start = 90 - consumed * 360;
            double extent = -fraction * 360;
            if (slices.stream().filter(item -> item.count() > 0).count() > 1) {
                extent += 1.2;
            }
            Arc arc = new Arc(0, 0, 68, 68, start, extent);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(slice.color());
            arc.setStrokeWidth(20);
            arc.setStrokeLineCap(StrokeLineCap.BUTT);
            segments.getChildren().add(arc);
            consumed += fraction;
        }
    }
}
