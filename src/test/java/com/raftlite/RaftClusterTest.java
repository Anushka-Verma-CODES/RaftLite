package com.raftlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;

import com.raftlite.monitoring.SimulationEvent;
import com.raftlite.monitoring.SimulationEventType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fault-tolerance scenarios on a whole 5-node cluster. */
class RaftClusterTest {

    private RaftCluster cluster;

    @BeforeEach
    void setUp() {
        cluster = new RaftCluster(5);
    }

    @AfterEach
    void tearDown() {
        cluster.stopHeartbeats();
    }

    private NodeState stateOf(int id) {
        return cluster.getNodeById(id).getState();
    }

    @Test
    void initialElectionProducesOneLeaderThatEveryoneFollows() {

        cluster.electLeader(3);

        assertEquals(3, cluster.getCurrentLeaderId());
        assertEquals(5, cluster.getClusterSize());

        for (int id = 1; id <= 5; id++) {
            assertEquals(3, cluster.getNodeById(id).getLeaderId().orElseThrow(),
                    "node " + id + " should follow node 3");
        }
    }

    @Test
    void newLeaderIsElectedAfterTheLeaderCrashes() {

        cluster.electLeader(1);
        cluster.crashNode(1);

        assertEquals(-1, cluster.getCurrentLeaderId());

        cluster.electLeader(2);

        assertEquals(2, cluster.getCurrentLeaderId());
        assertEquals(NodeState.FAILED, stateOf(1));
    }

    @Test
    void crashedNodeRecoversAsFollowerAndAcceptsTheCurrentLeader() {

        cluster.electLeader(1);
        cluster.crashNode(3);
        cluster.recoverNode(3);

        assertEquals(NodeState.FOLLOWER, stateOf(3));

        cluster.broadcastHeartbeats();

        assertEquals(1, cluster.getNodeById(3).getLeaderId().orElseThrow());
    }

    @Test
    void minorityPartitionCannotElectALeader() {

        cluster.electLeader(1);

        // Nodes 4 and 5 are cut off from 1, 2 and 3.
        for (int inside : new int[] {4, 5}) {
            for (int outside : new int[] {1, 2, 3}) {
                cluster.disconnectNodes(inside, outside);
            }
        }

        cluster.electLeader(4);

        assertEquals(NodeState.CANDIDATE, stateOf(4));
        assertEquals(NodeState.LEADER, stateOf(1));
    }

    /**
     * Regression test: an isolated old leader is reconnected while a newer
     * leader exists. When its first heartbeat reveals the newer term it must
     * stop sending heartbeats; before the fix it kept going with the new
     * term, and the remaining followers accepted it as leader.
     */
    @Test
    void reconnectedOldLeaderDoesNotTakeOverFollowersOfTheNewTerm() {

        cluster.electLeader(1);          // term 1: leader 1
        cluster.disconnectNode(1);
        cluster.electLeader(3);          // term 2: leader 3, node 1 still thinks it leads term 1
        cluster.reconnectNode(1);

        assertEquals(NodeState.LEADER, stateOf(1));

        cluster.broadcastHeartbeats();   // node 1 sends first, then node 3

        assertEquals(NodeState.FOLLOWER, stateOf(1));
        assertEquals(NodeState.LEADER, stateOf(3));

        // No follower may have accepted node 1 as leader of term 2.
        List<SimulationEvent> acceptedFromOldLeader = cluster.getMonitor()
                .getEventLogger()
                .getEvents(SimulationEventType.HEARTBEAT_RECEIVED)
                .stream()
                .filter(e -> e.getTerm() == 2 &&
                             e.getDescription().endsWith("from Node 1"))
                .toList();

        assertEquals(List.of(), acceptedFromOldLeader);

        for (int id : new int[] {1, 2, 4, 5}) {
            assertEquals(3, cluster.getNodeById(id).getLeaderId().orElseThrow(),
                    "node " + id + " should follow node 3");
        }
    }

    @Test
    void timeoutElectsANewLeaderWhenTheLeaderIsCrashed() {

        cluster.electLeader(1);
        cluster.startHeartbeats();
        cluster.crashNode(1);

        // The longest election timeout is 2.5 s; allow time for a second round.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (cluster.getCurrentLeaderId() == -1 ||
                    cluster.getCurrentLeaderId() == 1) {
                Thread.sleep(50);
            }
        });

        assertNotEquals(1, cluster.getCurrentLeaderId());
        assertTrue(cluster.getMonitor().getStatistics().electionTimeouts() >= 1);
    }

    @Test
    void operationsOnUnknownNodesAreIgnored() {

        cluster.electLeader(1);

        cluster.crashNode(42);
        cluster.recoverNode(42);
        cluster.disconnectNodes(1, 42);
        cluster.electLeader(42);

        assertEquals(1, cluster.getCurrentLeaderId());
        assertEquals(0, cluster.getMonitor().getStatistics().nodeFailures());
    }
}
