package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_MIXINTYPES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PRIMARYTYPE;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class UndeclaredNodeTypesCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UndeclaredNodeTypesCheck.class);

    public static final ContentIntegrityErrorType UNDECLARED_NODE_TYPE = createErrorType("UNDECLARED_NODE_TYPE", "Undeclared type", true);

    private final Set<String> existingNodeTypes = new HashSet<>();
    private final Set<String> missingNodeTypes = new HashSet<>();
    private final Set<String> existingMixins = new HashSet<>();
    private final Set<String> missingMixins = new HashSet<>();

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        existingNodeTypes.clear();
        existingMixins.clear();
        missingNodeTypes.clear();
        missingMixins.clear();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        final String pt = node.getPropertyAsString(JCR_PRIMARYTYPE);
        checkNodeType(node, errors, existingNodeTypes, missingNodeTypes, "primary type", false, pt);
        try {
            if (node.hasProperty(JCR_MIXINTYPES)) {
                final String[] types = Arrays.stream(node.getProperty(JCR_MIXINTYPES).getValues())
                        .map(getStringValue())
                        .filter(Objects::nonNull)
                        .toArray(String[]::new);
                checkNodeType(node, errors, existingMixins, missingMixins, "mixin type", true, types);
            }
        } catch (RepositoryException re) {
            logger.error("Impossible to read the mixins of the node " + node, re);
        }
        return errors;
    }

    private Function<JCRValueWrapper, String> getStringValue() {
        return jcrValueWrapper -> {
            try {
                return jcrValueWrapper.getString();
            } catch (RepositoryException e) {
                logger.error("", e);
                return null;
            }
        };
    }

    private void checkNodeType(JCRNodeWrapper node, ContentIntegrityErrorList errors,
                               Set<String> existingTypes, Set<String> missingTypes,
                               String text, boolean isMixin,
                               String... types) {
        for (String type : types) {
            if (existingTypes.contains(type)) continue;

            if (!missingTypes.contains(type)) {
                try {
                    final ExtendedNodeType nodeType = NodeTypeRegistry.getInstance().getNodeType(type);
                    if (nodeType.isMixin() == isMixin) {
                        existingTypes.add(type);
                        continue;
                    } else {
                        missingTypes.add(type);
                    }
                } catch (NoSuchNodeTypeException e) {
                    missingTypes.add(type);
                }
            }
            errors.addError(createError(node, UNDECLARED_NODE_TYPE, String.format("Undeclared %s", text))
                    .addExtraInfo(text, type));
        }
    }


}
