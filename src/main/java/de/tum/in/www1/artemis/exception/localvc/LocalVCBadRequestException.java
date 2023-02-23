package de.tum.in.www1.artemis.exception.localvc;

/**
 * Exception thrown when the repository URL is not formatted correctly.
 * Corresponds to HTTP status code 400.
 */
public class LocalVCBadRequestException extends LocalVCException {

    public LocalVCBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
