
package com.raftlite;

public class Main {

    public static void main(String[] args)
            throws InterruptedException {

        RaftCluster cluster = new RaftCluster(5);

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

            System.out.println(
                    "\n===== ALL TESTS COMPLETED =====");

        } finally {

            cluster.stopHeartbeats();

            System.out.println(
                    "Heartbeat and timeout schedulers stopped.");
        }
    }
}
