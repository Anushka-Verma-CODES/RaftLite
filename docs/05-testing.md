# 5. Testing

## 5.1 Strategy

| Level | What is tested | How |
|-------|----------------|-----|
| Unit | Raft rules of one node, majority maths, message delivery, event and statistics classes | Objects built directly, no threads |
| Integration | Whole cluster: elections, crashes, partitions, reconnection; monitoring fed by the real simulator | `RaftCluster` with schedulers off, so results are deterministic |
| Timing | Automatic re-election after the leader crashes | Schedulers on, bounded wait (`assertTimeoutPreemptively`, 10 s) |
| Database | Round trip, filters, aggregates, transactions, rollback, cascade delete, reopening the file | In-memory SQLite, plus a real file in a JUnit `@TempDir` |
| End to end | A real simulation saved and read back equals the in-memory monitor | `SimulationRepositoryTest.realSimulationIsStoredAndMatchesTheMonitor` |
| Manual | Full demo scenario | `mvn compile exec:java`, output checked against [section 3.2](03-functionality.md#32-demo-scenario-main) |

Framework: **JUnit 5.13** through the Maven Surefire plugin.

## 5.2 Running the tests

```bash
mvn clean test
```

Reports are written to `target/surefire-reports/`.

## 5.3 Test inventory

| Test class | Tests | Covers |
|------------|------:|--------|
| `RaftNodeTest` | 17 | Initial state, timeout range, starting an election, one vote per term, stale terms, step-down, duplicate votes, becoming leader, heartbeats, crash and recovery, timeout flag |
| `ElectionManagerTest` | 8 | Majority formula, winning with all votes or exactly a majority, failing without one, unknown or failed candidate, leader history, listener callbacks |
| `NetworkSimulatorTest` | 8 | Vote round trip, heartbeat delivery, blocking for unknown or failed nodes and cut links (both directions), idempotent link events, node isolation, stale leader step-down |
| `RaftClusterTest` | 7 | One leader followed by all, re-election after a crash, recovery, minority partition, **split-brain regression**, timeout-driven election, unknown node ids |
| `SimulationRepositoryTest` | 12 | Create run, exact round trip, NULL terms, filter and count by type, multiple runs, double save, unknown run, **transaction rollback**, cascade delete, argument validation, persistence across reopen, real simulation saved |
| `EventLoggerTest` | 9 | Logging, ordering, filtering, snapshots, clear |
| `SimulationEventTest` | 7 | Validation, factories, equality, log line format |
| `SimulationMonitorTest` | 8 | Each listener callback produces the right event and counter |
| `StatisticsTrackerTest` | 8 | Counters, durations, success rate, snapshot, reset |
| `SimulationMonitoringIntegrationTest` | 7 | Monitoring driven by the real cluster |
| **Total** | **91** | |

## 5.4 Results

```
[INFO] Tests run: 91, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Run with JDK 26.0.2 and Maven 3.10.0 on Windows 11.

## 5.5 Defect found by testing

While checking the stored event log of a demo run, two nodes were seen sending
heartbeats in the **same term**. This is a split-brain situation.

**Cause.** In `RaftCluster.broadcastHeartbeats()`, a leader sends one heartbeat
per follower. If a follower's reply showed a newer term, the leader stepped down
to FOLLOWER of that term but **kept looping**, and it stamped the remaining
heartbeats with its new term. Followers of that term accepted it as their leader.

**Fix.** The leader's term is captured before the loop and used for every
heartbeat in the round. The loop also stops as soon as the node is no longer
leader of that term.

**Regression test.** `RaftClusterTest.reconnectedOldLeaderDoesNotTakeOverFollowersOfTheNewTerm`
isolates leader 1, elects leader 3 in term 2, reconnects node 1, and sends one
heartbeat round. It asserts that no node accepted a term-2 heartbeat from node 1.
On the original code the test fails, because Nodes 4 and 5 accepted it. With the
fix it passes.

## 5.6 Limitations

- Timing-based behaviour (which node times out first) is random by design. Tests
  check only properties that hold in every run: one leader per term, a new leader
  exists, and the old leader steps down.
- Messages are delivered synchronously. Message delay and reordering are not
  simulated.
