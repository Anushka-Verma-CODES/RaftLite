package com.raftlite.ui;

import com.raftlite.NodeState;
import com.raftlite.RaftCluster;
import com.raftlite.RaftNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Draws the cluster as a ring of nodes and animates the messages between them.
 *
 * Node colour shows the state, the arc around a follower shows how much of
 * its election timeout has elapsed, and dots moving along the links are
 * heartbeats (green), vote requests (amber), granted votes (blue), rejected
 * votes (purple) and blocked messages (red, stopping halfway).
 */
final class ClusterView extends Pane {

    static final Color FOLLOWER = Color.web("#64748b");
    static final Color CANDIDATE = Color.web("#f59e0b");
    static final Color LEADER = Color.web("#16a34a");
    static final Color FAILED = Color.web("#dc2626");

    private static final Color HEARTBEAT = Color.web("#22c55e");
    private static final Color VOTE_REQUEST = Color.web("#f59e0b");
    private static final Color VOTE_GRANTED = Color.web("#2563eb");
    private static final Color VOTE_REJECTED = Color.web("#9333ea");
    private static final Color BLOCKED = Color.web("#dc2626");

    private static final double NODE_RADIUS = 40;
    private static final double MESSAGE_MILLIS = 450;
    private static final int MAX_ACTIVE_ANIMATIONS = 120;

    private final Group linkLayer = new Group();
    private final Group messageLayer = new Group();
    private final Group nodeLayer = new Group();
    private final Group effectLayer = new Group();

    private final Map<Integer, NodeGraphic> graphics = new HashMap<>();
    private final Map<String, Line> links = new HashMap<>();

    private RaftCluster cluster;
    private int selectedNodeId = -1;
    private boolean timersVisible;
    private final java.util.Set<javafx.animation.Animation> running = new java.util.HashSet<>();
    private IntConsumer onNodeClicked = id -> { };

    ClusterView() {
        getStyleClass().add("cluster-view");
        getChildren().addAll(linkLayer, messageLayer, nodeLayer, effectLayer);
        setMinSize(420, 380);
        setPrefSize(640, 520);

        widthProperty().addListener((obs, old, now) -> relayout());
        heightProperty().addListener((obs, old, now) -> relayout());
    }

    void setOnNodeClicked(IntConsumer handler) {
        this.onNodeClicked = handler;
    }

    /** Whether election-timeout arcs are drawn (only meaningful while heartbeats run). */
    void setTimersVisible(boolean visible) {
        this.timersVisible = visible;
    }

    /** Replaces the drawn cluster, for example when a new run starts. */
    void setCluster(RaftCluster cluster) {

        this.cluster = cluster;

        graphics.clear();
        links.clear();
        linkLayer.getChildren().clear();
        nodeLayer.getChildren().clear();
        // Stop animations of the previous cluster before removing their shapes.
        for (javafx.animation.Animation animation : List.copyOf(running)) {
            animation.stop();
        }
        running.clear();
        messageLayer.getChildren().clear();
        effectLayer.getChildren().clear();

        List<RaftNode> nodes = cluster.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {

                Line line = new Line();
                line.getStyleClass().add("link");
                links.put(linkKey(nodes.get(i).getNodeId(), nodes.get(j).getNodeId()), line);
                linkLayer.getChildren().add(line);
            }
        }

        for (RaftNode node : nodes) {

            NodeGraphic graphic = new NodeGraphic(node.getNodeId());
            graphic.setOnMouseClicked(e -> onNodeClicked.accept(node.getNodeId()));
            graphics.put(node.getNodeId(), graphic);
            nodeLayer.getChildren().add(graphic);
        }

