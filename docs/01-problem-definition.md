# 1. Problem Definition

## 1.1 Background

Distributed systems such as databases, key-value stores and cluster managers
(etcd, Consul, CockroachDB, Kafka KRaft) keep several copies of their data on
different machines so that the service survives when one machine fails. The copies
must agree on the order of updates, and the usual way to achieve that is to choose
one **leader** that coordinates all writes.

Choosing that leader is hard because the machines communicate over an unreliable
network:

- a machine can crash at any moment and come back later;
- messages can be lost, or a link between two machines can break (a *network partition*);
- there is no global clock, so a machine cannot tell a slow peer from a dead one.

If the cluster handles this badly it ends up with **no leader** (unavailable)
or **two leaders** at once (*split brain*, where data diverges).

**Raft** (Ongaro and Ousterhout, 2014) is a consensus algorithm designed to be
understandable. Its leader election uses *terms*, *randomized election timeouts*,
*majority voting* and *heartbeats* so that at most one leader exists per term and a
new leader is elected quickly after a failure.

## 1.2 Problem statement

The rules of Raft are short to state but hard to picture: what happens when a
leader crashes mid-term, when a partition splits the cluster, or when an old leader
comes back after a newer one was elected? Real deployments are hard to experiment
with, and their behaviour is hard to observe.

> **Build a lightweight, observable simulator of Raft leader election in which a
> user can run a cluster, inject failures, and see and measure how the cluster
> recovers, with every run recorded for later analysis.**

## 1.3 Objectives

1. Model Raft nodes with the four states FOLLOWER, CANDIDATE, LEADER and FAILED,
   plus the current term and the vote cast in that term.
2. Implement leader election: a node becomes a candidate, requests votes, and wins
   only with a majority of the whole cluster.
3. Implement heartbeats and randomized election timeouts so that leader failure is
   detected and a new election starts automatically.
4. Simulate an unreliable network: crashed nodes, cut links and isolated nodes.
5. Guarantee **election safety** (at most one leader per term) and check it.
6. Log every important event and compute statistics about elections and traffic.
7. **Persist** each simulation run (events, leaders, statistics) in a database so
   runs can be compared and queried.
8. Verify the behaviour with automated tests.

## 1.4 Scope

**In scope**

- Leader election, heartbeats, election timeouts and term handling as described in
  section 5.2 of the Raft paper.
- An in-process cluster (default 5 nodes) with a simulated network.
- Fault injection: crash, recover, disconnect and reconnect a link, isolate and
  reconnect a node.
- Monitoring, statistics and SQLite storage.

**Out of scope**

- Log replication and the state machine (sections 5.3 and 5.4 of the paper).
- Cluster membership changes and log compaction.
- Real network sockets: nodes run as objects in one JVM, and messages are method
  calls through `NetworkSimulator`.

## 1.5 Users

- **Students and instructors** learning distributed consensus.
- **Developers** who want to see how Raft reacts to particular failure sequences
  before working with a production implementation.

## 1.6 Success criteria

| Criterion | How it is met |
|-----------|---------------|
| A leader is elected from a healthy cluster | `ElectionManagerTest`, `RaftClusterTest` |
| A new leader is elected automatically after the leader crashes | `RaftClusterTest.timeoutElectsANewLeaderWhenTheLeaderIsCrashed` |
| A minority partition never elects a leader | `RaftClusterTest.minorityPartitionCannotElectALeader` |
| Never two leaders in one term | `recordLeader` check, DB primary key, regression test in `RaftClusterTest` |
| Every run can be retrieved from the database | `SimulationRepositoryTest` |
