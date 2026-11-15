package com.raftlite.persistence;

import java.time.Instant;
import java.util.Optional;

/**
 * One stored execution of the simulator (a row of {@code simulation_run}).
 *
 * @param finishedAt null while the run has not been completed
 */
public record SimulationRun(long runId,
                            Instant startedAt,
                            Instant finishedAt,
                            int clusterSize,
                            String description) {

    public Optional<Instant> getFinishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public boolean isFinished() {
        return finishedAt != null;
    }
}
