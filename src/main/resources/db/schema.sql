-- RaftLite database schema (SQLite).
-- Every statement is idempotent, so the schema can be applied on each start.

-- One row per execution of the simulator.
CREATE TABLE IF NOT EXISTS simulation_run (
    run_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at    TEXT    NOT NULL,              -- ISO-8601 instant
    finished_at   TEXT,                          -- NULL while the run is in progress
    cluster_size  INTEGER NOT NULL CHECK (cluster_size > 0),
    description   TEXT    NOT NULL
);

-- The chronological event log produced by the monitoring module.
CREATE TABLE IF NOT EXISTS simulation_event (
    event_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id        INTEGER NOT NULL REFERENCES simulation_run (run_id) ON DELETE CASCADE,
    sequence_no   INTEGER NOT NULL,              -- position in the log, starts at 1
    occurred_at   TEXT    NOT NULL,              -- ISO-8601 instant
    event_type    TEXT    NOT NULL,              -- SimulationEventType name
    node_id       INTEGER NOT NULL CHECK (node_id >= 0),
    term          INTEGER,                       -- NULL when the event has no term
    description   TEXT    NOT NULL,
    UNIQUE (run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_event_run_type
    ON simulation_event (run_id, event_type);

-- Which node won each term. The primary key enforces Raft's
-- "at most one leader per term" rule in the database as well.
CREATE TABLE IF NOT EXISTS leader_history (
    run_id        INTEGER NOT NULL REFERENCES simulation_run (run_id) ON DELETE CASCADE,
    term          INTEGER NOT NULL CHECK (term > 0),
    leader_id     INTEGER NOT NULL,
    PRIMARY KEY (run_id, term)
);

-- Final statistics of a run (one row per run).
CREATE TABLE IF NOT EXISTS run_statistics (
    run_id                          INTEGER PRIMARY KEY REFERENCES simulation_run (run_id) ON DELETE CASCADE,
    total_elections                 INTEGER NOT NULL,
    successful_elections            INTEGER NOT NULL,
    failed_elections                INTEGER NOT NULL,
    leader_changes                  INTEGER NOT NULL,
    heartbeats_sent                 INTEGER NOT NULL,
    heartbeats_received             INTEGER NOT NULL,
    election_timeouts               INTEGER NOT NULL,
    node_failures                   INTEGER NOT NULL,
    node_recoveries                 INTEGER NOT NULL,
    messages_blocked                INTEGER NOT NULL,
    election_duration_samples       INTEGER NOT NULL,
    total_election_duration_millis  INTEGER NOT NULL,
    min_election_duration_millis    INTEGER NOT NULL,
    max_election_duration_millis    INTEGER NOT NULL,
    last_election_duration_millis   INTEGER NOT NULL
);
