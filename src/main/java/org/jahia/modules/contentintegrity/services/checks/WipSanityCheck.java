package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.jahia.modules.contentintegrity.services.impl.Constants.EDIT_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIAMIX_LASTPUBLISHED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_TRANSLATION;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_LANGUAGES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_ALLCONTENT;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_DISABLED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_LANG;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIAMIX_LASTPUBLISHED + "," + JAHIANT_TRANSLATION,
        ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=" + WORKINPROGRESS + "," + WORKINPROGRESS_STATUS,
        ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GTE + "=7.2.3.1"
})
public class WipSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(WipSanityCheck.class);

    private static final List<String> UNEXPECTED_PROPS_ON_I18N = Arrays.asList(WORKINPROGRESS, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES);
    public static final ContentIntegrityErrorType WIP_ON_TRANSLATION_NODE = createErrorType("WIP_ON_TRANSLATION_NODE", "Unexpected WIP property on a translation node");
    public static final ContentIntegrityErrorType WIP_LEGACY_FORMAT = createErrorType("WIP_LEGACY_FORMAT", "WIP legacy format found on the node");
    public static final ContentIntegrityErrorType WIP_UNEXPECTED_LANG = createErrorType("WIP_UNEXPECTED_LANG", "Unexpected language flagged as WIP");
    public static final ContentIntegrityErrorType WIP_MISSING_PROP = createErrorType("WIP_MISSING_PROP", "Missing WIP property");
    public static final ContentIntegrityErrorType WIP_UNEXPECTED_PROP = createErrorType("WIP_UNEXPECTED_PROP", "Unexpected WIP property");
    public static final ContentIntegrityErrorType WIP_INCONSISTENT_STATUS_PROP = createErrorType("WIP_INCONSISTENT_STATUS_PROP", "Inconsistent value for the WIP status property");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final ContentIntegrityErrorList errors = createEmptyErrorsList();
            if (node.isNodeType(JAHIANT_TRANSLATION)) {
                for (String p : UNEXPECTED_PROPS_ON_I18N) {
                    if (node.hasProperty(p)) {
                        final ContentIntegrityError error = createError(node, WIP_ON_TRANSLATION_NODE)
                                .addExtraInfo("property-name", p);
                        errors.addError(error);
                    }
                }
            } else {
                if (node.hasProperty(WORKINPROGRESS)) {
                    final ContentIntegrityError error = createError(node, WIP_LEGACY_FORMAT)
                            .addExtraInfo("unexpected-property", WORKINPROGRESS);
                    errors.addError(error);
                }
                final boolean propertyLangsIsDefined = node.hasProperty(WORKINPROGRESS_LANGUAGES);
                if (node.hasProperty(WORKINPROGRESS_STATUS)) {
                    final String status = node.getPropertyAsString(WORKINPROGRESS_STATUS);
                    switch (status) {
                        case WORKINPROGRESS_STATUS_LANG:
                            if (propertyLangsIsDefined) {
                                final Set<String> siteLanguages = node.getResolveSite().getLanguages();
                                for (JCRValueWrapper value : node.getProperty(WORKINPROGRESS_LANGUAGES).getValues()) {
                                    final String lang = value.getString();
                                    if (!siteLanguages.contains(lang)) {
                                        final ContentIntegrityError error = createError(node, WIP_UNEXPECTED_LANG)
                                                .addExtraInfo("language", lang)
                                                .addExtraInfo("site-languages", siteLanguages);
                                        errors.addError(error);
                                    }
                                }
                            } else {
                                final ContentIntegrityError error = createError(node, WIP_MISSING_PROP, String.format("Missing property %s on a node with the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_LANG));
                                errors.addError(error);
                            }
                            break;
                        case WORKINPROGRESS_STATUS_ALLCONTENT:
                            if (propertyLangsIsDefined) {
                                final ContentIntegrityError error = createError(node, WIP_UNEXPECTED_PROP, String.format("Unexpected property %s on a node with the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_ALLCONTENT));
                                errors.addError(error);
                            }
                            break;
                        case WORKINPROGRESS_STATUS_DISABLED:
                            if (propertyLangsIsDefined) {
                                final ContentIntegrityError error = createError(node, WIP_UNEXPECTED_PROP, String.format("Unexpected property %s on a node with the %s=%s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_DISABLED));
                                errors.addError(error);
                            }
                            break;
                        default:
                            errors.addError(createError(node, WIP_INCONSISTENT_STATUS_PROP, String.format("Unexpected value for the property %s", WORKINPROGRESS_STATUS))
                                    .addExtraInfo("property-value", status));
                            break;
                    }
                } else if (propertyLangsIsDefined) {
                    final ContentIntegrityError error = createError(node, WIP_UNEXPECTED_PROP, String.format("Unexpected property %s on a node without the property %s", WORKINPROGRESS_LANGUAGES, WORKINPROGRESS_STATUS));
                    errors.addError(error);
                }
            }
            return errors;
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }
}
