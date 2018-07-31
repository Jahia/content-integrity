package org.jahia.modules.contentintegrity.services;

import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static ContentIntegrityService getContentIntegrityService() {
        return BundleUtils.getOsgiService(ContentIntegrityService.class, null);
    }
}
