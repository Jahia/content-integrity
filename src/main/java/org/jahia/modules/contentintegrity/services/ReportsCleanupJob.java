package org.jahia.modules.contentintegrity.services;

import org.apache.commons.lang.math.NumberUtils;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Deletes the old scan reports stored in the JCR, in the background. Configured by the Karaf configuration
 * org.jahia.modules.contentintegrity.reports (karaf/etc/org.jahia.modules.contentintegrity.reports.cfg):
 * - retentionDays: the reports of the scans started earlier are deleted, 0 keeps them all;
 * - cleanupIntervalHours: the interval between two cleanups. A cleanup also runs as soon as the configuration changes.
 * In a cluster, only the processing server deletes reports.
 */
@Component(service = ReportsCleanupJob.class, immediate = true, configurationPid = ReportsCleanupJob.PID)
public class ReportsCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(ReportsCleanupJob.class);

    public static final String PID = "org.jahia.modules.contentintegrity.reports";
    private static final String RETENTION_DAYS = "retentionDays";
    private static final String CLEANUP_INTERVAL_HOURS = "cleanupIntervalHours";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final int DEFAULT_CLEANUP_INTERVAL_HOURS = 24;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "content-integrity-reports-cleanup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private ScheduledFuture<?> task;
    private volatile int retentionDays = DEFAULT_RETENTION_DAYS;

    @Activate
    @Modified
    public synchronized void configure(Map<String, Object> properties) {
        retentionDays = readInt(properties, RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        final int intervalHours = Math.max(1, readInt(properties, CLEANUP_INTERVAL_HOURS, DEFAULT_CLEANUP_INTERVAL_HOURS));
        if (task != null) task.cancel(false);
        task = executor.scheduleWithFixedDelay(this::cleanup, 0L, intervalHours, TimeUnit.HOURS);
        logger.info("Scan reports kept {} days, cleaned up every {} hours", retentionDays > 0 ? retentionDays : "forever,", intervalHours);
    }

    @Deactivate
    public synchronized void stop() {
        if (task != null) task.cancel(false);
        executor.shutdownNow();
    }

    private void cleanup() {
        try {
            if (retentionDays <= 0 || !SettingsBean.getInstance().isProcessingServer()) return;
            final long limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
            final int deleted = ResultsStore.deleteReportsOlderThan(limit);
            if (deleted > 0) logger.info("{} scan report(s) older than {} days deleted", deleted, retentionDays);
        } catch (RuntimeException e) {
            // The next cleanup must run anyway
            logger.error("Impossible to delete the old scan reports", e);
        }
    }

    private static int readInt(Map<String, Object> properties, String key, int defaultValue) {
        final Object value = properties == null ? null : properties.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        return value == null ? defaultValue : NumberUtils.toInt(value.toString().trim(), defaultValue);
    }
}
