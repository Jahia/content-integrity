package org.jahia.modules.contentintegrity.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ContentIntegrityErrorList {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityErrorList.class);

    private final List<ContentIntegrityError> nestedErrors = new ArrayList<>();

    public static ContentIntegrityErrorList createEmptyList() {
        return new ContentIntegrityErrorList();
    }

    public static ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return (new ContentIntegrityErrorList()).addError(error);
    }

    public ContentIntegrityErrorList addError(ContentIntegrityError error) {
        nestedErrors.add(error);
        return this;
    }

    public ContentIntegrityErrorList addAll(ContentIntegrityErrorList otherList) {
        if (otherList == null) return this;
        for (ContentIntegrityError error : otherList.getNestedErrors()) {
            addError(error);
        }
        return this;
    }

    /*
    the list should use a "add only" implementation, or this method should return a copy of the list,
    in order to prevent removing an error from the list. Since cloning would impact the overall performances,
    using a package-private visibility for the method should be enough.
     */
    List<ContentIntegrityError> getNestedErrors() {
        return nestedErrors;
    }

    public boolean hasErrors() {
        return !nestedErrors.isEmpty();
    }
}