        relayout();
        refresh();
    }

    void setSelectedNode(int nodeId) {
        this.selectedNodeId = nodeId;
        refresh();
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private Point2D centerOf(int nodeId) {

        List<RaftNode> nodes = cluster.getNodes();
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getNodeId() == nodeId) {
                index = i;
            }
        }

        double w = Math.max(getWidth(), getMinWidth());
        double h = Math.max(getHeight(), getMinHeight());
        double ring = Math.min(w, h) / 2 - NODE_RADIUS - 30;
        double angle = -Math.PI / 2 + 2 * Math.PI * index / nodes.size();

        return new Point2D(w / 2 + ring * Math.cos(angle), h / 2 + ring * Math.sin(angle));
    }

    private void relayout() {

        if (cluster == null) {
            return;
        }

        graphics.forEach((id, graphic) -> {
            Point2D p = centerOf(id);
            graphic.relocateCenter(p.getX(), p.getY());
        });

        links.forEach((key, line) -> {
            String[] ids = key.split("-");
            Point2D a = centerOf(Integer.parseInt(ids[0]));
            Point2D b = centerOf(Integer.parseInt(ids[1]));
            line.setStartX(a.getX());
            line.setStartY(a.getY());
            line.setEndX(b.getX());
            line.setEndY(b.getY());
        });
    }

    // ---------------------------------------------------------------
    // State refresh (called about 10 times a second)
    // ---------------------------------------------------------------

    void refresh() {

        if (cluster == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (RaftNode node : cluster.getNodes()) {
            graphics.get(node.getNodeId()).update(node, node.getNodeId() == selectedNodeId, now);
        }

        links.forEach((key, line) -> {

            String[] ids = key.split("-");
            int a = Integer.parseInt(ids[0]);
            int b = Integer.parseInt(ids[1]);

            boolean cut = !cluster.isLinkConnected(a, b);

            line.getStyleClass().setAll("link");

            if (cut) {
                line.getStyleClass().add("link-cut");
            }
        });
    }

    // ---------------------------------------------------------------
    // Animations
    // ---------------------------------------------------------------

    void animate(UiEvent event) {

        if (cluster == null ||
                !graphics.containsKey(event.from()) ||
                !graphics.containsKey(event.to())) {
            return;
        }

        switch (event.kind()) {
            case HEARTBEAT -> sendDot(event, HEARTBEAT, 0, 4, false);
            case VOTE_REQUEST -> sendDot(event, VOTE_REQUEST, 0, 6, false);
            case VOTE_GRANTED -> sendDot(event, VOTE_GRANTED, MESSAGE_MILLIS, 6, false);
            case VOTE_REJECTED -> sendDot(event, VOTE_REJECTED, MESSAGE_MILLIS, 6, false);
            case MESSAGE_BLOCKED -> sendDot(event, BLOCKED, 0, 5, true);
            case ELECTION_STARTED -> pulse(event.from(), CANDIDATE, 0);
            case ELECTION_TIMEOUT -> pulse(event.from(), Color.web("#fb923c"), 0);
            case LEADER_ELECTED -> pulse(event.from(), LEADER, 2 * MESSAGE_MILLIS);
        }
    }

    private void sendDot(UiEvent event, Color color, double delayMillis,
                         double radius, boolean blocked) {

        if (running.size() >= MAX_ACTIVE_ANIMATIONS) {
            return;   // keep the UI responsive when traffic is heavy
        }

        Point2D from = centerOf(event.from());
        Point2D to = centerOf(event.to());

        if (blocked) {
            to = from.add(to.subtract(from).multiply(0.45));
        }

        Circle dot = new Circle(radius, color);
        dot.setStroke(Color.WHITE);
        dot.setStrokeWidth(1.2);
        dot.setTranslateX(from.getX());
        dot.setTranslateY(from.getY());
        dot.setOpacity(0);
        messageLayer.getChildren().add(dot);

        TranslateTransition move = new TranslateTransition(Duration.millis(MESSAGE_MILLIS), dot);
        move.setToX(to.getX());
        move.setToY(to.getY());

        FadeTransition appear = new FadeTransition(Duration.millis(1), dot);
        appear.setToValue(1);

        FadeTransition fade = new FadeTransition(Duration.millis(blocked ? 350 : 120), dot);
        fade.setToValue(0);

        SequentialTransition animation = new SequentialTransition(appear, move, fade);
        animation.setDelay(Duration.millis(delayMillis));
        run(animation, dot);
    }

    private void pulse(int nodeId, Color color, double delayMillis) {

        Point2D c = centerOf(nodeId);

        Circle ring = new Circle(c.getX(), c.getY(), NODE_RADIUS);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(color);
        ring.setStrokeWidth(4);
        ring.setOpacity(0);
        effectLayer.getChildren().add(ring);

        ScaleTransition grow = new ScaleTransition(Duration.millis(700), ring);
        grow.setToX(1.9);
        grow.setToY(1.9);

        FadeTransition fade = new FadeTransition(Duration.millis(700), ring);
        fade.setFromValue(0.95);
        fade.setToValue(0);

        ParallelTransition animation = new ParallelTransition(grow, fade);
        animation.setDelay(Duration.millis(delayMillis));
        run(animation, ring);
    }

    private void run(javafx.animation.Animation animation, javafx.scene.Node shape) {

        running.add(animation);

        animation.setOnFinished(e -> {
            running.remove(animation);

            if (shape.getParent() instanceof Group layer) {
                layer.getChildren().remove(shape);
            }
        });

        animation.play();
    }

    private static String linkKey(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    static Color colorOf(NodeState state) {
        return switch (state) {
            case FOLLOWER -> FOLLOWER;
            case CANDIDATE -> CANDIDATE;
            case LEADER -> LEADER;
            case FAILED -> FAILED;
        };
    }

    // ---------------------------------------------------------------
    // One node on screen
    // ---------------------------------------------------------------

    private final class NodeGraphic extends Group {

        private final Arc timer = new Arc();
        private final Circle body = new Circle(NODE_RADIUS);
        private final Text title = new Text();
        private final Text state = new Text();
        private final Text detail = new Text();

        NodeGraphic(int nodeId) {

            timer.setRadiusX(NODE_RADIUS + 8);
            timer.setRadiusY(NODE_RADIUS + 8);
            timer.setStartAngle(90);
            timer.setType(ArcType.OPEN);
            timer.setFill(null);
            timer.setStrokeWidth(4);
            timer.getStyleClass().add("timeout-arc");

            body.getStyleClass().add("node-body");

            title.setText("Node " + nodeId);
            title.getStyleClass().add("node-title");
            state.getStyleClass().add("node-state");
            detail.getStyleClass().add("node-detail");

            for (Text text : new Text[] {title, state, detail}) {
                text.setTextAlignment(TextAlignment.CENTER);
                text.setMouseTransparent(true);
            }

            getChildren().addAll(timer, body, title, state, detail);
            setCursor(javafx.scene.Cursor.HAND);
        }

        void relocateCenter(double x, double y) {

            body.setCenterX(x);
            body.setCenterY(y);
            timer.setCenterX(x);
            timer.setCenterY(y);
            centerTexts();
        }

        private void centerTexts() {
            double x = body.getCenterX();
            double y = body.getCenterY();
            centerText(title, x, y - 4);
            centerText(state, x, y + 14);
            centerText(detail, x, y + NODE_RADIUS + 26);
        }

        private void centerText(Text text, double x, double y) {
            text.setX(x - text.getLayoutBounds().getWidth() / 2);
            text.setY(y);
        }

        void update(RaftNode node, boolean selected, long now) {

            NodeState nodeState = node.getState();

            body.setFill(colorOf(nodeState));
            body.setStroke(selected ? Color.web("#1e3a8a") : Color.WHITE);
            body.setStrokeWidth(selected ? 5 : 3);

            state.setText(nodeState == NodeState.LEADER ? "★ LEADER" : nodeState.name());

            Integer votedFor = node.getVotedFor();
            detail.setText("term " + node.getCurrentTerm() +
                    "  ·  vote " + (votedFor == null ? "–" : votedFor));

            boolean waiting = timersVisible &&
                    (nodeState == NodeState.FOLLOWER || nodeState == NodeState.CANDIDATE);

            timer.setVisible(waiting);

            if (waiting) {
                double fraction = Math.min(1.0,
                        (now - node.getLastHeartbeatTime()) / (double) node.getElectionTimeout());
                timer.setLength(-360 * fraction);
                timer.setStroke(fraction > 0.75 ? Color.web("#f97316") : Color.web("#94a3b8"));
            }

            // Text widths change with the content, so re-centre each time.
            centerTexts();
        }
    }
}
