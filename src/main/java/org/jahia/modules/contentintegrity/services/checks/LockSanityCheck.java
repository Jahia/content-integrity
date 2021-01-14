package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashSet;

import static org.jahia.api.Constants.JCR_LOCKISDEEP;
import static org.jahia.api.Constants.JCR_LOCKOWNER;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE
})
public class LockSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(LockSanityCheck.class);

    private HashSet<String> lockRelatedProperties;

    @Override
    protected void activate(ComponentContext context) {
        super.activate(context);
        init();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        HashSet<String> missingProps = null;
        for (String property : lockRelatedProperties) {
            try {
                if (!node.hasProperty(property)) {
                    if (missingProps == null) missingProps = new HashSet<>();
                    missingProps.add(property);
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
        if (missingProps != null && !missingProps.isEmpty()) {
            final String msg = String.format("The following properties are missing: %s", missingProps);
            final ContentIntegrityError error = createError(node, msg);
            error.setExtraInfos(missingProps);
            return createSingleError(error);
        }

        return null;
    }

    private void init() {
        lockRelatedProperties = new HashSet<>();
        lockRelatedProperties.add("j:lockTypes");
        lockRelatedProperties.add("j:locktoken");
        lockRelatedProperties.add(JCR_LOCKISDEEP);
        lockRelatedProperties.add(JCR_LOCKOWNER);

        final AnyOfCondition anyOfCondition = new AnyOfCondition();
        for (String property : lockRelatedProperties) {
            anyOfCondition.add(new HasPropertyCondition(property));
        }
        addCondition(anyOfCondition);
    }
}
