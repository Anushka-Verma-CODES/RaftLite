
package com.raftlite;

import com.raftlite.persistence.DatabaseManager;
import com.raftlite.persistence.PersistenceException;
import com.raftlite.persistence.SimulationRepository;
import com.raftlite.persistence.SimulationRun;

import java.util.List;

public class Main {

    private static final int CLUSTER_SIZE = 5;

    /**
     * Runs the demo scenario and stores the run in a SQLite database.
     *
     * @param args optional: path of the database file
     *             (default {@value DatabaseManager#DEFAULT_DATABASE_FILE})
     */
    public static void main(String[] args)
            throws InterruptedException {

        String databaseFile = args.length > 0
                ? args[0]
                : DatabaseManager.DEFAULT_DATABASE_FILE;

        try (DatabaseManager database = new DatabaseManager(databaseFile)) {

            SimulationRepository repository = new SimulationRepository(database);

            long runId = repository.startRun(
                    CLUSTER_SIZE, "Demo: crash, recovery, partition and leader isolation");

            System.out.println(
                    "Recording simulation run #" + runId + " in " + databaseFile);

            RaftCluster cluster = runScenario();

            repository.saveRunResults(
                    runId,
                    cluster.getMonitor().getEventLogger().getEvents(),
                    cluster.getElectionManager().getLeaderHistory(),
                    cluster.getMonitor().getStatistics());

            printStoredRun(repository, runId);

        } catch (PersistenceException e) {

            System.err.println("Database error: " + e.getMessage());

            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }

            System.exit(1);
        }
    }

    /** Reads the run back from the database to show what was stored. */
    private static void printStoredRun(SimulationRepository repository, long runId) {

        System.out.println("\n===== STORED IN DATABASE (run #" + runId + ") =====");

        System.out.println("Events by type:");
        repository.countEventsByType(runId).forEach((type, count) ->
                System.out.printf("  %-18s %d%n", type, count));

        System.out.println("Leader of each term:");
        repository.findLeaderHistory(runId).forEach((term, leader) ->
                System.out.println("  Term " + term + " -> Node " + leader));

        List<SimulationRun> runs = repository.findAllRuns();

        System.out.println("Runs stored so far: " + runs.size());

        for (SimulationRun run : runs) {
            System.out.println(
                    "  #" + run.runId() + "  started " + run.startedAt() +
                    "  nodes=" + run.clusterSize() +
                    "  " + (run.isFinished() ? "finished" : "incomplete"));
        }
    }

    /** The fault-tolerance scenario. Returns the cluster so its results can be saved. */
    private static RaftCluster runScenario()
            throws InterruptedException {

        RaftCluster cluster = new RaftCluster(CLUSTER_SIZE);

        try {

            System.out.println("\n===== INITIAL LEADER ELECTION =====");

            cluster.electLeader(3);

            cluster.startHeartbeats();

            Thread.sleep(1000);

            cluster.displayClusterStatus();

            // TEST 1: Crash Node 2

            System.out.println(
                    "\n===== TEST 1: CRASH NODE 2 =====");

            cluster.crashNode(2);

            Thread.sleep(1000);

            cluster.displayClusterStatus();

            // TEST 2: Recover Node 2

            System.out.println(
                    "\n===== TEST 2: RECOVER NODE 2 =====");

            cluster.recoverNode(2);

            Thread.sleep(1000);

            cluster.displayClusterStatus();

            // TEST 3: Disconnect Nodes 1 and 3

            System.out.println(
                    "\n===== TEST 3: DISCONNECT NODES 1 AND 3 =====");

            cluster.disconnectNodes(1, 3);

            Thread.sleep(3500);

            cluster.displayClusterStatus();

            // TEST 4: Reconnect Nodes 1 and 3

            System.out.println(
                    "\n===== TEST 4: RECONNECT NODES 1 AND 3 =====");

            cluster.reconnectNodes(1, 3);

            Thread.sleep(1500);

            cluster.displayClusterStatus();

            // TEST 5: Crash Current Leader

            System.out.println(
                    "\n===== TEST 5: CRASH CURRENT LEADER =====");

            int currentLeader = cluster.getCurrentLeaderId();

            if (currentLeader != -1) {

                System.out.println(
                        "Crashing current leader: Node " +
                        currentLeader);

                cluster.crashNode(currentLeader);

                Thread.sleep(3500);

                cluster.displayClusterStatus();

            } else {

                System.out.println(
                        "No active leader found.");
            }

            // TEST 6: Isolate the leader, then reconnect it.
            // The old leader must step down when it sees the higher term.

            System.out.println(
                    "\n===== TEST 6: ISOLATE AND RECONNECT LEADER =====");

            int isolatedLeader = cluster.getCurrentLeaderId();

            if (isolatedLeader != -1) {

                cluster.disconnectNode(isolatedLeader);

                Thread.sleep(3500);

                System.out.println(
                        "Status while Node " + isolatedLeader +
                        " is isolated:");

                cluster.displayClusterStatus();

                cluster.reconnectNode(isolatedLeader);

                Thread.sleep(1000);

                cluster.displayClusterStatus();
            }

            System.out.println("\n===== LEADER OF EACH TERM =====");

            ElectionManager elections = cluster.getElectionManager();

            elections.getLeaderHistory().forEach((term, leader) ->
                    System.out.println(
                            "Term " + term + " -> Node " + leader));

            System.out.println(
                    "Total election attempts: " +
                    elections.getElectionAttempts());

            cluster.getMonitor().printReport(System.out);

            System.out.println(
                    "\n===== ALL TESTS COMPLETED =====");

        } finally {

            cluster.stopHeartbeats();

            System.out.println(
                    "Heartbeat and timeout schedulers stopped.");
        }

        return cluster;
    }
}
