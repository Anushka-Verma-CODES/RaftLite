package com.raftlite.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.raftlite.RaftCluster;
import com.raftlite.monitoring.SimulationEvent;
import com.raftlite.monitoring.SimulationEventType;
import com.raftlite.monitoring.SimulationStatistics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for storing and reading simulation runs in SQLite. */
class SimulationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private DatabaseManager database;
    private SimulationRepository repository;

    @BeforeEach
    void setUp() {
        database = DatabaseManager.inMemory();
        repository = new SimulationRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static SimulationStatistics sampleStatistics() {
        return new SimulationStatistics(3, 2, 1, 2, 40, 38, 1, 1, 1, 4, 2, 10, 4, 6, 6);
    }

    private static List<SimulationEvent> sampleEvents() {
        return List.of(
                new SimulationEvent(T0, SimulationEventType.ELECTION_STARTED, 1, 1, "Node 1 started"),
                new SimulationEvent(T0.plusMillis(5), SimulationEventType.LEADER_ELECTED, 1, 1, "Node 1 won"),
                new SimulationEvent(T0.plusMillis(9), SimulationEventType.NODE_FAILED, 1,
                        SimulationEvent.NO_TERM, "Node 1 failed"),
                new SimulationEvent(T0.plusMillis(12), SimulationEventType.ELECTION_STARTED, 2, 2, "Node 2 started"));
    }

    @Test
    void newRunIsStoredAsUnfinished() {

        long runId = repository.startRun(5, "test run");

        SimulationRun run = repository.findRun(runId).orElseThrow();

        assertEquals(5, run.clusterSize());
        assertEquals("test run", run.description());
        assertFalse(run.isFinished());
    }

    @Test
    void savedRunCanBeReadBackExactly() {

        long runId = repository.startRun(5, "round trip");

        repository.saveRunResults(runId, sampleEvents(), Map.of(1, 1, 2, 2), sampleStatistics());

        assertTrue(repository.findRun(runId).orElseThrow().isFinished());
        assertEquals(sampleEvents(), repository.findEvents(runId));
        assertEquals(Map.of(1, 1, 2, 2), repository.findLeaderHistory(runId));
        assertEquals(sampleStatistics(), repository.findStatistics(runId).orElseThrow());
    }

    @Test
    void eventsWithoutATermKeepThatAfterLoading() {

        long runId = repository.startRun(3, "terms");
        repository.saveRunResults(runId, sampleEvents(), Map.of(), sampleStatistics());

        SimulationEvent failure = repository.findEvents(runId, SimulationEventType.NODE_FAILED).get(0);

        assertFalse(failure.hasTerm());
    }

    @Test
    void eventsCanBeFilteredAndCountedByType() {

        long runId = repository.startRun(3, "filter");
        repository.saveRunResults(runId, sampleEvents(), Map.of(), sampleStatistics());

        assertEquals(2, repository.findEvents(runId, SimulationEventType.ELECTION_STARTED).size());

        Map<SimulationEventType, Long> counts = repository.countEventsByType(runId);

        assertEquals(2L, counts.get(SimulationEventType.ELECTION_STARTED));
        assertEquals(1L, counts.get(SimulationEventType.LEADER_ELECTED));
        assertFalse(counts.containsKey(SimulationEventType.HEARTBEAT_SENT));
    }

    @Test
    void runsAreListedNewestFirstAndKeptApart() {

        long first = repository.startRun(3, "first");
        long second = repository.startRun(5, "second");

        repository.saveRunResults(first, sampleEvents(), Map.of(1, 1), sampleStatistics());

        assertEquals(List.of(second, first),
                repository.findAllRuns().stream().map(SimulationRun::runId).toList());
        assertTrue(repository.findEvents(second).isEmpty());
        assertTrue(repository.findStatistics(second).isEmpty());
    }

    @Test
    void runCannotBeSavedTwice() {

        long runId = repository.startRun(3, "twice");
        repository.saveRunResults(runId, sampleEvents(), Map.of(1, 1), sampleStatistics());

        assertThrows(PersistenceException.class, () ->
                repository.saveRunResults(runId, sampleEvents(), Map.of(1, 1), sampleStatistics()));

        assertEquals(4, repository.findEvents(runId).size());
    }

    @Test
    void savingAnUnknownRunFails() {

        assertThrows(PersistenceException.class, () ->
                repository.saveRunResults(99, sampleEvents(), Map.of(), sampleStatistics()));
    }

    @Test
    void failedSaveIsRolledBackCompletely() {

        long runId = repository.startRun(3, "rollback");

        // Term 0 violates the CHECK constraint after the events were written.
        assertThrows(PersistenceException.class, () ->
                repository.saveRunResults(runId, sampleEvents(), Map.of(0, 1), sampleStatistics()));

        assertTrue(repository.findEvents(runId).isEmpty(), "events must be rolled back");
        assertFalse(repository.findRun(runId).orElseThrow().isFinished());

        // The run can still be saved correctly afterwards.
        repository.saveRunResults(runId, sampleEvents(), Map.of(1, 1), sampleStatistics());
        assertEquals(4, repository.findEvents(runId).size());
    }

    @Test
    void deletingARunRemovesAllItsData() {

        long runId = repository.startRun(3, "delete");
        repository.saveRunResults(runId, sampleEvents(), Map.of(1, 1), sampleStatistics());

        assertTrue(repository.deleteRun(runId));
        assertFalse(repository.deleteRun(runId));

        assertTrue(repository.findRun(runId).isEmpty());
        assertTrue(repository.findEvents(runId).isEmpty());
        assertTrue(repository.findLeaderHistory(runId).isEmpty());
        assertTrue(repository.findStatistics(runId).isEmpty());
    }

    @Test
    void invalidRunArgumentsAreRejected() {

        assertThrows(IllegalArgumentException.class, () -> repository.startRun(0, "x"));
        assertThrows(NullPointerException.class, () -> repository.startRun(3, null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseManager(" "));
    }

    @Test
    void dataSurvivesClosingAndReopeningTheDatabaseFile(@TempDir Path folder) {

        String file = folder.resolve("runs.db").toString();
        long runId;

        try (DatabaseManager db = new DatabaseManager(file)) {
            SimulationRepository repo = new SimulationRepository(db);
            runId = repo.startRun(5, "persistent");
            repo.saveRunResults(runId, sampleEvents(), Map.of(1, 1), sampleStatistics());
        }

        try (DatabaseManager db = new DatabaseManager(file)) {
            SimulationRepository repo = new SimulationRepository(db);

            assertEquals(1, repo.findAllRuns().size());
            assertEquals(sampleEvents(), repo.findEvents(runId));
        }
    }

    @Test
    void realSimulationIsStoredAndMatchesTheMonitor() {

        RaftCluster cluster = new RaftCluster(5);
        cluster.electLeader(2);
        cluster.crashNode(2);
        cluster.electLeader(4);

        long runId = repository.startRun(cluster.getClusterSize(), "integration");

        repository.saveRunResults(
                runId,
                cluster.getMonitor().getEventLogger().getEvents(),
                cluster.getElectionManager().getLeaderHistory(),
                cluster.getMonitor().getStatistics());

        assertEquals(cluster.getMonitor().getEventLogger().getEvents(),
                repository.findEvents(runId));
        assertEquals(Map.of(1, 2, 2, 4), repository.findLeaderHistory(runId));
        assertEquals(cluster.getMonitor().getStatistics(),
                repository.findStatistics(runId).orElseThrow());
    }
}
