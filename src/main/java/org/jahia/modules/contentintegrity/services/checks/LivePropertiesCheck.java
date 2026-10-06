package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the nodes flagged with jmix:liveProperties, in both workspaces, with or without a value for
 * j:liveProperties (from the clean-recursive script).
 */
@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + LivePropertiesCheck.JMIX_LIVE_PROPERTIES,
        ContentIntegrityCheck.ENABLED + "=false"
})
public class LivePropertiesCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(LivePropertiesCheck.class);

    static final String JMIX_LIVE_PROPERTIES = "jmix:liveProperties";
    private static final String J_LIVE_PROPERTIES = "j:liveProperties";

    public static final ContentIntegrityErrorType LIVE_PROPERTIES = createErrorType("LIVE_PROPERTIES", String.format("Node flagged with %s, with a value for %s", JMIX_LIVE_PROPERTIES, J_LIVE_PROPERTIES));
    public static final ContentIntegrityErrorType EMPTY_LIVE_PROPERTIES = createErrorType("EMPTY_LIVE_PROPERTIES", String.format("Node flagged with %s, without a value for %s", JMIX_LIVE_PROPERTIES, J_LIVE_PROPERTIES));

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final List<String> liveProperties = new ArrayList<>();
            if (node.hasProperty(J_LIVE_PROPERTIES)) {
                for (JCRValueWrapper value : node.getProperty(J_LIVE_PROPERTIES).getValues()) {
                    liveProperties.add(value.getString());
                }
            }
            if (liveProperties.isEmpty()) {
                return createSingleError(createError(node, EMPTY_LIVE_PROPERTIES));
            }
            return createSingleError(createError(node, LIVE_PROPERTIES)
                    .addExtraInfo("live-properties", liveProperties));
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, e));
        }
    }

    /*
     * From the clean-recursive script: j:liveProperties and the mixin jmix:liveProperties are removed.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!error.getErrorType().equals(LIVE_PROPERTIES) && !error.getErrorType().equals(EMPTY_LIVE_PROPERTIES)) return false;
        RepairUtils.runWithListenersDisabled(() -> {
            if (node.hasProperty(J_LIVE_PROPERTIES)) node.getProperty(J_LIVE_PROPERTIES).remove();
            if (node.isNodeType(JMIX_LIVE_PROPERTIES)) node.removeMixin(JMIX_LIVE_PROPERTIES);
            node.saveSession();
        });
        return true;
    }
}
