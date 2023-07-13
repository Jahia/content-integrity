package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.jahia.api.Constants.JAHIAMIX_LASTPUBLISHED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JMIX_DELETED_CHILDREN;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JMIX_LIVE_PROPERTIES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_LIVE_PROPERTIES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.ROOT_NODE_PATH;
import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.LIVE_WORKSPACE
})
public class PublicationSanityLiveCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable, ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSanityLiveCheck.class);

    private static final String DEEP_COMPARE_PUBLISHED_NODES = "deep-compare-published-nodes";
    private static final String LIVE_MIXINS_DECLARATION_PREFIX = "jcr:mixinTypes=";

    private static final List<String> DEFAULT_ONLY_MIXINS = Collections.singletonList(JMIX_DELETED_CHILDREN);
    /*
    Properties which can be defined on either workspace, and be missing on the other one.

    Lock related properties are ignored because they are set on a single WS (usually the default WS), and do not alter the publication status
     */
    private static final Collection<String> IGNORED_WS_ONLY_PROPS = Arrays.asList(Constants.JCR_LOCK_OWNER, Constants.J_LOCK_TYPES, Constants.J_LOCKTOKEN, Constants.JCR_LOCK_IS_DEEP, Constants.FULLPATH, Constants.NODENAME);
    /*
     */
    private static final Collection<String> IGNORED_DEFAULT_ONLY_PROPS = CollectionUtils.union(Arrays.asList(Constants.J_DELETED_CHILDREN), IGNORED_WS_ONLY_PROPS);
    /*
    J_LIVE_PROPERTIES is set on the node only in the live WS to keep track of the UGC properties
    NODENAME is sometimes missing in the default WS. Since it reflects the node name, let's ignore it
     */
    private static final Collection<String> IGNORED_LIVE_ONLY_PROPS = CollectionUtils.union(Arrays.asList(J_LIVE_PROPERTIES), IGNORED_WS_ONLY_PROPS);
    /*
    JCR_LASTMODIFIED / JCR_LASTMODIFIEDBY are not compared, because the node can be updated in live when writing UCG properties
    Versioning related properties are not compared because each workspace works with its own graph
    JCR_MIXINTYPES might differ if some mixins are added in live. Mixins are compared separately, without using this property
     */
    private static final Collection<String> NOT_COMPARED_PROPERTIES = Arrays.asList(Constants.JCR_LASTMODIFIED, Constants.JCR_LASTMODIFIEDBY,
            Constants.JCR_BASEVERSION, Constants.JCR_PREDECESSORS,
            Constants.JCR_MIXINTYPES, Constants.FULLPATH, Constants.NODENAME,
            Constants.J_LOCK_TYPES);

    private final ContentIntegrityCheckConfiguration configurations;

    private enum ErrorType {NO_DEFAULT_NODE, MISSING_PROP_LIVE, MISSING_PROP_DEFAULT, DIFFERENT_PROP_VAL, DIFFERENT_MIXINS}

    public PublicationSanityLiveCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(DEEP_COMPARE_PUBLISHED_NODES, false, BOOLEAN_PARSER, "If true, the value of every property will be compared between default and live on the nodes without pending modification");
        // TODO: for multi-value properties, compare the order of the values
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean isDeepComparePublishedNodes() {
        return (boolean) configurations.getParameter(DEEP_COMPARE_PUBLISHED_NODES);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRSessionWrapper defaultSession = JCRUtils.getSystemSession(Constants.EDIT_WORKSPACE, true);
            if (JCRUtils.isUGCNode(node)) {
                // UGC
                // TODO: check if there's a node with the same ID in default
                return null;
            }
            final JCRNodeWrapper defaultNode;
            try {
                defaultNode = defaultSession.getNodeByIdentifier(node.getIdentifier());
            } catch (ItemNotFoundException infe) {
                boolean isUGC = false;

                if (node.isNodeType(Constants.JAHIANT_TRANSLATION)) {
                    /*
                    When a node has no i18n property in default, and an i18n property is written in live,
                    the translation node is created in live, but it has no j:originWS property
                    */

                    isUGC = true;
                    final PropertyIterator properties = node.getProperties();
                    while (properties.hasNext()) {
                        /*
                        Known issue: UGC i18n properties are not tracked in j:liveProperties ,
                        neither on the translation node itself or on the parent node.
                        As a consequence, if this error is raised on a jnt:translation node, this might be a false positive
                        We should consider that the node is not UGC if we find a property which is not named jcr:* AND is not tracked in j:liveProperties
                        TODO: handle this correctly if the tracking of such property gets fixed in the product
                         */
                        final Property property = properties.nextProperty();
                        if (!StringUtils.startsWith(property.getName(), Constants.PROPERTY_DEFINITION_NAME_JCR_PREFIX)) {
                            isUGC = false;
                            break;
                        }
                    }
                } else if (node.isNodeType(Constants.JAHIANT_PERMISSION) && StringUtils.startsWith(node.getPath(), Constants.MODULES_TREE_PATH)) {
                    /*
                    It seems that some permissions are autocreated when the module is installed,
                    in the live workspace, but they do not have any j:originWS property
                     */
                    isUGC = true;
                }

                if (isUGC) return null;

                if (!node.isNodeType(JAHIAMIX_LASTPUBLISHED)) {
                    final JCRNodeWrapper publicationRoot = JCRUtils.runJcrSupplierCallBack(() -> JCRContentUtils.getParentOfType(node, JAHIAMIX_LASTPUBLISHED), null, false);
                    if (publicationRoot != null) {
                        // The node is a technical node attached to a publication compliant node  (such as access rights, references in text, ...)
                        final JCRNodeWrapper publicationRootDefault = JCRUtils.runJcrSupplierCallBack(() -> defaultSession.getNodeByIdentifier(publicationRoot.getIdentifier()), null, false);
                        if (publicationRootDefault == null) {
                            // The parent itself holds the error, reporting it again on the technical node would be too verbose
                            return null;
                        }
                        final Locale technicalNodeLocale = JCRUtils.getTechnicalNodeLocale(node);
                        final JCRNodeWrapper nodeToCheck;
                        if (technicalNodeLocale != null) {
                            nodeToCheck = JCRUtils.getI18NWrapper(publicationRootDefault, technicalNodeLocale);
                            if (nodeToCheck == null) {
                                // The technical node is related to a locale for which no translation subnode exists. That's another error for which a dedicated check should be implemented (TODO)
                                // Example: a jnt:referenceInField node, with j:fieldName=text_de and no sibling node j:translation_de
                                return null;
                            }
                        } else {
                            nodeToCheck = publicationRootDefault;
                        }
                        // The fact that the node is missing in default might be related to the pending modifications
                        if (JCRUtils.hasPendingModifications(nodeToCheck)) return null;
                    }
                }

                final String msg = "Found not-UGC node which exists only in live";
                final ContentIntegrityError error = createError(node, msg)
                        .setErrorType(ErrorType.NO_DEFAULT_NODE);
                return createSingleError(error);
            }
            final ContentIntegrityErrorList errors = createEmptyErrorsList();
            deepComparePublishedNodes(defaultNode, node, errors);
            return errors;
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private void deepComparePublishedNodes(JCRNodeWrapper defaultNode, JCRNodeWrapper liveNode, ContentIntegrityErrorList errors) {
        if (!isDeepComparePublishedNodes()) return;

        /*
        The repository root is not auto published.
        When ACL are defined on the repository root, jmix:accessControlled is added to the node in default , but not in live,
        what makes differences on the property jcr:mixinTypes
        */
        if (StringUtils.equals(defaultNode.getPath(), ROOT_NODE_PATH)) return;

        if (JCRUtils.hasPendingModifications(defaultNode, false, true)) return;

        try {
            final PropertyIterator properties = defaultNode.getRealNode().getProperties();
            while (properties.hasNext()) {
                final Property defaultProperty = properties.nextProperty();
                final String pName = defaultProperty.getName();
                if (!liveNode.getRealNode().hasProperty(pName)) {
                    if (!IGNORED_DEFAULT_ONLY_PROPS.contains(pName)) {
                        errors.addError(createError(liveNode, "Missing property in live on a published node")
                                .setErrorType(ErrorType.MISSING_PROP_LIVE)
                                .addExtraInfo("property name", pName));
                    }
                } else if (!NOT_COMPARED_PROPERTIES.contains(pName) &&
                        !JCRUtils.propertyValueEquals(defaultProperty, liveNode.getRealNode().getProperty(pName))) {
                    errors.addError(createError(liveNode, "Different value for a property in default and live on a published node")
                            .setErrorType(ErrorType.DIFFERENT_PROP_VAL)
                            .addExtraInfo("property name", pName));
                }
            }
            final PropertyIterator liveProperties = liveNode.getRealNode().getProperties();
            Set<String> ugcProperties = null;
            while (liveProperties.hasNext()) {
                final Property liveProperty = liveProperties.nextProperty();
                final String pName = liveProperty.getName();
                if (!IGNORED_LIVE_ONLY_PROPS.contains(pName) && !defaultNode.getRealNode().hasProperty(pName)) {
                    if (ugcProperties == null) {
                        ugcProperties = getUgcProperties(liveNode);
                    }
                    if (ugcProperties == null || !ugcProperties.contains(pName)) {
                        /*
                        Known issue: UGC i18n properties are not tracked in j:liveProperties ,
                        neither on the translation node itself or on the parent node.
                        As a consequence, if this error is raised on a jnt:translation node, this might be a false positive
                        TODO: handle this correctly if the tracking of such property gets fixed in the product
                         */
                        errors.addError(createError(liveNode, "Missing property in default on a published node")
                                .setErrorType(ErrorType.MISSING_PROP_DEFAULT)
                                .addExtraInfo("property name", pName));
                    }
                }
            }

            compareMixins(defaultNode, liveNode, errors);

        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private Set<String> getUgcProperties(JCRNodeWrapper liveNode) throws RepositoryException {
        if (!liveNode.hasProperty(J_LIVE_PROPERTIES)) return null;

        return Arrays.stream(liveNode.getProperty(J_LIVE_PROPERTIES).getValues())
                .map(this::getStringValue)
                .filter(Objects::nonNull)
                .filter(v -> !StringUtils.startsWith(v, LIVE_MIXINS_DECLARATION_PREFIX))
                .collect(Collectors.toSet());
    }

    private void compareMixins(JCRNodeWrapper defaultNode, JCRNodeWrapper liveNode, ContentIntegrityErrorList errors) {
        try {
            final Set<String> defaultMixins = getNodeMixins(defaultNode, DEFAULT_ONLY_MIXINS);
            final Set<String> liveMixins = getNodeMixins(liveNode, null);
            final Collection<String> liveOnlyMixins = CollectionUtils.subtract(liveMixins, defaultMixins);
            final Collection<String> defaultOnlyMixins = CollectionUtils.subtract(defaultMixins, liveMixins);

            if (CollectionUtils.isEmpty(liveOnlyMixins)) {
                if (defaultMixins.size() == liveMixins.size()) {
                    // No mixin is only in live, and the collections have the same size, so they are equal
                    return;
                } else {
                    // In this case, there are some additional mixins in default
                    errors.addError(createError(liveNode, "Different mixins on a published node")
                            .setErrorType(ErrorType.DIFFERENT_MIXINS)
                            .addExtraInfo("default-only-mixins", defaultOnlyMixins));
                }
                return;
            }

            Set<String> ugcMixins = null;
            if (liveNode.isNodeType(JMIX_LIVE_PROPERTIES) && liveNode.hasProperty(J_LIVE_PROPERTIES)) {
                ugcMixins = Arrays.stream(liveNode.getProperty(J_LIVE_PROPERTIES).getValues())
                        .map(this::getStringValue)
                        .filter(v -> StringUtils.startsWith(v, LIVE_MIXINS_DECLARATION_PREFIX))
                        .map(v -> StringUtils.substring(v, LIVE_MIXINS_DECLARATION_PREFIX.length()))
                        .collect(Collectors.toSet());
            }
            final Set<String> finalUgcMixins = ugcMixins;
            final Set<String> filteredLiveOnlyMixins = liveOnlyMixins.stream()
                    .filter(s -> {
                        if (StringUtils.equals(s, JMIX_LIVE_PROPERTIES)) return false;
                        return finalUgcMixins == null || !finalUgcMixins.contains(s);
                    })
                    .collect(Collectors.toCollection(TreeSet::new));
            if (CollectionUtils.isNotEmpty(filteredLiveOnlyMixins) || CollectionUtils.isNotEmpty(defaultOnlyMixins)) {
                errors.addError(createError(liveNode, "Different mixins on a published node")
                        .setErrorType(ErrorType.DIFFERENT_MIXINS)
                        .addExtraInfo("default-only-mixins", defaultOnlyMixins)
                        .addExtraInfo("live-only-mixins", filteredLiveOnlyMixins));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private String getStringValue(Value v) {
        try {
            return v.getString();
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private Set<String> getNodeMixins(JCRNodeWrapper node, List<String> ignoredMixins) throws RepositoryException {
        return Arrays.stream(node.getMixinNodeTypes())
                .map(ExtendedNodeType::getName)
                .filter(m -> CollectionUtils.isEmpty(ignoredMixins) || !ignoredMixins.contains(m))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError integrityError) throws RepositoryException {
        final Object errorTypeObject = integrityError.getErrorType();
        if (!(errorTypeObject instanceof ErrorType)) {
            logger.error("Unexpected error type: " + errorTypeObject);
            return false;
        }
        final ErrorType errorType = (ErrorType) errorTypeObject;
        switch (errorType) {
            case NO_DEFAULT_NODE:
                // We assume here that the deletion has not been correctly published. An alternative fix would be to consider
                // that this node is not correctly flagged as UGC, and so to flag it as such.
                node.remove();
                node.getSession().save();
                return true;
        }
        return false;
    }
}
