package org.jahia.modules.contentintegrity.services.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Patterns;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public abstract class AbstractContentIntegrityCheck implements ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(AbstractContentIntegrityCheck.class);

    private float priority = 100f;
    private boolean enabled = true;
    private String description;
    private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();
    private long id = -1L;
    private long ownTime = 0L;
    private int fatalErrorCount = 0;
    private final int FATAL_ERRORS_THRESHOLD = 10;  // TODO make this configurable

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
        if (!enabled) return false;
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

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
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
        return String.format("%s %s", toString(), printConditions());
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

    protected final ContentIntegrityErrorList createSingleError(JCRNodeWrapper node, String locale, String message) {
        return ContentIntegrityErrorList.createSingleError(createError(node, locale, message));
    }

    protected final ContentIntegrityErrorList createSingleError(JCRNodeWrapper node, Locale locale, String message) {
        return ContentIntegrityErrorList.createSingleError(createError(node, locale, message));
    }

    protected final ContentIntegrityErrorList createSingleError(JCRNodeWrapper node, String message) {
        return ContentIntegrityErrorList.createSingleError(createError(node, message));
    }

    protected final ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return ContentIntegrityErrorList.createSingleError(error);
    }

    protected final ContentIntegrityErrorList appendError(ContentIntegrityErrorList list, ContentIntegrityError error) {
        if (list == null) return createSingleError(error);
        return list.addError(error);
    }

    @Override
    public void trackFatalError() {
        fatalErrorCount += 1;
        if (fatalErrorCount >= FATAL_ERRORS_THRESHOLD) {
            logger.warn(String.format("Automatically disabling the check as it is raising too many unhandled errors: %s", getName()));
            this.setEnabled(false);
        }
    }

    @Override
    public void initializeIntegrityTest() {
        fatalErrorCount = 0;
    }

    @Override
    public void finalizeIntegrityTest() {
        if (!enabled && fatalErrorCount > 0) {
            logger.info(String.format("Enabling back the integrity check which was disabled after too many errors: %s", getName()));
            this.setEnabled(true);
        }
    }

    /*
        Utility methods
    */

    protected boolean isInDefaultWorkspace(JCRNodeWrapper node) {
        try {
            return Constants.EDIT_WORKSPACE.equals(node.getSession().getWorkspace().getName());
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    protected boolean isInLiveWorkspace(JCRNodeWrapper node) {
        return !isInDefaultWorkspace(node);
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

        private ExecutionCondition condition;

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

        private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();

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

        private String nodeType;

        public NodeTypeCondition(String nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            try {
                return node.isNodeType(nodeType);
            } catch (RepositoryException e) {
                logger.error("An error occured while ", e);
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

        private String workspace;

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

        private String treePath;

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

        private String propertyName;

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
}
