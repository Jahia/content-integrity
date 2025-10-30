package org.jahia.modules.contentintegrity.taglib;

import org.jahia.modules.contentintegrity.config.ContentIntegrityConfig;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Functions {

    private static final Logger logger = LoggerFactory.getLogger(Functions.class);

    public static String getScreenView() {
        return BundleUtils.getOsgiService(ContentIntegrityConfig.class, null).getUi();
    }
}
