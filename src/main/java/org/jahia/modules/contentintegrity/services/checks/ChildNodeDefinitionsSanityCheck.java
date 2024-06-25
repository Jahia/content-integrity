package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=rep:root"
})
public class ChildNodeDefinitionsSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(ChildNodeDefinitionsSanityCheck.class);

    public static final ContentIntegrityErrorType NOT_ALLOWED_BY_PARENT_DEF = createErrorType("NOT_ALLOWED_BY_PARENT_DEF",
            "The node is not allowed as a child node of its current parent node", true);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRNodeWrapper parent = node.getParent();
            /*
            https://github.com/Jahia/jahia/blob/JAHIA_8_1_2_0/core/src/main/java/org/jahia/services/content/nodetypes/ExtendedNodeType.java#L1103
            if the node definition declares at least one i18n property, then jmix:i18n is added to the list of supertypes,
            so that the node can accept translation sub nodes, no matter the definition.
            If not, let's continue with the regular test, in case such subnode would be anyway allowed, by another mean that jmix:i18n
             */
            if (node.isNodeType(Constants.JAHIANT_TRANSLATION) && parent.isNodeType(Constants.JAHIA_MIX_I18N)) return null;

            final List<String> types = new ArrayList<>();
            types.add(node.getPrimaryNodeTypeName());
            types.addAll(Arrays.stream(node.getMixinNodeTypes())
                    .map(ExtendedNodeType::getName)
                    .collect(Collectors.toList()));
            final String name = node.getName();
            if (types.stream().noneMatch(type -> isChildAllowed(parent, name, type)))
                return createSingleError(createError(node, NOT_ALLOWED_BY_PARENT_DEF));
        } catch (RepositoryException re) {
            logger.error("Impossible to validate " + node.getPath(), re);
        }

        return null;
    }

    private boolean isChildAllowed(JCRNodeWrapper parent, String name, String type) {
        try {
            parent.getApplicableChildNodeDefinition(name, type);
        } catch (ConstraintViolationException cve) {
            return false;
        } catch (RepositoryException e) {
            logger.error(String.format("Error while checking if %s accepts a child of type %s named %s",
                    parent.getPath(), type, name), e);
        }

        /*
        If there's a RepositoryException of another type than ConstraintViolationException,
        let's return true, so that no ContentIntegrityError is created
         */
        return true;
    }
}
