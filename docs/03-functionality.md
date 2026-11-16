# 3. Functionality

## 3.1 Features

| # | Feature | Where |
|---|---------|-------|
| F1 | Create a cluster of N nodes, all FOLLOWER in term 0 | `RaftCluster(int)` |
| F2 | Start an election for a chosen node | `RaftCluster.electLeader(id)` |
| F3 | Majority voting, one vote per term, stale-term rejection | `RaftNode`, `ElectionManager` |
| F4 | Periodic heartbeats from the leader | `RaftCluster.startHeartbeats()` |
| F5 | Automatic election when a node's randomized timeout elapses | timeout scheduler in `RaftCluster` |
| F6 | Crash and recover a node | `crashNode`, `recoverNode` |
| F7 | Cut and restore the link between two nodes | `disconnectNodes`, `reconnectNodes` |
| F8 | Isolate a node completely and bring it back | `disconnectNode`, `reconnectNode` |
| F9 | Old leaders step down when they see a higher term | `RaftNode.updateTermIfHigher`, heartbeat replies |
| F10 | Election safety check (one leader per term) | `ElectionManager.recordLeader` |
| F11 | Chronological event log of 12 event types | `EventLogger`, `SimulationEventType` |
| F12 | Statistics: elections, success rate, leader changes, heartbeats, timeouts, failures, blocked messages, election duration | `StatisticsTracker` |
| F13 | Store every run in SQLite and query it later | `SimulationRepository` |
| F14 | Console report of cluster status, leader history, statistics and stored data | `Main` |
| F15 | Interactive JavaFX dashboard with animated cluster, fault buttons, live log, charts and run history | `com.raftlite.ui`, see [Dashboard guide](06-dashboard.md) |

## 3.2 Demo scenario (`Main`)

`mvn compile exec:java` runs this script on a 5-node cluster:

| Step | Action | Expected behaviour |
|------|--------|--------------------|
| 0 | Elect Node 3, start heartbeats | Node 3 becomes leader of term 1; the others follow it. |
| 1 | Crash Node 2 | Messages to Node 2 are blocked; the leader stays in place (4 of 5 alive). |
| 2 | Recover Node 2 | Node 2 returns as FOLLOWER and accepts the next heartbeat. |
| 3 | Cut the link 1–3 | Node 1 stops hearing the leader, times out, and starts a higher term. The old leader steps down when it learns that term. |
| 4 | Restore the link 1–3 | The cluster settles on one leader. |
| 5 | Crash the current leader | The followers time out and elect a new leader among the 4 survivors. |
| 6 | Isolate the leader, then reconnect it | The majority side elects a new leader. The reconnected node sees the higher term and becomes a FOLLOWER. |
| End | Print history and statistics, save to the database | The leader per term, statistics and full event log are printed, then read back from SQLite. |

Timing is real (randomized timeouts), so leader ids and term numbers vary
between runs. The safety guarantee (one leader per term) holds in every run.

## 3.3 Sample output (shortened)

```
Recording simulation run #1 in raftlite.db

===== INITIAL LEADER ELECTION =====
Node 3 became CANDIDATE and started election for term 1
Node 3 received 5/5 votes (majority = 3)
Node 3 became LEADER for term 1!

===== TEST 1: CRASH NODE 2 =====
Node 2 has FAILED.
Message blocked: a node has failed.
...
===== LEADER OF EACH TERM =====
Term 1 -> Node 3
Term 2 -> Node 1
Term 3 -> Node 3
Term 4 -> Node 1
Term 5 -> Node 4
Total election attempts: 5

===== SIMULATION STATISTICS =====
Elections started       : 5
Elections won           : 5
Elections failed        : 0
Election success rate   : 100.0%
Leader changes          : 5
Election timeouts       : 4
Heartbeats sent         : 103
Heartbeats received     : 100
Node failures           : 2
Node recoveries         : 1
Messages blocked        : 66
Election duration (ms)  : avg=6.8 min=1 max=25 last=1 (samples=5)

===== STORED IN DATABASE (run #1) =====
Events by type:
  ELECTION_STARTED   5
  LEADER_ELECTED     5
  HEARTBEAT_SENT     103
  ...
Leader of each term:
  Term 1 -> Node 3
  Term 2 -> Node 1
  Term 3 -> Node 3
  Term 4 -> Node 1
  Term 5 -> Node 4
Runs stored so far: 1
  #1  started 2026-11-15T13:52:27Z  nodes=5  finished
```

## 3.4 Using the simulator from code

```java
RaftCluster cluster = new RaftCluster(5);
cluster.electLeader(1);
cluster.startHeartbeats();

cluster.disconnectNode(1);          // isolate the leader
Thread.sleep(3000);                 // the others time out and elect a new leader
cluster.reconnectNode(1);           // node 1 steps down

cluster.stopHeartbeats();
cluster.getMonitor().printReport(System.out);
```

## 3.5 Error handling

| Situation | Behaviour |
|-----------|-----------|
| Election for a node that does not exist or has failed | `ElectionException`; the cluster prints "Election not started" and continues. |
| Two leaders recorded for one term | `ElectionSafetyException`, an unchecked error that signals a bug. |
| Operation on an unknown node id | Ignored with a console message; no state change. |
| Database cannot be opened or written | `PersistenceException` with the SQL cause. The demo prints it and exits with code 1, and a failed save is rolled back. |
