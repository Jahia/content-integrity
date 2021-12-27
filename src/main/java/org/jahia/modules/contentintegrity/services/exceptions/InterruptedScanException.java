package org.jahia.modules.contentintegrity.services.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterruptedScanException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(InterruptedScanException.class);

    public InterruptedScanException() {
        super("Interrupting the scan");
    }
}
