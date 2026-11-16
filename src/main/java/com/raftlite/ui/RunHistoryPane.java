package com.raftlite.ui;

import com.raftlite.persistence.PersistenceException;
import com.raftlite.persistence.SimulationRepository;
import com.raftlite.persistence.SimulationRun;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongPredicate;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Runs stored in the SQLite database, with their leaders and statistics. */
final class RunHistoryPane extends BorderPane {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SimulationRepository repository;
    private final LongPredicate isCurrentRun;

    private final TableView<SimulationRun> runs = new TableView<>();
    private final ListView<String> leaders = new ListView<>();
    private final TextArea statistics = new TextArea();

    RunHistoryPane(SimulationRepository repository, LongPredicate isCurrentRun) {

        this.repository = repository;
        this.isCurrentRun = isCurrentRun;

        TableColumn<SimulationRun, Long> id = new TableColumn<>("Run");
        id.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().runId()));
        EventLogPane.fixWidth(id, 50);

        TableColumn<SimulationRun, String> started = new TableColumn<>("Started");
        started.setCellValueFactory(c -> new ReadOnlyStringWrapper(WHEN.format(c.getValue().startedAt())));
        EventLogPane.fixWidth(started, 170);

        TableColumn<SimulationRun, Integer> size = new TableColumn<>("Nodes");
        size.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().clusterSize()));
        EventLogPane.fixWidth(size, 60);

        TableColumn<SimulationRun, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new ReadOnlyStringWrapper(statusOf(c.getValue())));
        EventLogPane.fixWidth(status, 90);

        TableColumn<SimulationRun, String> description = new TableColumn<>("Description");
        description.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().description()));

        runs.getColumns().setAll(List.of(id, started, size, status, description));
        runs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        runs.setPlaceholder(new Label("No runs stored yet."));
        runs.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, run) -> showDetails(run));

        statistics.setEditable(false);
        leaders.setPrefHeight(110);
        statistics.getStyleClass().add("mono");
        leaders.setPlaceholder(new Label("Select a finished run"));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        Button delete = new Button("Delete run");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(runs.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> deleteSelected());

        HBox toolbar = new HBox(8, refresh, delete);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        VBox details = new VBox(6,
                new Label("Leader of each term"), leaders,
                new Label("Statistics"), statistics);
        details.setPadding(new Insets(0, 0, 0, 10));
        VBox.setVgrow(statistics, javafx.scene.layout.Priority.ALWAYS);

        SplitPane split = new SplitPane(runs, details);
        split.setDividerPositions(0.6);

        setTop(toolbar);
        setCenter(split);
        setPadding(new Insets(10));
    }

    private String statusOf(SimulationRun run) {
        if (run.isFinished()) {
            return "saved";
        }
        return isCurrentRun.test(run.runId()) ? "running" : "incomplete";
    }

    /** Reloads the run list from the database. */
    void reload() {

        Long selectedId = Optional.ofNullable(runs.getSelectionModel().getSelectedItem())
                .map(SimulationRun::runId).orElse(null);

        try {
            runs.getItems().setAll(repository.findAllRuns());
        } catch (PersistenceException e) {
            Dialogs.error("Could not load runs", e);
            return;
        }

        runs.getItems().stream()
                .filter(run -> selectedId != null && run.runId() == selectedId)
                .findFirst()
                .ifPresent(run -> runs.getSelectionModel().select(run));
    }

    private void showDetails(SimulationRun run) {

        leaders.getItems().clear();
        statistics.clear();

        if (run == null) {
            return;
        }

        try {
            Map<Integer, Integer> history = repository.findLeaderHistory(run.runId());
            history.forEach((term, leader) ->
                    leaders.getItems().add("Term " + term + "  →  Node " + leader));

            statistics.setText(repository.findStatistics(run.runId())
                    .map(s -> s.toDisplayString())
                    .orElse(isCurrentRun.test(run.runId())
                            ? "This run is still in progress.\nUse \"Save run\" or start a new run to store it."
                            : "No statistics stored for this run."));

        } catch (PersistenceException e) {
            Dialogs.error("Could not load run " + run.runId(), e);
        }
    }

    private void deleteSelected() {

        SimulationRun run = runs.getSelectionModel().getSelectedItem();

        if (isCurrentRun.test(run.runId())) {
            Dialogs.warning("Run in progress",
                    "Run #" + run.runId() + " is the current run and cannot be deleted.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete run #" + run.runId() + " and all of its events? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete run");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            repository.deleteRun(run.runId());
            reload();
        } catch (PersistenceException e) {
            Dialogs.error("Could not delete run " + run.runId(), e);
        }
    }
}
