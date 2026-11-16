package com.raftlite.ui;

import com.raftlite.NodeState;
import com.raftlite.RaftCluster;
import com.raftlite.RaftNode;
import com.raftlite.monitoring.EventLogger;
import com.raftlite.persistence.DatabaseManager;
import com.raftlite.persistence.PersistenceException;
import com.raftlite.persistence.SimulationRepository;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * JavaFX dashboard for RaftLite.
 *
 * Shows the cluster live, lets the user inject faults with buttons, and
 * displays the event log, statistics and the runs stored in SQLite.
 *
 * Run with {@code mvn javafx:run}. An optional first argument sets the
 * database file (default {@value DatabaseManager#DEFAULT_DATABASE_FILE}).
 */
public final class DashboardApp extends Application {

    private static final int DEFAULT_CLUSTER_SIZE = 5;

    // Database
    private String databaseFile;
    private DatabaseManager database;
    private SimulationRepository repository;
    private PersistenceException startupError;

    // Current run
    private RaftCluster cluster;
    private UiEventBridge bridge;
    private long runId = -1;
    private boolean runSaved;
    private long runStartMillis;
    private int eventsShown;
    private int selectedNodeId = 1;

    // Views
    private final ClusterView clusterView = new ClusterView();
    private final EventLogPane eventLog = new EventLogPane();
    private final StatisticsPane statisticsPane = new StatisticsPane();
    private RunHistoryPane historyPane;

    // Controls
    private final ComboBox<Integer> nodeChoice = new ComboBox<>();
    private final ComboBox<Integer> linkFrom = new ComboBox<>();
    private final ComboBox<Integer> linkTo = new ComboBox<>();
    private final ToggleButton heartbeatToggle = new ToggleButton("▶  Start heartbeats");
    private final Spinner<Integer> clusterSize = new Spinner<>(3, 9, DEFAULT_CLUSTER_SIZE);

    // Status bar
    private final Label runLabel = new Label();
    private final Label leaderLabel = new Label();
    private final Label termLabel = new Label();
    private final Label dbLabel = new Label();

    private Timeline refresher;
    private int tick;

    public static void main(String[] args) {
        launch(args);
    }

    // ---------------------------------------------------------------
    // Lifecycle: init() -> start() -> stop()
    // ---------------------------------------------------------------

    @Override
    public void init() {

        List<String> args = getParameters().getRaw();
        databaseFile = args.isEmpty() ? DatabaseManager.DEFAULT_DATABASE_FILE : args.get(0);

        try {
            database = new DatabaseManager(databaseFile);
            repository = new SimulationRepository(database);
        } catch (PersistenceException e) {
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) {

        if (startupError != null) {
            Dialogs.error("Could not open database " + databaseFile, startupError);
            Platform.exit();
            return;
        }

        historyPane = new RunHistoryPane(repository, id -> id == runId && !runSaved);

        TabPane tabs = new TabPane(
                new Tab("Event log", eventLog),
                new Tab("Statistics", statisticsPane),
                new Tab("Run history (database)", historyPane));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            if (tab != null && tab.getContent() == historyPane) {
                historyPane.reload();
            }
        });

        HBox.setHgrow(clusterView, Priority.ALWAYS);
        HBox top = new HBox(clusterView, buildControls());

        SplitPane split = new SplitPane(top, tabs);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);

        BorderPane root = new BorderPane(split);
        root.setTop(buildHeader());

        clusterView.setOnNodeClicked(this::selectNode);

        Scene scene = new Scene(root, 1280, 900);
        scene.getStylesheets().add(
                DashboardApp.class.getResource("dashboard.css").toExternalForm());

        stage.setTitle("RaftLite Dashboard – Raft leader election simulator");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.setScene(scene);

        startNewRun(DEFAULT_CLUSTER_SIZE);

        refresher = new Timeline(new KeyFrame(Duration.millis(100), e -> onTick()));
        refresher.setCycleCount(Timeline.INDEFINITE);
        refresher.play();

        stage.show();
    }

    @Override
    public void stop() {

        if (refresher != null) {
            refresher.stop();
        }

        try {
            finishCurrentRun();
        } catch (PersistenceException e) {
            System.err.println("Could not save the last run: " + e.getMessage());
        }

        if (database != null) {
            database.close();
        }
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private HBox buildHeader() {

        Label title = new Label("RaftLite");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Raft leader election simulator");
        subtitle.getStyleClass().add("app-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        for (Label label : new Label[] {title, runLabel, leaderLabel, termLabel, dbLabel}) {
            label.setMinWidth(Region.USE_PREF_SIZE);   // never truncate to "..."
        }

        for (Label label : new Label[] {runLabel, leaderLabel, termLabel, dbLabel}) {
            label.getStyleClass().add("status-chip");
        }

        dbLabel.setTooltip(new Tooltip(new java.io.File(databaseFile).getAbsolutePath()));

        HBox header = new HBox(12, title, subtitle, spacer, runLabel, leaderLabel, termLabel, dbLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private ScrollPane buildControls() {

        // --- Selected node ------------------------------------------
        nodeChoice.setOnAction(e -> {
            if (nodeChoice.getValue() != null) {
                selectNode(nodeChoice.getValue());
            }
        });

        Button elect = button("Start election", "Make this node a candidate now", this::startElection);
        elect.getStyleClass().add("primary");
        Button crash = button("Crash", "Stop this node: it no longer sends or receives", this::crash);
        crash.getStyleClass().add("danger");
        Button recover = button("Recover", "Restart a crashed node as a follower", this::recover);
        Button isolate = button("Isolate", "Cut every link of this node (network partition)", this::isolate);
        Button reconnect = button("Reconnect", "Restore every link of this node", this::reconnect);

        GridPane nodeButtons = new GridPane();
        nodeButtons.setHgap(6);
        nodeButtons.setVgap(6);
        nodeButtons.add(elect, 0, 0, 2, 1);
        nodeButtons.addRow(1, crash, recover);
        nodeButtons.addRow(2, isolate, reconnect);
        stretch(elect, crash, recover, isolate, reconnect);

        VBox nodeBox = new VBox(8, new Label("Node (or click it in the view)"), nodeChoice, nodeButtons);
        nodeChoice.setMaxWidth(Double.MAX_VALUE);

        // --- Single link -------------------------------------------
        Button cut = button("Cut link", "Messages between the two nodes are dropped", this::cutLink);
        Button restore = button("Restore link", "Reconnect the two nodes", this::restoreLink);
        stretch(cut, restore, linkFrom, linkTo);

        GridPane linkGrid = new GridPane();
        linkGrid.setHgap(6);
        linkGrid.setVgap(6);
        linkGrid.addRow(0, linkFrom, linkTo);
        linkGrid.addRow(1, cut, restore);

        // --- Simulation --------------------------------------------
        heartbeatToggle.setMaxWidth(Double.MAX_VALUE);
        heartbeatToggle.setTooltip(new Tooltip(
                "Leaders send heartbeats every 500 ms; followers that hear nothing for 1.5–2.5 s start an election"));
        heartbeatToggle.setOnAction(e -> setHeartbeatsRunning(heartbeatToggle.isSelected()));

        clusterSize.setMaxWidth(Double.MAX_VALUE);
        Button newRun = button("Save run & start new", "Store this run in the database and reset the cluster",
                () -> startNewRun(clusterSize.getValue()));
        newRun.setMaxWidth(Double.MAX_VALUE);

        VBox simulationBox = new VBox(8,
                heartbeatToggle,
                new Label("Cluster size for next run"), clusterSize,
                newRun);

        VBox controls = new VBox(10,
                section("Node actions", nodeBox),
                section("Network link", linkGrid),
                section("Simulation", simulationBox),
                buildLegend());
        controls.setPadding(new Insets(10));
        controls.setPrefWidth(270);

        ScrollPane scroll = new ScrollPane(controls);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(290);
        scroll.getStyleClass().add("controls");
        return scroll;
    }

    private VBox buildLegend() {

        VBox legend = new VBox(5, new Label("Legend"));
        legend.getStyleClass().add("legend");

        legend.getChildren().addAll(
                legendRow(ClusterView.FOLLOWER, "Follower"),
                legendRow(ClusterView.CANDIDATE, "Candidate / vote request"),
                legendRow(ClusterView.LEADER, "Leader / heartbeat"),
                legendRow(ClusterView.FAILED, "Failed node / blocked message"),
                legendRow(Color.web("#2563eb"), "Vote granted"),
                legendRow(Color.web("#9333ea"), "Vote rejected"),
                new Label("Grey arc = election timer.\nDashed red line = cut link."));

        return legend;
    }

    private static HBox legendRow(Color color, String text) {
        HBox row = new HBox(8, new Circle(6, color), new Label(text));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static TitledPane section(String title, javafx.scene.Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        return pane;
    }

    private static Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private static void stretch(Region... regions) {
        for (Region region : regions) {
            region.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(region, Priority.ALWAYS);
        }
    }

    // ---------------------------------------------------------------
    // Runs
    // ---------------------------------------------------------------

    private void startNewRun(int size) {

        try {
            finishCurrentRun();

            cluster = new RaftCluster(size);
            bridge = new UiEventBridge();
            cluster.addSimulationListener(bridge);
            cluster.addElectionListener(bridge);

            runId = repository.startRun(size, "Dashboard session");
            runSaved = false;
            runStartMillis = System.currentTimeMillis();
            eventsShown = 0;

        } catch (PersistenceException e) {
            Dialogs.error("Could not start a new run", e);
            return;
        }

        List<Integer> ids = cluster.getNodes().stream().map(RaftNode::getNodeId).toList();
        nodeChoice.getItems().setAll(ids);
        linkFrom.getItems().setAll(ids);
        linkTo.getItems().setAll(ids);
        linkFrom.setValue(ids.get(0));
        linkTo.setValue(ids.get(1));

        heartbeatToggle.setSelected(false);
        setHeartbeatsRunning(false);

        clusterView.setCluster(cluster);
        selectNode(ids.get(0));

        eventLog.clear();
        statisticsPane.clear();
        statisticsPane.showStatistics(cluster.getMonitor().getStatistics());
        updateStatusBar();

        if (historyPane != null) {
            historyPane.reload();
        }
    }

    /**
     * Stores the current run. A run in which nothing happened is removed
     * instead, so the history only lists runs worth looking at.
     */
    private void finishCurrentRun() {

        if (cluster == null || runSaved) {
            return;
        }

        cluster.stopHeartbeats();

        EventLogger log = cluster.getMonitor().getEventLogger();

        if (log.isEmpty()) {
            repository.deleteRun(runId);
        } else {
            repository.saveRunResults(
                    runId,
                    log.getEvents(),
                    cluster.getElectionManager().getLeaderHistory(),
                    cluster.getMonitor().getStatistics());
        }

        runSaved = true;
    }

    // ---------------------------------------------------------------
    // Actions (with input validation)
    // ---------------------------------------------------------------

    private void selectNode(int nodeId) {
        selectedNodeId = nodeId;
        nodeChoice.setValue(nodeId);
        clusterView.setSelectedNode(nodeId);
    }

    private NodeState selectedState() {
        return cluster.getNodes().stream()
                .filter(n -> n.getNodeId() == selectedNodeId)
                .findFirst().orElseThrow().getState();
    }

    private void startElection() {

        if (selectedState() == NodeState.FAILED) {
            Dialogs.warning("Node " + selectedNodeId + " has failed",
                    "A crashed node cannot start an election. Recover it first.");
            return;
        }

        if (selectedState() == NodeState.LEADER) {
            Dialogs.warning("Node " + selectedNodeId + " is already the leader",
                    "Starting an election would just move it to the next term. Pick another node.");
            return;
        }

        cluster.electLeader(selectedNodeId);
    }

    private void crash() {

        if (selectedState() == NodeState.FAILED) {
            Dialogs.warning("Already crashed", "Node " + selectedNodeId + " has already failed.");
            return;
        }

        cluster.crashNode(selectedNodeId);
    }

    private void recover() {

        if (selectedState() != NodeState.FAILED) {
            Dialogs.warning("Nothing to recover", "Node " + selectedNodeId + " is running.");
            return;
        }

        cluster.recoverNode(selectedNodeId);
    }

    private void isolate() {
        cluster.disconnectNode(selectedNodeId);
    }

    private void reconnect() {
        cluster.reconnectNode(selectedNodeId);
    }

    private void cutLink() {

        Integer a = linkFrom.getValue();
        Integer b = linkTo.getValue();

        if (a == null || b == null || a.equals(b)) {
            Dialogs.warning("Choose two different nodes", "A link connects two different nodes.");
            return;
        }

        if (!cluster.isLinkConnected(a, b)) {
            Dialogs.warning("Link already cut", "Nodes " + a + " and " + b + " are already disconnected.");
            return;
        }

        cluster.disconnectNodes(a, b);
    }

    private void restoreLink() {

        Integer a = linkFrom.getValue();
        Integer b = linkTo.getValue();

        if (a == null || b == null || a.equals(b)) {
            Dialogs.warning("Choose two different nodes", "A link connects two different nodes.");
            return;
        }

        cluster.reconnectNodes(a, b);
    }

    private void setHeartbeatsRunning(boolean running) {

        if (running) {
            cluster.startHeartbeats();
            heartbeatToggle.setText("⏸  Pause heartbeats");
        } else {
            cluster.stopHeartbeats();
            heartbeatToggle.setText("▶  Start heartbeats");
        }

        clusterView.setTimersVisible(running);
    }

    // ---------------------------------------------------------------
    // Refresh loop (JavaFX thread, every 100 ms)
    // ---------------------------------------------------------------

    private void onTick() {

        if (cluster == null) {
            return;
        }

        for (UiEvent event : bridge.drain()) {
            clusterView.animate(event);
        }

        clusterView.refresh();

        EventLogger log = cluster.getMonitor().getEventLogger();
        int total = log.getEventCount();

        if (total > eventsShown) {
            eventLog.append(log.getRecentEvents(total - eventsShown));
            eventsShown = total;
        }

        if (++tick % 5 == 0) {

            statisticsPane.showStatistics(cluster.getMonitor().getStatistics());

            int highestTerm = cluster.getNodes().stream()
                    .mapToInt(RaftNode::getCurrentTerm).max().orElse(0);
            statisticsPane.addTermSample(
                    (System.currentTimeMillis() - runStartMillis) / 1000.0, highestTerm);

            updateStatusBar();
        }
    }

    private void updateStatusBar() {

        int term = cluster.getNodes().stream().mapToInt(RaftNode::getCurrentTerm).max().orElse(0);

        // During a partition an isolated old leader may still believe it
        // leads an older term; the real leader is the one with the newest term.
        List<RaftNode> leaders = cluster.getNodes().stream()
                .filter(n -> n.getState() == NodeState.LEADER)
                .sorted(java.util.Comparator.comparingInt(RaftNode::getCurrentTerm).reversed())
                .toList();

        int leader = leaders.isEmpty() ? -1 : leaders.get(0).getNodeId();

        String leaderText = leader == -1 ? "No leader" : "Leader: Node " + leader;

        if (leaders.size() > 1) {
            leaderText += "  (stale: " + leaders.stream().skip(1)
                    .map(n -> "Node " + n.getNodeId() + ", term " + n.getCurrentTerm())
                    .collect(java.util.stream.Collectors.joining("; ")) + ")";
        }

        runLabel.setText("Run #" + runId);
        leaderLabel.setText(leaderText);
        leaderLabel.getStyleClass().removeAll("chip-ok", "chip-warn");
        leaderLabel.getStyleClass().add(leader == -1 ? "chip-warn" : "chip-ok");
        termLabel.setText("Term " + term);
        dbLabel.setText("DB: " + new java.io.File(databaseFile).getName());
    }
}
