package org.jahia.modules.contentintegrity.services.util;

import org.slf4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ProgressMonitor {
    private static final ProgressMonitor instance = new ProgressMonitor();
    private static final long DISLAY_INTERVAL_MS = 5000;

    private Logger logger;
    private String message;
    private int counter;
    private long targetCount;
    private long lastMoment;
    private long firstMoment;
    private int lastCounter;

    private ProgressMonitor() {
    }

    public static ProgressMonitor getInstance() {
        return instance;
    }

    public void init(long targetCount, String message, Logger logger) {
        this.targetCount = targetCount;
        this.message = message;
        this.logger = logger;
        counter = 0;
        lastCounter = -1;
        lastMoment = System.currentTimeMillis();
        firstMoment = System.currentTimeMillis();
    }

    public void progress() {
        final long now = System.currentTimeMillis();
        counter++;
        final boolean doDisplay = counter == 1 || counter == targetCount || (now - lastMoment) / DISLAY_INTERVAL_MS > 0;
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
            final boolean longEnoughForETA = (now - firstMoment) / DISLAY_INTERVAL_MS >= 2;

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
            logger.info(effectiveMessage);
            System.out.println(effectiveMessage);
            // Remember last point displayed
            lastMoment = now;
            lastCounter = counter;
        }
    }
}
