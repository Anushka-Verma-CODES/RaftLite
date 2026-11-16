package com.raftlite.ui;

import com.raftlite.monitoring.SimulationEvent;
import com.raftlite.monitoring.SimulationEventType;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Live, filterable table of the simulation's event log. */
final class EventLogPane extends BorderPane {

    private static final int MAX_ROWS = 3000;
    private static final String ALL_TYPES = "All event types";

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final Set<SimulationEventType> HEARTBEATS =
            EnumSet.of(SimulationEventType.HEARTBEAT_SENT, SimulationEventType.HEARTBEAT_RECEIVED);

    private final ObservableList<SimulationEvent> rows = FXCollections.observableArrayList();
    private final FilteredList<SimulationEvent> filtered = new FilteredList<>(rows);
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final CheckBox showHeartbeats = new CheckBox("Show heartbeats");
    private final Label count = new Label();

    EventLogPane() {

        TableView<SimulationEvent> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No events yet. Start an election to begin."));

        TableColumn<SimulationEvent, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(TIME.format(c.getValue().getTimestamp())));
        fixWidth(time, 100);

        TableColumn<SimulationEvent, String> type = new TableColumn<>("Event");
        type.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getType().name()));
        fixWidth(type, 170);

        TableColumn<SimulationEvent, Integer> node = new TableColumn<>("Node");
        node.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNodeId()));
        fixWidth(node, 55);

        TableColumn<SimulationEvent, String> term = new TableColumn<>("Term");
        term.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().hasTerm() ? Integer.toString(c.getValue().getTerm()) : "–"));
        fixWidth(term, 55);

        TableColumn<SimulationEvent, String> description = new TableColumn<>("Description");
        description.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDescription()));

        table.getColumns().setAll(List.of(time, type, node, term, description));

        // Colour important rows so they stand out among the traffic.
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(SimulationEvent event, boolean empty) {
                super.updateItem(event, empty);
                getStyleClass().removeAll("row-leader", "row-fault", "row-election");

                if (empty || event == null) {
                    return;
                }

                switch (event.getType()) {
                    case LEADER_ELECTED, LEADER_CHANGED, NODE_RECOVERED ->
                            getStyleClass().add("row-leader");
                    case NODE_FAILED, ELECTION_FAILED, LINK_DISCONNECTED ->
                            getStyleClass().add("row-fault");
                    case ELECTION_STARTED, ELECTION_TIMEOUT ->
                            getStyleClass().add("row-election");
                    default -> { }
                }
            }
        });

        typeFilter.getItems().add(ALL_TYPES);
        for (SimulationEventType t : SimulationEventType.values()) {
            typeFilter.getItems().add(t.name());
        }
        typeFilter.setValue(ALL_TYPES);
        typeFilter.setOnAction(e -> applyFilter());
        showHeartbeats.setOnAction(e -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, new Label("Filter:"), typeFilter, showHeartbeats, spacer, count);
        toolbar.setPadding(new Insets(0, 0, 8, 0));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        setTop(toolbar);
        setCenter(table);
        setPadding(new Insets(10));

        applyFilter();
    }

    static void fixWidth(TableColumn<?, ?> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
    }

    /** Appends events that were logged since the last call. */
    void append(List<SimulationEvent> newEvents) {

        if (newEvents.isEmpty()) {
            return;
        }

        rows.addAll(newEvents);

        if (rows.size() > MAX_ROWS) {
            rows.remove(0, rows.size() - MAX_ROWS);
        }

        updateCount();
    }

    void clear() {
        rows.clear();
        updateCount();
    }

    private void applyFilter() {

        String selected = typeFilter.getValue();

        filtered.setPredicate(event -> {

            if (!ALL_TYPES.equals(selected)) {
                return event.getType().name().equals(selected);
            }

            return showHeartbeats.isSelected() || !HEARTBEATS.contains(event.getType());
        });

        updateCount();
    }

    private void updateCount() {
        count.setText(filtered.size() + " shown / " + rows.size() + " events");
    }
}
