# RaftLite

RaftLite is a Java simulator of **leader election in the Raft consensus algorithm**.
It runs a cluster of nodes inside one JVM, makes them elect a leader, keeps the
leader alive with heartbeats, and lets you inject faults (node crashes, broken
network links, isolated leaders) to watch the cluster recover. Every run is
logged, measured and stored in a SQLite database.

## Features

- **Leader election**: randomized election timeouts, one vote per node per term,
  majority rule, and step-down when a node sees a higher term.
- **Heartbeats**: the leader sends a heartbeat every 500 ms; a follower that hears
  nothing for 1.5 to 2.5 s starts a new election.
- **Fault simulation**: crash and recover nodes, cut and restore single links,
  isolate a whole node.
- **Safety checks**: at most one leader per term, enforced in memory
  (`ElectionSafetyException`) and in the database (primary key on `(run_id, term)`).
- **Monitoring**: a chronological event log and statistics (elections, success
  rate, heartbeats, timeouts, failures, blocked messages, election duration).
- **Database integration**: each run, its full event log, its leader history and
  its statistics are saved to SQLite in one transaction and can be queried later.
- **Tests**: 91 JUnit 5 tests covering every module.

## Requirements

| Tool  | Version                                      |
|-------|----------------------------------------------|
| JDK   | 26 (set in `pom.xml` via `maven.compiler.release`) |
| Maven | 3.9 or newer                                 |

Nothing else needs to be installed: SQLite is embedded through the
`sqlite-jdbc` driver, and the database file is created on first run.

## Quick start

Build and run all tests:

```bash
mvn clean test
```

Run the demo scenario. It writes to `raftlite.db` in the current folder:

```bash
mvn compile exec:java
```

Store the run in a different database file:

```bash
mvn compile exec:java -Dexec.args="runs/my-run.db"
```

Each run appends to the database, so running the demo several times builds up a
history of runs. The demo takes about 20 seconds because it waits for real
timeouts.

## Project structure

```
src/main/java/com/raftlite/
├── Main.java                 Demo scenario + saves the run to the database
├── RaftCluster.java          Owns nodes, network, schedulers; fault-injection API
├── RaftNode.java             One server: state, term, vote, Raft rules
├── ElectionManager.java      Runs an election, majority check, leader history
├── NetworkSimulator.java     Delivers messages, blocks failed nodes / cut links
├── RaftMessage.java, MessageType.java, VoteRequest.java, VoteResponse.java
├── NodeState.java            FOLLOWER, CANDIDATE, LEADER, FAILED
├── ElectionListener.java, SimulationListener.java   Observer interfaces
├── ElectionException.java, ElectionSafetyException.java
├── monitoring/               Event log and statistics
│   ├── SimulationMonitor.java, EventLogger.java, StatisticsTracker.java
│   └── SimulationEvent.java, SimulationEventType.java, SimulationStatistics.java
└── persistence/              SQLite storage (JDBC)
    ├── DatabaseManager.java, SimulationRepository.java
    └── SimulationRun.java, PersistenceException.java
src/main/resources/db/schema.sql    Database schema
src/test/java/...                   JUnit 5 tests
docs/                               Project documentation
```

## Documentation

| Document | Contents |
|----------|----------|
| [Problem definition](docs/01-problem-definition.md) | Background, problem statement, objectives, scope |
| [System design](docs/02-system-design.md) | Architecture, class and sequence diagrams, threading, design decisions |
| [Functionality](docs/03-functionality.md) | Features, demo scenario, sample output |
| [Database integration](docs/04-database.md) | Schema, ER diagram, data flow, example SQL queries |
| [Testing](docs/05-testing.md) | Test strategy, test inventory, results, bug found by testing |

## Team

| Module | Contributor |
|--------|-------------|
| Project setup, cluster and heartbeat mechanism, election engine (`RaftNode`, `ElectionManager`) | Anushka Verma |
| Communication and fault simulation (`NetworkSimulator`, timeouts, crash/recovery) | Rajat |
| Monitoring, event logging, statistics and tests (`monitoring` package) | Jahanvi Wadhwa |
