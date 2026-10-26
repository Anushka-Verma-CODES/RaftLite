package com.raftlite;

public class Main {

    public static void main(String[] args) {
        System.out.println("Raft-Lite Simulator Started!");

        RaftCluster cluster = new RaftCluster(5);

        cluster.electLeader(3);

        cluster.displayClusterStatus();
    }
}