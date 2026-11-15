package com.raftlite.persistence;

import com.raftlite.monitoring.SimulationEvent;
import com.raftlite.monitoring.SimulationEventType;
import com.raftlite.monitoring.SimulationStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Stores simulation runs in the database and reads them back (JDBC DAO).
 *
 * A run is saved in two steps: {@link #startRun} creates the run row when
 * the simulation begins, and {@link #saveRunResults} writes its event log,
 * leader history and statistics in a single transaction when it ends.
 * Writing at the end keeps database work off the simulator's heartbeat
 * threads.
 */
public final class SimulationRepository {

    private final Connection connection;

    public SimulationRepository(DatabaseManager database) {
        this.connection = Objects.requireNonNull(database, "database must not be null")
                .getConnection();
    }

    // ---------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------

    /** Creates a new, unfinished run and returns its id. */
    public long startRun(int clusterSize, String description) {

        if (clusterSize <= 0) {
            throw new IllegalArgumentException(
                    "clusterSize must be positive: " + clusterSize);
        }

        Objects.requireNonNull(description, "description must not be null");

        String sql = "INSERT INTO simulation_run (started_at, cluster_size, description) " +
                     "VALUES (?, ?, ?)";

        try (PreparedStatement insert =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, Instant.now().toString());
            insert.setInt(2, clusterSize);
            insert.setString(3, description);
            insert.executeUpdate();

            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not create a simulation run", e);
        }
    }

    /**
     * Saves everything a finished run produced and marks it finished.
     * Either all of it is stored or, on error, none of it.
     *
     * @param leaderHistory term -> id of the leader of that term
     * @throws PersistenceException if the run does not exist, is already
     *                              finished, or the write fails
     */
    public void saveRunResults(long runId,
                               List<SimulationEvent> events,
                               Map<Integer, Integer> leaderHistory,
                               SimulationStatistics statistics) {

        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(leaderHistory, "leaderHistory must not be null");
        Objects.requireNonNull(statistics, "statistics must not be null");

        SimulationRun run = findRun(runId).orElseThrow(() ->
                new PersistenceException("Simulation run " + runId + " does not exist"));

        if (run.isFinished()) {
            throw new PersistenceException(
                    "Simulation run " + runId + " has already been saved");
        }

        inTransaction(() -> {
            insertEvents(runId, events);
            insertLeaderHistory(runId, leaderHistory);
            insertStatistics(runId, statistics);
            markFinished(runId);
        });
    }

    private void insertEvents(long runId, List<SimulationEvent> events)
            throws SQLException {

        String sql = "INSERT INTO simulation_event " +
                     "(run_id, sequence_no, occurred_at, event_type, node_id, term, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement insert = connection.prepareStatement(sql)) {

            int sequence = 1;

            for (SimulationEvent event : events) {

                insert.setLong(1, runId);
                insert.setInt(2, sequence++);
                insert.setString(3, event.getTimestamp().toString());
                insert.setString(4, event.getType().name());
                insert.setInt(5, event.getNodeId());

                if (event.hasTerm()) {
                    insert.setInt(6, event.getTerm());
                } else {
                    insert.setNull(6, Types.INTEGER);
                }

                insert.setString(7, event.getDescription());
                insert.addBatch();
            }

            insert.executeBatch();
        }
    }

    private void insertLeaderHistory(long runId, Map<Integer, Integer> leaderHistory)
            throws SQLException {

        String sql = "INSERT INTO leader_history (run_id, term, leader_id) VALUES (?, ?, ?)";

        try (PreparedStatement insert = connection.prepareStatement(sql)) {

            for (Map.Entry<Integer, Integer> entry : leaderHistory.entrySet()) {

                insert.setLong(1, runId);
                insert.setInt(2, entry.getKey());
                insert.setInt(3, entry.getValue());
                insert.addBatch();
            }

            insert.executeBatch();
        }
    }

    private void insertStatistics(long runId, SimulationStatistics s)
            throws SQLException {

        String sql = "INSERT INTO run_statistics (run_id, total_elections, successful_elections, " +
                     "failed_elections, leader_changes, heartbeats_sent, heartbeats_received, " +
                     "election_timeouts, node_failures, node_recoveries, messages_blocked, " +
                     "election_duration_samples, total_election_duration_millis, " +
                     "min_election_duration_millis, max_election_duration_millis, " +
                     "last_election_duration_millis) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement insert = connection.prepareStatement(sql)) {

            insert.setLong(1, runId);
            insert.setLong(2, s.totalElections());
            insert.setLong(3, s.successfulElections());
            insert.setLong(4, s.failedElections());
            insert.setLong(5, s.leaderChanges());
            insert.setLong(6, s.heartbeatsSent());
            insert.setLong(7, s.heartbeatsReceived());
            insert.setLong(8, s.electionTimeouts());
            insert.setLong(9, s.nodeFailures());
            insert.setLong(10, s.nodeRecoveries());
            insert.setLong(11, s.messagesBlocked());
            insert.setLong(12, s.electionDurationSamples());
            insert.setLong(13, s.totalElectionDurationMillis());
            insert.setLong(14, s.minElectionDurationMillis());
            insert.setLong(15, s.maxElectionDurationMillis());
            insert.setLong(16, s.lastElectionDurationMillis());
            insert.executeUpdate();
        }
    }

    private void markFinished(long runId) throws SQLException {

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE simulation_run SET finished_at = ? WHERE run_id = ?")) {

            update.setString(1, Instant.now().toString());
            update.setLong(2, runId);
            update.executeUpdate();
        }
    }

    /** Deletes a run together with its events, leaders and statistics. */
    public boolean deleteRun(long runId) {

        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM simulation_run WHERE run_id = ?")) {

            delete.setLong(1, runId);
            return delete.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new PersistenceException("Could not delete run " + runId, e);
        }
    }

    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    public Optional<SimulationRun> findRun(long runId) {

        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM simulation_run WHERE run_id = ?")) {

            query.setLong(1, runId);

            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? Optional.of(toRun(rows)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not read run " + runId, e);
        }
    }

    /** Every stored run, newest first. */
    public List<SimulationRun> findAllRuns() {

        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM simulation_run ORDER BY run_id DESC");
             ResultSet rows = query.executeQuery()) {

            List<SimulationRun> runs = new ArrayList<>();

            while (rows.next()) {
                runs.add(toRun(rows));
            }

            return runs;

        } catch (SQLException e) {
            throw new PersistenceException("Could not list simulation runs", e);
        }
    }

    /** The event log of a run, in the order the events happened. */
    public List<SimulationEvent> findEvents(long runId) {
        return queryEvents(
                "SELECT * FROM simulation_event WHERE run_id = ? ORDER BY sequence_no",
                runId, null);
    }

    /** The events of one type in a run, in the order they happened. */
    public List<SimulationEvent> findEvents(long runId, SimulationEventType type) {

        Objects.requireNonNull(type, "type must not be null");

        return queryEvents(
                "SELECT * FROM simulation_event WHERE run_id = ? AND event_type = ? " +
                "ORDER BY sequence_no",
                runId, type);
    }

    private List<SimulationEvent> queryEvents(String sql,
                                              long runId,
                                              SimulationEventType type) {

        try (PreparedStatement query = connection.prepareStatement(sql)) {

            query.setLong(1, runId);

            if (type != null) {
                query.setString(2, type.name());
            }

            try (ResultSet rows = query.executeQuery()) {

                List<SimulationEvent> events = new ArrayList<>();

                while (rows.next()) {

                    int term = rows.getInt("term");

                    if (rows.wasNull()) {
                        term = SimulationEvent.NO_TERM;
                    }

                    events.add(new SimulationEvent(
                            Instant.parse(rows.getString("occurred_at")),
                            SimulationEventType.valueOf(rows.getString("event_type")),
                            rows.getInt("node_id"),
                            term,
                            rows.getString("description")));
                }

                return events;
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not read events of run " + runId, e);
        }
    }

    /** How many events of each type a run produced (types that never occurred are left out). */
    public Map<SimulationEventType, Long> countEventsByType(long runId) {

        try (PreparedStatement query = connection.prepareStatement(
                "SELECT event_type, COUNT(*) AS total FROM simulation_event " +
                "WHERE run_id = ? GROUP BY event_type")) {

            query.setLong(1, runId);

            try (ResultSet rows = query.executeQuery()) {

                Map<SimulationEventType, Long> counts =
                        new EnumMap<>(SimulationEventType.class);

                while (rows.next()) {
                    counts.put(
                            SimulationEventType.valueOf(rows.getString("event_type")),
                            rows.getLong("total"));
                }

                return counts;
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not count events of run " + runId, e);
        }
    }

    /** Term -> leader id for a run, ordered by term. */
    public Map<Integer, Integer> findLeaderHistory(long runId) {

        try (PreparedStatement query = connection.prepareStatement(
                "SELECT term, leader_id FROM leader_history WHERE run_id = ? ORDER BY term")) {

            query.setLong(1, runId);

            try (ResultSet rows = query.executeQuery()) {

                Map<Integer, Integer> history = new TreeMap<>();

                while (rows.next()) {
                    history.put(rows.getInt("term"), rows.getInt("leader_id"));
                }

                return history;
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not read leaders of run " + runId, e);
        }
    }

    public Optional<SimulationStatistics> findStatistics(long runId) {

        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM run_statistics WHERE run_id = ?")) {

            query.setLong(1, runId);

            try (ResultSet r = query.executeQuery()) {

                if (!r.next()) {
                    return Optional.empty();
                }

                return Optional.of(new SimulationStatistics(
                        r.getLong("total_elections"),
                        r.getLong("successful_elections"),
                        r.getLong("failed_elections"),
                        r.getLong("leader_changes"),
                        r.getLong("heartbeats_sent"),
                        r.getLong("heartbeats_received"),
                        r.getLong("election_timeouts"),
                        r.getLong("node_failures"),
                        r.getLong("node_recoveries"),
                        r.getLong("messages_blocked"),
                        r.getLong("election_duration_samples"),
                        r.getLong("total_election_duration_millis"),
                        r.getLong("min_election_duration_millis"),
                        r.getLong("max_election_duration_millis"),
                        r.getLong("last_election_duration_millis")));
            }

        } catch (SQLException e) {
            throw new PersistenceException("Could not read statistics of run " + runId, e);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static SimulationRun toRun(ResultSet row) throws SQLException {

        String finished = row.getString("finished_at");

        return new SimulationRun(
                row.getLong("run_id"),
                Instant.parse(row.getString("started_at")),
                finished == null ? null : Instant.parse(finished),
                row.getInt("cluster_size"),
                row.getString("description"));
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }

    private void inTransaction(SqlWork work) {

        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                work.run();
                connection.commit();

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;

            } finally {
                connection.setAutoCommit(autoCommit);
            }

        } catch (SQLException e) {
            throw new PersistenceException("Database transaction failed and was rolled back", e);
        }
    }
}
