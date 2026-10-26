package com.raftlite;

public enum NodeState {
    FOLLOWER,
    CANDIDATE,
    LEADER,
    FAILED
}