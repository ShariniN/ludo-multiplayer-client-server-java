package ludo.db;

/** Unchecked wrapper for storage failures, so callers outside the Database Tier never see java.sql. */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
