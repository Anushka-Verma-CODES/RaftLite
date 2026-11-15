package com.raftlite.persistence;

/**
 * Thrown when the database cannot be opened, read or written.
 *
 * It is unchecked so callers are not forced to handle low-level
 * {@link java.sql.SQLException}s, but it keeps the original cause.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}
