package org.jahia.modules.contentintegrity.services.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.bin.Jahia;
import org.jahia.commons.Version;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Patterns;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jahia.api.Constants.JAHIAMIX_LASTPUBLISHED;
import static org.jahia.api.Constants.JCR_LASTMODIFIED;
import static org.jahia.api.Constants.LASTPUBLISHED;

public abstract class AbstractContentIntegrityCheck implements ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(AbstractContentIntegrityCheck.class);

    private float priority = 100f;
    private boolean enabled = true;
    private boolean scanDurationDisabled = false;
    private String description;
    private final List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();
    private String id = null;
    private long ownTime = 0L;
    private int fatalErrorCount = 0;
    private final int FATAL_ERRORS_THRESHOLD = 10;  // TODO make this configurable
    private String validity_jahiaMinimumVersion = null;  // TODO if another criteria is some day required, introduce a list of validity conditions as for the execution conditions
    private boolean validity_jahiaMinimumVersionBoundIncluded = false;

    protected void activate(ComponentContext context) {
        if (logger.isDebugEnabled()) logger.debug(String.format("Activating check %s", getClass().getCanonicalName()));
        if (context == null) {
            logger.error("The ComponentContext is null");
            return;
        }

        Object prop = context.getProperties().get(PRIORITY);
        if (prop instanceof Float) priority = (float) prop;

        prop = context.getProperties().get(ENABLED);
        if (prop instanceof Boolean) enabled = (Boolean) prop;

        prop = context.getProperties().get(ValidityCondition.APPLY_ON_VERSION_GT);
        if (prop instanceof String) {
            validity_jahiaMinimumVersion = (String) prop;
            validity_jahiaMinimumVersionBoundIncluded = false;
        }

        prop = context.getProperties().get(ValidityCondition.APPLY_ON_VERSION_GTE);
        if (prop instanceof String) {
            validity_jahiaMinimumVersion = (String) prop;
            validity_jahiaMinimumVersionBoundIncluded = true;
        }

        // TODO check if it is possible to keep the declaration order
        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_NT);
        if (prop instanceof String) setApplyOnNodeTypes((String) prop);
        prop = context.getProperties().get(ExecutionCondition.SKIP_ON_NT);
        if (prop instanceof String) setSkipOnNodeTypes((String) prop);

        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_SUBTREES);
        if (prop instanceof String) setApplyOnSubTrees((String) prop);
        prop = context.getProperties().get(ExecutionCondition.SKIP_ON_SUBTREES);
        if (prop instanceof String) setSkipOnSubTrees((String) prop);

        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_WS);
        if (prop instanceof String) setApplyOnWorkspace((String) prop);
        prop = context.getProperties().get(ExecutionCondition.SKIP_ON_WS);
        if (prop instanceof String) setSkipOnWorkspace((String) prop);

        prop = context.getProperties().get(ExecutionCondition.APPLY_IF_HAS_PROP);
        if (prop instanceof String) setApplyIfHasProp((String) prop);
        prop = context.getProperties().get(ExecutionCondition.SKIP_IF_HAS_PROP);
        if (prop instanceof String) setSkipIfHasProp((String) prop);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node) {
        return null;
    }

    @Override
    public boolean areConditionsMatched(JCRNodeWrapper node) {
        for (ExecutionCondition condition : conditions) {
            if (!condition.matches(node)) return false;
        }
        return true;
    }

    protected void addCondition(ExecutionCondition condition) {
        conditions.add(condition);
    }

    public void setConditions(List<ExecutionCondition> conditions) {
        for (ExecutionCondition condition : conditions) {
            addCondition(condition);
        }
    }

    @Override
    public float getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return enabled && !scanDurationDisabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected void setScanDurationDisabled(boolean scanDurationDisabled) {
        this.scanDurationDisabled = scanDurationDisabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return String.format("%s (id: %s, priority: %s, enabled: %s)", getName(), getId(), priority, enabled);
    }

    @Override
    public String toFullString() {
        return String.format("%s %s", this, printConditions());
    }

    @Override
    public void resetOwnTime() {
        ownTime = 0L;
    }

    @Override
    public long getOwnTime() {
        return ownTime;
    }

    @Override
    public void trackOwnTime(long time) {
        ownTime += time;
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String locale, String message) {
        return ContentIntegrityError.createError(node, locale, message, this);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, Locale locale, String message) {
        return ContentIntegrityError.createError(node, locale == null ? null : LanguageCodeConverters.localeToLanguageTag(locale), message, this);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String message) {
        return ContentIntegrityError.createError(node, null, message, this);
    }

    protected final ContentIntegrityErrorList createEmptyErrorsList() {
        return ContentIntegrityErrorList.createEmptyList();
    }

    protected final ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return ContentIntegrityErrorList.createSingleError(error);
    }

    @Override
    public void trackFatalError() {
        fatalErrorCount += 1;
        if (fatalErrorCount >= FATAL_ERRORS_THRESHOLD) {
            logger.warn(String.format("Automatically disabling the check as it is raising too many unhandled errors: %s", getName()));
            setScanDurationDisabled(true);
        }
    }

    @Override
    public final void initializeIntegrityTest(JCRNodeWrapper node, Collection<String> excludedPaths) {
        fatalErrorCount = 0;
        setScanDurationDisabled(false);
        initializeIntegrityTestInternal(node, excludedPaths);
    }

    /**
     * This method is run once on each check before starting a scan
     */
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {}

    @Override
    public final void finalizeIntegrityTest(JCRNodeWrapper node, Collection<String> excludedPaths) {
        finalizeIntegrityTestInternal(node, excludedPaths);
        if (!scanDurationDisabled && fatalErrorCount > 0) {
            logger.info(String.format("Enabling back the integrity check which was disabled after too many errors: %s", getName()));
            setScanDurationDisabled(false);
        }
    }

    /**
     * This method is run once on each check after finishing a scan
     */
    protected void finalizeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {}

    @Override
    public boolean isValid() {
        if (validity_jahiaMinimumVersion == null) return true;
        final Version checkVersion;
        try {
            checkVersion = new Version(validity_jahiaMinimumVersion);
        } catch (NumberFormatException nfe) {
            logger.error(String.format("Invalid Jahia minimum version: %s", validity_jahiaMinimumVersion));
            return false;
        }

        final Version jahiaVersion = new Version(Jahia.VERSION);
        final int compareTo = jahiaVersion.compareTo(checkVersion);
        return validity_jahiaMinimumVersionBoundIncluded ? compareTo >= 0 : compareTo > 0;
    }

    /*
        Utility methods
    */

    private boolean isInWorkspace(JCRNodeWrapper node, String workspace) {
        try {
            return StringUtils.equals(node.getSession().getWorkspace().getName(), workspace);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    protected boolean isInDefaultWorkspace(JCRNodeWrapper node) {
        return isInWorkspace(node, Constants.EDIT_WORKSPACE);
    }

    protected boolean isInLiveWorkspace(JCRNodeWrapper node) {
        return isInWorkspace(node, Constants.LIVE_WORKSPACE);
    }

    protected JCRSessionWrapper getSystemSession(String workspace, boolean refresh) {
        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
            if (refresh) session.refresh(false);
            return session;
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
            return null;
        }
    }

    protected boolean nodeExists(String uuid, JCRSessionWrapper session) {
        try {
            session.getNodeByUUID(uuid);
            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }

    protected String getTranslationNodeLocale(Node translationNode) {
        try {
            if (translationNode.hasProperty(Constants.JCR_LANGUAGE))
                return translationNode.getProperty(Constants.JCR_LANGUAGE).getString();
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to read the property %s on a translation node", Constants.JCR_LANGUAGE), e);
        }
        return getTranslationNodeLocaleFromNodeName(translationNode);
    }

    protected String getTranslationNodeLocaleFromNodeName(Node translationNode) {
        try {
            return translationNode.getName().substring("j:translation_".length());
        } catch (RepositoryException e) {
            logger.error("Impossible to extract the locale", e);
            return null;
        }
    }

    protected boolean hasPendingModifications(JCRNodeWrapper node) {
        try {
            if (!StringUtils.equals(node.getSession().getWorkspace().getName(), Constants.EDIT_WORKSPACE))
                throw new IllegalArgumentException("The publication status can be tested only in the default workspace");

            if (!node.isNodeType(JAHIAMIX_LASTPUBLISHED)) return false;
            if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT)) return true;
            if (!node.hasProperty(LASTPUBLISHED)) return true;
            final Calendar lastPublished = node.getProperty(LASTPUBLISHED).getDate();
            if (lastPublished == null) return true;
            if (!node.hasProperty(JCR_LASTMODIFIED)) {
                // If this occurs, then it should be detected by a dedicated integrityCheck. But here there's no way to deal with such node.
                logger.error("The node has no last modification date set " + node.getPath());
                return false;
            }
            final Calendar lastModified = node.getProperty(JCR_LASTMODIFIED).getDate();

            return lastModified.after(lastPublished);
        } catch (RepositoryException e) {
            logger.error("", e);
            // If we can't validate that there's some pending modifications here, then we assume that there are no one.
            return false;
        }
    }

    protected boolean propertyValueEquals(Property p1, Property p2) throws RepositoryException {
        if (p1.isMultiple() != p2.isMultiple()) return false;
        if (p1.isMultiple()) return propertyValueEqualsMultiple(p1, p2);
        return valueEquals(p1.getValue(), p2.getValue());
    }

    private boolean valueEquals(Value v1, Value v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;

        final int type1 = v1.getType();
        if (type1 != v2.getType()) return false;

        try {
            switch (type1) {
                case PropertyType.STRING:
                case PropertyType.REFERENCE:
                case PropertyType.WEAKREFERENCE:
                case PropertyType.NAME:
                case PropertyType.PATH:
                case PropertyType.URI:
                    return StringUtils.equals(v1.getString(), v2.getString());
                case PropertyType.DATE:
                    return v1.getDate().equals(v2.getDate());
                case PropertyType.DOUBLE:
                    return v1.getDouble() == v2.getDouble();
                case PropertyType.DECIMAL:
                    return v1.getDecimal().equals(v2.getDecimal());
                case PropertyType.LONG:
                    return v1.getLong() == v2.getLong();
                case PropertyType.BOOLEAN:
                    return v1.getBoolean() == v2.getBoolean();
                // binary values are not compared
                case PropertyType.BINARY:
                default:
                    return true;
            }
        } catch (RepositoryException re) {
            logger.error("", re);
            return true;
        }
    }

    private boolean propertyValueEqualsMultiple(Property p1, Property p2) throws RepositoryException {
        final Value[] values1 = p1.getValues();
        final Value[] values2 = p2.getValues();
        if (values1.length != values2.length) return false;

        final Map<Integer, List<Value>> valuesMap1 = Arrays.stream(values1).collect(Collectors.groupingBy(Value::getType));
        return Arrays.stream(p2.getValues()).noneMatch(v2 -> {
            final int type = v2.getType();
            if (!valuesMap1.containsKey(type)) return true;
            // TODO: this might fail with properties holding several times the same value:
            // { a, a, b } will be seen as equal to { a, b, b }
            return valuesMap1.get(type).stream().noneMatch(v1 -> valueEquals(v1, v2));
        });
    }

    /*
        Execution conditions
    */

    private String printConditions() {
        if (CollectionUtils.isEmpty(conditions)) return StringUtils.EMPTY;
        final StringBuilder sb = new StringBuilder("[");
        for (ExecutionCondition condition : conditions) {
            if (sb.length() > 1) sb.append(" && ");
            sb.append("(").append(condition).append(")");
        }

        return sb.append("]").toString();
    }

    public static class NotCondition implements ExecutionCondition {

        private final ExecutionCondition condition;

        public NotCondition(ExecutionCondition condition) {
            this.condition = condition;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            return !condition.matches(node);
        }

        @Override
        public String toString() {
            return "not (" + condition + ")";
        }
    }

    public static class AnyOfCondition implements ExecutionCondition {

        private final List<ExecutionCondition> conditions = new LinkedList<>();

        public void add(ExecutionCondition condition) {
            conditions.add(condition);
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            if (CollectionUtils.isEmpty(conditions)) return true;
            for (ExecutionCondition condition : conditions) {
                if (condition.matches(node)) return true;
            }
            return false;
        }

        @Override
        public String toString() {
            final StringBuilder out = new StringBuilder();
            for (ExecutionCondition cond : conditions) {
                if (out.length() > 0) out.append(" || ");
                out.append("(").append(cond).append(")");
            }
            return out.toString();
        }
    }

    public static class NodeTypeCondition implements ExecutionCondition {

        private final String nodeType;

        public NodeTypeCondition(String nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            try {
                return node.isNodeType(nodeType);
            } catch (RepositoryException e) {
                logger.error("An error occurred while testing the type of the node", e);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("node type = %s", nodeType);
        }
    }

    private void setApplyOnNodeTypes(String nodeTypes) {
        if (nodeTypes.contains(",")) {
            final AnyOfCondition condition = new AnyOfCondition();
            for (String nodeType : Patterns.COMMA.split(nodeTypes)) {
                condition.add(new NodeTypeCondition(nodeType.trim()));
            }
            addCondition(condition);
        } else if (StringUtils.isNotBlank(nodeTypes)) {
            addCondition(new NodeTypeCondition(nodeTypes.trim()));
        }
    }

    private void setSkipOnNodeTypes(String nodeTypes) {
        ExecutionCondition condition = null;
        if (nodeTypes.contains(",")) {
            final AnyOfCondition anyOf = new AnyOfCondition();
            for (String nodeType : Patterns.COMMA.split(nodeTypes)) {
                anyOf.add(new NodeTypeCondition(nodeType.trim()));
            }
            condition = anyOf;
        } else if (StringUtils.isNotBlank(nodeTypes)) {
            condition = new NodeTypeCondition(nodeTypes);
        }
        if (condition != null) {
            addCondition(new NotCondition(condition));
        }
    }

    public static class WorkspaceCondition implements ExecutionCondition {

        private final String workspace;

        public WorkspaceCondition(String workspace) {
            this.workspace = workspace;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            try {
                return workspace != null && workspace.equalsIgnoreCase(node.getSession().getWorkspace().getName());
            } catch (RepositoryException e) {
                logger.error("", e);
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("workspace = %s", workspace);
        }
    }

    private void setApplyOnWorkspace(String workspace) {
        addCondition(new WorkspaceCondition(workspace));
    }

    private void setSkipOnWorkspace(String workspace) {
        addCondition(new NotCondition(new WorkspaceCondition(workspace)));
    }

    public static class SubtreeCondition implements ExecutionCondition {

        private final String treePath;

        public SubtreeCondition(String treePath) {
            if (treePath.endsWith("/")) this.treePath = treePath.substring(0, treePath.length() - 1);
            else this.treePath = treePath;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            final String path = node.getPath();
            return path.equals(treePath) || path.startsWith(treePath + "/"); // TODO review path.equals(treePath) , shouldn't this be another condition? (toString() to adapt if changed)
        }

        @Override
        public String toString() {
            return String.format("is or is under %s", treePath);
        }
    }

    private void setApplyOnSubTrees(String trees) {
        if (trees.contains(",")) {
            final AnyOfCondition condition = new AnyOfCondition();
            for (String tree : Patterns.COMMA.split(trees)) {
                condition.add(new SubtreeCondition(tree.trim()));
            }
            addCondition(condition);
        } else if (StringUtils.isNotBlank(trees)) {
            addCondition(new SubtreeCondition(trees.trim()));
        }
    }

    private void setSkipOnSubTrees(String trees) {
        ExecutionCondition condition = null;
        if (trees.contains(",")) {
            final AnyOfCondition anyOf = new AnyOfCondition();
            for (String tree : Patterns.COMMA.split(trees)) {
                anyOf.add(new SubtreeCondition(tree.trim()));
            }
            condition = anyOf;
        } else if (StringUtils.isNotBlank(trees)) {
            condition = new SubtreeCondition(trees);
        }
        if (condition != null) {
            addCondition(new NotCondition(condition));
        }
    }

    protected static class HasPropertyCondition implements ExecutionCondition {

        private final String propertyName;

        public HasPropertyCondition(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            try {
                return node.hasProperty(propertyName);
            } catch (RepositoryException e) {
                logger.error("", e);
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("has property %s", propertyName);
        }
    }

    private void setApplyIfHasProp(String properties) {
        if (properties.contains(",")) {
            final AnyOfCondition condition = new AnyOfCondition();
            for (String prop : Patterns.COMMA.split(properties)) {
                condition.add(new HasPropertyCondition(prop.trim()));
            }
            addCondition(condition);
        } else if (StringUtils.isNotBlank(properties)) {
            addCondition(new HasPropertyCondition(properties.trim()));
        }
    }

    private void setSkipIfHasProp(String properties) {
        ExecutionCondition condition = null;
        if (properties.contains(",")) {
            final AnyOfCondition anyOf = new AnyOfCondition();
            for (String prop : Patterns.COMMA.split(properties)) {
                anyOf.add(new HasPropertyCondition(prop.trim()));
            }
            condition = anyOf;
        } else if (StringUtils.isNotBlank(properties)) {
            condition = new HasPropertyCondition(properties);
        }
        if (condition != null) {
            addCondition(new NotCondition(condition));
        }

    }
}
