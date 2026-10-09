package org.jahia.modules.contentintegrity.services.exceptions;

public class InterruptedScanException extends Exception {

    private static final String MESSAGE = "Interrupting the scan";

    public InterruptedScanException() {
        super(MESSAGE);
    }
}
