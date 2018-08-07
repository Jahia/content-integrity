package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + ":Boolean=true",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_CONTENT + "," + Constants.JAHIANT_PAGE
})
public class MandatoryPropertiesCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(MandatoryPropertiesCheck.class);

    private List<String> propertiesToIgnore = Arrays.asList("jcr:predecessors", "j:nodename", "jcr:versionHistory",
            "jcr:baseVersion", "jcr:isCheckedOut", "jcr:uuid", "jcr:mergeFailed", "j:isHomePage", "j:templateNode",
            "j:tags", "j:newTag", "j:tagList", "jcr:activity", "jcr:configuration", "j:legacyRuleSettings",
            "j:processId", "jcr:description", "j:invalidLanguages", "j:workInProgress", "jcr:lockOwner",
            "jcr:configuration", "j:originWS", "j:published", "j:lastPublishedBy", "j:lastPublished",
            "jcr:lastModified", "jcr:LastModifiedBy", "jcr:lockIsDeep", "j:locktoken", "j:lockTypes", "jcr:created",
            "jcr:createdBy", "j:fullpath", "jcr:mixinTypes", "jcr:primaryType"
    );

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRSessionWrapper session = node.getSession();
            final List<String> nodeTypes;
            nodeTypes = node.getNodeTypes();
            for (String nt : nodeTypes) {
                final Collection<ExtendedPropertyDefinition> propDefs = NodeTypeRegistry.getInstance().getNodeType(nt).getPropertyDefinitionsAsMap().values();
                for (ExtendedPropertyDefinition propertyDefinition : propDefs) {
                    final String propertyName = propertyDefinition.getName();
                    Locale errorLocale = null;
                    if (propertyDefinition.isInternationalized()) {
                        errorLocale = session.getLocale();
                    }

                    if (propertiesToIgnore.contains(propertyName)) {
                        continue;
                    }

                    // Following condition checks mandatory missing properties
                    if (propertyDefinition.isMandatory()) {
                        if (propertyDefinition.getRequiredType() != PropertyType.WEAKREFERENCE &&
                                propertyDefinition.getRequiredType() != PropertyType.REFERENCE &&
                                !propertyDefinition.isProtected() &&
                                (!propertyDefinition.isInternationalized() || session.getLocale() != null) &&
                                (!node.hasProperty(propertyName) ||
                                        (!propertyDefinition.isMultiple() &&
                                                propertyDefinition.getRequiredType() != PropertyType.BINARY &&
                                                StringUtils.isEmpty(node.getProperty(propertyName).getString()))

                                )) {
                            logger.debug("Mandatory field missing on property");
                            return createSingleError(node, errorLocale, String.format("Mandatory field without value (%s)", propertyName));
                        } else if (propertyDefinition.getRequiredType() == PropertyType.WEAKREFERENCE ||
                                propertyDefinition.getRequiredType() == PropertyType.REFERENCE) {
                            try {
                                node.getProperty(propertyName).getNode();
                            } catch (PathNotFoundException pnfe) {
                                logger.debug("Mandatory field missing on reference property");
                                return createSingleError(node, errorLocale, String.format("Mandatory field without value (%s)", propertyName));
                            } catch (ItemNotFoundException infe) {
                                logger.debug("Mandatory field on reference property is set toward a no-more existing node");
                                createError(node, errorLocale, String.format("Mandatory reference broken (%s)", propertyName));
                            }
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }
        return null;
    }
}
