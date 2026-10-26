package com.raftlite;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        RaftCluster cluster = new RaftCluster(5);

        cluster.electLeader(3);
        cluster.startHeartbeats();

        Thread.sleep(3000);

        cluster.stopHeartbeats();
        cluster.displayClusterStatus();
    }
}