package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIAMIX_LASTPUBLISHED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_TRANSLATION;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_LANGUAGES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_ALLCONTENT;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_DISABLED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS_LANG;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIAMIX_LASTPUBLISHED + "," + JAHIANT_TRANSLATION,
        ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=" + WORKINPROGRESS + "," + WORKINPROGRESS_STATUS + "," + WORKINPROGRESS_LANGUAGES,
        ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GTE + "=7.2.3.1"
})
public class WipSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(WipSanityCheck.class);

    private static final List<String> UNEXPECTED_PROPS_ON_I18N = Arrays.asList(WORKINPROGRESS, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES);
    public static final ContentIntegrityErrorType WIP_ON_TRANSLATION_NODE = createErrorType("WIP_ON_TRANSLATION_NODE", "Unexpected WIP property on a translation node");
    public static final ContentIntegrityErrorType WIP_LEGACY_FORMAT = createErrorType("WIP_LEGACY_FORMAT", "WIP legacy format found on the node");
    public static final ContentIntegrityErrorType WIP_UNEXPECTED_LANG = createErrorType("WIP_UNEXPECTED_LANG", "Unexpected language flagged as WIP");
    public static final ContentIntegrityErrorType WIP_MISSING_PROP = createErrorType("WIP_MISSING_PROP", "Missing WIP property");
    public static final ContentIntegrityErrorType WIP_UNEXPECTED_PROP = createErrorType("WIP_UNEXPECTED_PROP", "Unexpected WIP property");
    public static final ContentIntegrityErrorType WIP_INCONSISTENT_STATUS_PROP = createErrorType("WIP_INCONSISTENT_STATUS_PROP", "Inconsistent value for the WIP status property");
    public static final ContentIntegrityErrorType WIP_IN_LIVE = createErrorType("WIP_IN_LIVE", "WIP property in the live workspace");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            // The work in progress state is an editing state: no WIP property is expected in live
            if (JCRUtils.isInLiveWorkspace(node)) {
                final List<String> liveWipProperties = new ArrayList<>();
                for (String p : UNEXPECTED_PROPS_ON_I18N) {
                    if (node.hasProperty(p)) liveWipProperties.add(p);
                }
                return liveWipProperties.isEmpty() ? null :
                        createSingleError(createError(node, WIP_IN_LIVE).addExtraInfo("properties", liveWipProperties));
            }

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
            return createSingleError(createFrameworkError(node, e));
        }
    }

    /*
     * From the jcr-scripts fix, which removed the work in progress state. Each error type now removes only what
     * is inconsistent:
     * - a WIP property on a translation node, or the legacy j:workInProgress property, is removed;
     * - a language which is not a language of the site is removed from the WIP languages;
     * - an inconsistent WIP status removes the WIP state of the node;
     * - a WIP property found in live is removed (from the clean-recursive script).
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final ContentIntegrityErrorType errorType = error.getErrorType();
        if (errorType.equals(WIP_ON_TRANSLATION_NODE)) {
            return removeProperties(node, (String) error.getExtraInfo("property-name"));
        }
        if (errorType.equals(WIP_LEGACY_FORMAT)) {
            return removeProperties(node, WORKINPROGRESS);
        }
        if (errorType.equals(WIP_UNEXPECTED_LANG)) {
            final String language = (String) error.getExtraInfo("language");
            if (language == null || !node.hasProperty(WORKINPROGRESS_LANGUAGES)) return false;
            final List<Value> kept = new ArrayList<>();
            for (JCRValueWrapper value : node.getProperty(WORKINPROGRESS_LANGUAGES).getValues()) {
                if (!language.equals(value.getString())) kept.add(value);
            }
            if (kept.isEmpty()) return removeProperties(node, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES);
            RepairUtils.runWithListenersDisabled(() -> {
                node.setProperty(WORKINPROGRESS_LANGUAGES, kept.toArray(new Value[0]));
                node.saveSession();
            });
            return true;
        }
        if (errorType.equals(WIP_MISSING_PROP)) {
            return removeProperties(node, WORKINPROGRESS_STATUS);
        }
        if (errorType.equals(WIP_UNEXPECTED_PROP)) {
            return removeProperties(node, WORKINPROGRESS_LANGUAGES);
        }
        if (errorType.equals(WIP_INCONSISTENT_STATUS_PROP)) {
            return removeProperties(node, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES);
        }
        if (errorType.equals(WIP_IN_LIVE)) {
            return removeProperties(node, WORKINPROGRESS_STATUS, WORKINPROGRESS_LANGUAGES, WORKINPROGRESS);
        }
        return false;
    }

    private boolean removeProperties(JCRNodeWrapper node, String... propertyNames) throws RepositoryException {
        if (propertyNames == null || propertyNames.length == 0 || propertyNames[0] == null) return false;
        RepairUtils.runWithListenersDisabled(() -> {
            for (String propertyName : propertyNames) {
                if (node.hasProperty(propertyName)) node.getProperty(propertyName).remove();
            }
            node.saveSession();
        });
        return true;
    }
}
