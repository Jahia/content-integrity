package org.jahia.modules.contentintegrity.services.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurrentExecutionException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentExecutionException.class);
    private static final String MESSAGE = "Impossible to run the integrity check, since another one is already running";

    public ConcurrentExecutionException() {
        super(MESSAGE);
    }
}
