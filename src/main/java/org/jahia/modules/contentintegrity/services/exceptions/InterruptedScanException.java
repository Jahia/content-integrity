package org.jahia.modules.contentintegrity.services.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterruptedScanException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(InterruptedScanException.class);
    private static final String MESSAGE = "Interrupting the scan";

    public InterruptedScanException() {
        super(MESSAGE);
    }
}
