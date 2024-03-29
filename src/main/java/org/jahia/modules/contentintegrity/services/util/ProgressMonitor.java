package org.jahia.modules.contentintegrity.services.util;

import org.jahia.modules.contentintegrity.api.ExternalLogger;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ProgressMonitor {
    private static final ProgressMonitor instance = new ProgressMonitor();
    private static final long DISPLAY_INTERVAL_MS = 5000L;

    private Logger logger;
    private ExternalLogger externalLogger;
    private String message;
    private long counter;
    private long targetCount;
    private long lastMoment;
    private long firstMoment;
    private long lastCounter;

    private ProgressMonitor() {
    }

    public static ProgressMonitor getInstance() {
        return instance;
    }

    public void init(long targetCount, String message, Logger logger, ExternalLogger externalLogger) {
        this.targetCount = targetCount;
        this.message = message;
        this.logger = logger;
        this.externalLogger = externalLogger;
        counter = 0L;
        lastCounter = -1L;
        lastMoment = System.currentTimeMillis();
        firstMoment = System.currentTimeMillis();
    }

    public void progress() {
        final long now = System.currentTimeMillis();
        counter++;
        final boolean doDisplay = counter == 1 || counter == targetCount || (now - lastMoment) / DISPLAY_INTERVAL_MS > 0;
        if (doDisplay) {
            // Percentage
            final String percent = String.format("%2.0f%%", (100.0f * counter) / targetCount);

            // Calculate rate
            final String rate;
            if (lastCounter >= 0 && now > lastMoment) {
                rate = String.format(", %.0f/s", 1000.0f * (counter - lastCounter) / (now - lastMoment));
            } else {
                rate = "";
            }

            if (counter == 1) {
                firstMoment = now;
            }
            final boolean longEnoughForETA = (now - firstMoment) / DISPLAY_INTERVAL_MS >= 2;

            final String etaText;
            if (longEnoughForETA && counter > 1) {
                final float estimatedDurationMs = (float) (now - firstMoment) * targetCount / (counter - 1);
                final Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(firstMoment);
                cal.add(Calendar.MILLISECOND, (int) estimatedDurationMs);
                etaText = ", eta=" + new SimpleDateFormat("HH:mm:ss").format(cal.getTime());
            } else {
                etaText = "";
            }

            final String effectiveMessage = message + " (" + counter + "/" + targetCount + ", " + percent + rate + etaText + ")";
            Utils.log(effectiveMessage, logger, externalLogger);
            // Remember last point displayed
            lastMoment = now;
            lastCounter = counter;
        }
    }

    public long getCounter() {
        return counter;
    }
}
