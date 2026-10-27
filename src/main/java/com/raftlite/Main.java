
package com.raftlite;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        RaftCluster cluster = new RaftCluster(5);

        cluster.electLeader(3);
        cluster.startHeartbeats();

        Thread.sleep(1000);

        System.out.println("\n===== TEST 1: CRASH NODE 2 =====");
        cluster.crashNode(2);
        cluster.displayClusterStatus();

        Thread.sleep(1000);

        System.out.println("\n===== TEST 2: RECOVER NODE 2 =====");
        cluster.recoverNode(2);
        cluster.displayClusterStatus();

        System.out.println("\n===== TEST 3: DISCONNECT NODES 1 AND 3 =====");
        cluster.disconnectNodes(1, 3);

        Thread.sleep(3000);

        System.out.println("\n===== TEST 4: RECONNECT NODES 1 AND 3 =====");
        cluster.reconnectNodes(1, 3);

        Thread.sleep(1000);

        cluster.stopHeartbeats();
        cluster.displayClusterStatus();
    }
}
