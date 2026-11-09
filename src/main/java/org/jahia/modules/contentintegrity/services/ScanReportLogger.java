package org.jahia.modules.contentintegrity.services;

import org.jahia.modules.contentintegrity.api.ExternalLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logs the lines of a scan to another logger, and keeps them in the report of the scan: the report is written when the
 * scan starts, then updated with the new lines at most every few seconds, so that a scan which runs, or which was
 * stopped by a restart, has its log stored.
 */
public class ScanReportLogger implements ExternalLogger {

    private static final long FLUSH_INTERVAL_MS = 10000L;

    private final ExternalLogger delegate;
    private final ContentIntegrityResults report;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private volatile long lastFlush = 0L;

    /**
     * @param delegate the logger which also receives the lines, if any
     * @param report   results without errors, which identify the report of the scan, with the status RUNNING
     */
    public ScanReportLogger(ExternalLogger delegate, ContentIntegrityResults report) {
        this.delegate = delegate;
        this.report = report;
    }

    @Override
    public void logLine(String message) {
        if (delegate != null) delegate.logLine(message);
        lines.add(message);
        if (System.currentTimeMillis() - lastFlush > FLUSH_INTERVAL_MS) flush();
    }

    @Override
    public boolean includeSummary() {
        return delegate != null && delegate.includeSummary();
    }

    public ContentIntegrityResults getReport() {
        return report;
    }

    /**
     * Writes the report with the lines logged so far.
     */
    public void flush() {
        lastFlush = System.currentTimeMillis();
        ResultsStore.save(report, getLines(), false);
    }

    public List<String> getLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }
}
