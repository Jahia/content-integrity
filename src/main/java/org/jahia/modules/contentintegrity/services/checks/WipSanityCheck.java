package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Set;

import static org.jahia.api.Constants.EDIT_WORKSPACE;
import static org.jahia.api.Constants.JAHIAMIX_LASTPUBLISHED;
import static org.jahia.api.Constants.JAHIANT_TRANSLATION;
import static org.jahia.api.Constants.WORKINPROGRESS;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_ALLCONTENT;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_LANG;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_DISABLED;
import static org.jahia.api.Constants.WORKINPROGRESS_LANGUAGES;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIAMIX_LASTPUBLISHED + "," + JAHIANT_TRANSLATION,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + WORKINPROGRESS + "," + WORKINPROGRESS_STATUS,
        ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GTE + "=7.2.3.1"
})
public class WipSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(WipSanityCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            ContentIntegrityErrorList errors = null;
            if (node.isNodeType(JAHIANT_TRANSLATION)) {
                for (String p : Arrays.asList(WORKINPROGRESS, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES)) {
                    if (node.hasProperty(p)) {
                        final ContentIntegrityError error = createError(node, String.format("Unexpected property on a translation node: %s", p));
                        errors = appendError(errors, error);
                    }
                }
            } else {
                if (node.hasProperty(WORKINPROGRESS)) {
                    final ContentIntegrityError error = createError(node, "WIP legacy format found on the node");
                    errors = appendError(errors, error);
                }
                final boolean propertyLangsIsDefined = node.hasProperty(WORKINPROGRESS_LANGUAGES);
                if (node.hasProperty(WORKINPROGRESS_STATUS)) {
                    switch (node.getPropertyAsString(WORKINPROGRESS_STATUS)) {
                        case WORKINPROGRESS_STATUS_LANG:
                            if (node.hasProperty(WORKINPROGRESS_LANGUAGES)) {
                                final Set<String> siteLanguages = node.getResolveSite().getLanguages();
                                for (JCRValueWrapper value : node.getProperty(WORKINPROGRESS_LANGUAGES).getValues()) {
                                    final String lang = value.getString();
                                    if (!siteLanguages.contains(lang)) {
                                        final ContentIntegrityError error = createError(node, String.format("Unexpected language flagged as WIP: %s", lang));
                                        errors = appendError(errors, error);
                                    }
                                }
                            } else {
                                final ContentIntegrityError error = createError(node, String.format("Unexpected missing property %s on a node without the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_LANG));
                                errors = appendError(errors, error);
                            }
                            break;
                        case WORKINPROGRESS_STATUS_ALLCONTENT:
                            if (propertyLangsIsDefined) {
                                final ContentIntegrityError error = createError(node, String.format("Unexpected property %s on a node without the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_ALLCONTENT));
                                errors = appendError(errors, error);
                            }
                            break;
                        case WORKINPROGRESS_STATUS_DISABLED:
                            if (propertyLangsIsDefined) {
                                final ContentIntegrityError error = createError(node, String.format("Unexpected property %s on a node without the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_DISABLED));
                                errors = appendError(errors, error);
                            }
                            break;
                    }
                } else if (propertyLangsIsDefined) {
                    final ContentIntegrityError error = createError(node, String.format("Unexpected property %s on a node without the property %s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS));
                    errors = appendError(errors, error);
                }
            }
            return errors;
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }
}
