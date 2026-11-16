package com.raftlite.ui;

import com.raftlite.monitoring.SimulationStatistics;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Live statistics: key figures, the term over time, and fault/election counts. */
final class StatisticsPane extends HBox {

    private static final int MAX_TERM_POINTS = 240;

    private final Map<String, Label> values = new LinkedHashMap<>();
    private final NumberAxis termAxis = new NumberAxis();

    private final XYChart.Series<Number, Number> termSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> countSeries = new XYChart.Series<>();
    private final Map<String, XYChart.Data<String, Number>> bars = new LinkedHashMap<>();

    StatisticsPane() {

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(3);
        grid.getStyleClass().add("stats-grid");
        grid.getColumnConstraints().addAll(new ColumnConstraints(150), new ColumnConstraints(110));

        String[] names = {
                "Elections started", "Elections won", "Elections failed", "Success rate",
                "Leader changes", "Election timeouts", "Heartbeats sent", "Heartbeats received",
                "Node failures", "Node recoveries", "Messages blocked", "Avg election (ms)"
        };

        for (int row = 0; row < names.length; row++) {
            Label name = new Label(names[row]);
            Label value = new Label("0");
            value.getStyleClass().add("stat-value");
            values.put(names[row], value);
            grid.addRow(row, name, value);
        }

        NumberAxis timeAxis = new NumberAxis();
        timeAxis.setLabel("seconds since run start");
        timeAxis.setForceZeroInRange(false);
        termAxis.setLabel("term");
        termAxis.setAutoRanging(false);
        termAxis.setLowerBound(0);
        termAxis.setUpperBound(3);
        termAxis.setTickUnit(1);
        termAxis.setMinorTickVisible(false);

        LineChart<Number, Number> termChart = new LineChart<>(timeAxis, termAxis);
        termChart.setTitle("Term over time (each step = a new election)");
        termChart.setCreateSymbols(false);
        termChart.setLegendVisible(false);
        termChart.setAnimated(false);
        termChart.getData().add(termSeries);

        CategoryAxis categories = new CategoryAxis();
        NumberAxis countAxis = new NumberAxis();
        countAxis.setMinorTickVisible(false);
        // Counts are whole numbers: hide fractional tick labels such as 0.25.
        countAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number value) {
                double v = value.doubleValue();
                return v == Math.rint(v) ? Long.toString((long) v) : "";
            }

            @Override
            public Number fromString(String text) {
                return Double.parseDouble(text);
            }
        });

        BarChart<String, Number> countChart = new BarChart<>(categories, countAxis);
        countChart.setTitle("Elections and faults");
        countChart.setLegendVisible(false);
        countChart.setAnimated(false);
        countChart.setCategoryGap(18);

        for (String bar : new String[] {"Started", "Won", "Failed", "Timeouts", "Crashes", "Recoveries"}) {
            XYChart.Data<String, Number> data = new XYChart.Data<>(bar, 0);
            bars.put(bar, data);
            countSeries.getData().add(data);
        }

        countChart.getData().add(countSeries);

        HBox.setHgrow(termChart, Priority.ALWAYS);
        HBox.setHgrow(countChart, Priority.ALWAYS);
        termChart.setPrefWidth(500);
        countChart.setPrefWidth(380);

        getChildren().addAll(grid, termChart, countChart);
        setSpacing(16);
        setPadding(new Insets(12));
    }

    void showStatistics(SimulationStatistics s) {

        set("Elections started", s.totalElections());
        set("Elections won", s.successfulElections());
        set("Elections failed", s.failedElections());
        values.get("Success rate").setText(String.format("%.0f%%", s.electionSuccessRate() * 100));
        set("Leader changes", s.leaderChanges());
        set("Election timeouts", s.electionTimeouts());
        set("Heartbeats sent", s.heartbeatsSent());
        set("Heartbeats received", s.heartbeatsReceived());
        set("Node failures", s.nodeFailures());
        set("Node recoveries", s.nodeRecoveries());
        set("Messages blocked", s.messagesBlocked());
        values.get("Avg election (ms)").setText(String.format("%.1f", s.averageElectionDurationMillis()));

        bars.get("Started").setYValue(s.totalElections());
        bars.get("Won").setYValue(s.successfulElections());
        bars.get("Failed").setYValue(s.failedElections());
        bars.get("Timeouts").setYValue(s.electionTimeouts());
        bars.get("Crashes").setYValue(s.nodeFailures());
        bars.get("Recoveries").setYValue(s.nodeRecoveries());
    }

    /** Adds one point to the term chart. */
    void addTermSample(double seconds, int term) {

        termSeries.getData().add(new XYChart.Data<>(seconds, term));
        termAxis.setUpperBound(Math.max(3, term + 1));

        if (termSeries.getData().size() > MAX_TERM_POINTS) {
            termSeries.getData().remove(0);
        }
    }

    void clear() {
        termSeries.getData().clear();
    }

    private void set(String name, long value) {
        values.get(name).setText(Long.toString(value));
    }
}
