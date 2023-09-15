package org.jahia.modules.contentintegrity.services.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.commons.Version;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorImpl;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorListImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Patterns;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PATH_SEPARATOR;

public abstract class AbstractContentIntegrityCheck implements ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(AbstractContentIntegrityCheck.class);
    private static final String CONDITION_VALUES_SEPARATOR = ",";

    private float priority = 100f;
    private boolean enabled = true;
    private boolean scanDurationDisabled = false;
    private String description;
    private final List<ExecutionCondition> conditions = new LinkedList<>();
    private String id = null;
    private long ownTime = 0L;
    private int fatalErrorCount = 0;
    private final int FATAL_ERRORS_THRESHOLD = 10;  // TODO make this configurable
    private String validity_jahiaMinimumVersion = null;  // TODO if another criteria is some day required, introduce a list of validity conditions as for the execution conditions
    private boolean validity_jahiaMinimumVersionBoundIncluded = false;

    protected final void activate(ComponentContext context) {
        if (logger.isDebugEnabled()) logger.debug(String.format("Activating check %s", getClass().getCanonicalName()));
        if (context == null) {
            logger.error("The ComponentContext is null");
            return;
        }

        Object prop = context.getProperties().get(PRIORITY);
        if (prop instanceof Float) priority = (float) prop;
        else if (prop instanceof String) {
            try {
                priority = Float.parseFloat((String) prop);
            } catch (NumberFormatException ignored) {}
        }

        prop = context.getProperties().get(ENABLED);
        if (prop instanceof Boolean) enabled = (Boolean) prop;
        else if (prop instanceof String) enabled = Boolean.parseBoolean((String) prop);

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

        activateInternal(context);
    }

    protected void activateInternal(ComponentContext context) {}

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node) {
        return null;
    }

    @Override
    public final boolean areConditionsMatched(JCRNodeWrapper node) {
        for (ExecutionCondition condition : conditions) {
            if (!condition.matches(node)) return false;
        }
        return true;
    }

    protected final void addCondition(ExecutionCondition condition) {
        conditions.add(condition);
    }

    public final void setConditions(List<ExecutionCondition> conditions) {
        for (ExecutionCondition condition : conditions) {
            addCondition(condition);
        }
    }

    @Override
    public final boolean areConditionsReachable(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        return conditions.stream().allMatch(c -> c.isReachableCondition(scanRootNode, excludedPaths) >= 0);
    }

    @Override
    public final float getPriority() {
        return priority;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final boolean canRun() {
        return !scanDurationDisabled;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected final void setScanDurationDisabled(boolean scanDurationDisabled) {
        this.scanDurationDisabled = scanDurationDisabled;
    }

    public final String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        this.description = description;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final void setId(String id) {
        this.id = id;
    }

    @Override
    public final String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public final String toString() {
        return String.format("%s (id: %s, priority: %s, enabled: %s)", getName(), getId(), priority, enabled);
    }

    @Override
    public final String toFullString() {
        return String.format("%s %s", this, printConditions());
    }

    @Override
    public final void resetOwnTime() {
        ownTime = 0L;
    }

    @Override
    public final long getOwnTime() {
        return ownTime;
    }

    @Override
    public final void trackOwnTime(long time) {
        ownTime += time;
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String locale, String message) {
        return ContentIntegrityErrorImpl.createError(node, locale, message, this);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, Locale locale, String message) {
        return ContentIntegrityErrorImpl.createError(node, locale == null ? null : LanguageCodeConverters.localeToLanguageTag(locale), message, this);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String message) {
        return ContentIntegrityErrorImpl.createError(node, null, message, this);
    }

    /**
     * Creates an error related to a property.
     * If the node is a translation node, the error will be linked to the parent node, and the locale will be calculated from the translation node.
     * Otherwise, the effect is the same as with {@link #createError(JCRNodeWrapper, String) createError}
     *
     * @param node    the node which holds the property
     * @param message the error message
     * @return an error object
     */
    protected final ContentIntegrityError createPropertyRelatedError(JCRNodeWrapper node, String message) {
        return JCRUtils.runJcrSupplierCallBack(() -> {
            if (node.isNodeType(Constants.JAHIANT_TRANSLATION))
                return ContentIntegrityErrorImpl.createError(node.getParent(), JCRUtils.getTranslationNodeLocale(node), message, this);
            return createError(node, message);
        });
    }

    protected final ContentIntegrityErrorList createEmptyErrorsList() {
        return ContentIntegrityErrorListImpl.createEmptyList();
    }

    protected final ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return ContentIntegrityErrorListImpl.createSingleError(error);
    }

    @Override
    public final void trackFatalError() {
        fatalErrorCount += 1;
        if (fatalErrorCount >= FATAL_ERRORS_THRESHOLD) {
            logger.warn(String.format("Automatically disabling the check as it is raising too many unhandled errors: %s", getName()));
            setScanDurationDisabled(true);
        }
    }

    @Override
    public final void initializeIntegrityTest(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        fatalErrorCount = 0;
        setScanDurationDisabled(false);
        initializeIntegrityTestInternal(scanRootNode, excludedPaths);
    }

    /**
     * This method is run once on each check before starting a scan
     */
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {}

    @Override
    public final ContentIntegrityErrorList finalizeIntegrityTest(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        final ContentIntegrityErrorList errorList = finalizeIntegrityTestInternal(scanRootNode, excludedPaths);
        // TODO the next lines are useless. scanDurationDisabled can be set to true for other reasons. And it is set to false in initializeIntegrityTest() in any case.
        if (!scanDurationDisabled && fatalErrorCount > 0) {
            logger.info(String.format("Enabling back the integrity check which was disabled after too many errors: %s", getName()));
            setScanDurationDisabled(false);
        }
        return errorList;
    }

    /**
     * This method is run once on each check after finishing a scan.
     * It has the possibility to send a last list of errors, for example if the check is buffering the nodes instead analyzing them one by one.
     *
     * @param scanRootNode the root node of the scan
     * @param excludedPaths the paths which are excluded from the scan
     * @return some integrity errors
     */
    protected ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        return null;
    }

    @Override
    public final boolean isValid() {
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
        if (nodeTypes.contains(CONDITION_VALUES_SEPARATOR)) {
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
        if (nodeTypes.contains(CONDITION_VALUES_SEPARATOR)) {
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
        private final String treePathPlusSlash;

        public SubtreeCondition(String treePath) {
            if (treePath.endsWith(JCR_PATH_SEPARATOR)) {
                this.treePath = treePath.length() == 1 ? treePath : treePath.substring(0, treePath.length() - 1);
                treePathPlusSlash = treePath;
            } else {
                this.treePath = treePath;
                treePathPlusSlash = treePath.concat(JCR_PATH_SEPARATOR);
            }
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            final String path = node.getPath();
            return path.equals(treePath) || path.startsWith(treePathPlusSlash); // TODO review path.equals(treePath) , shouldn't this be another condition? (toString() to adapt if changed)
        }

        @Override
        public String toString() {
            return String.format("is or is under %s", treePath);
        }
    }

    private void setApplyOnSubTrees(String trees) {
        if (trees.contains(CONDITION_VALUES_SEPARATOR)) {
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
        if (trees.contains(CONDITION_VALUES_SEPARATOR)) {
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
        if (properties.contains(CONDITION_VALUES_SEPARATOR)) {
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
        if (properties.contains(CONDITION_VALUES_SEPARATOR)) {
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
