package org.jahia.modules.contentintegrity.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ContentIntegrityErrorList {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityErrorList.class);

    private List<ContentIntegrityError> nestedErrors = new ArrayList<>();

    public static ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return (new ContentIntegrityErrorList()).addError(error);
    }

    public ContentIntegrityErrorList addError(ContentIntegrityError error) {
        nestedErrors.add(error);
        return this;
    }

    public List<ContentIntegrityError> getNestedErrors() {
        return nestedErrors;
    }
}
