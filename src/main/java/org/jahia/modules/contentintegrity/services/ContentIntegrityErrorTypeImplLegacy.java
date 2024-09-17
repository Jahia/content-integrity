package org.jahia.modules.contentintegrity.services;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class ContentIntegrityErrorTypeImplLegacy extends ContentIntegrityErrorTypeImpl {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityErrorTypeImplLegacy.class);

    private String keyLegacy;

    public ContentIntegrityErrorTypeImplLegacy(String key) {
        super(key);
    }

    public ContentIntegrityErrorTypeImplLegacy(String key, boolean isBlockingImport) {
        super(key, isBlockingImport);
    }

    public void setKeyLegacy(String k) {
        keyLegacy = k;
    }

    @Override
    public String getKey() {
        return StringUtils.defaultIfBlank(keyLegacy, super.getKey());
    }
}
