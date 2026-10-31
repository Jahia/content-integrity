package org.jahia.modules.contentintegrity.services.exceptions;

public class ConcurrentExecutionException extends Exception {

    private static final String MESSAGE = "Impossible to run the integrity check, since another one is already running";

    public ConcurrentExecutionException() {
        super(MESSAGE);
    }
}
