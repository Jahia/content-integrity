package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections4.CollectionUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.impl.ExternalLogger;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static ContentIntegrityService getContentIntegrityService() {
        return BundleUtils.getOsgiService(ContentIntegrityService.class, null);
    }

    public static void log(String message, Logger log, ExternalLogger externalLogger) {
        if (log != null) log.info(message);
        if (externalLogger != null) externalLogger.logLine(message);
    }

    public static List<String> getChecksToExecute(ContentIntegrityService service, List<String> whiteList, List<String> blackList, ExternalLogger externalLogger) {
        if (CollectionUtils.isEmpty(whiteList) && CollectionUtils.isEmpty(blackList)) return null;

        if (CollectionUtils.isNotEmpty(whiteList)) {

            return CollectionUtils.isEmpty(blackList) ? whiteList : CollectionUtils.removeAll(whiteList, blackList)
                    .stream()
                    .map(id -> {
                        if (service.getContentIntegrityCheck(id) != null) return id;
                        externalLogger.logLine("Skipping invalid ID: " + id);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            return service.getContentIntegrityChecksIdentifiers(true).stream().filter(id -> !blackList.contains(id)).collect(Collectors.toList());
        }
    }
}
