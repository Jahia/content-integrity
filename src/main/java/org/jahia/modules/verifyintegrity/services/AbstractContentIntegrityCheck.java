package org.jahia.modules.verifyintegrity.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.verifyintegrity.api.ContentIntegrityCheck;
import org.jahia.utils.Patterns;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractContentIntegrityCheck implements ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(AbstractContentIntegrityCheck.class);

    private float priority = 100f;
    private boolean disabled;
    private String description;
    private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();
    private long id = -1L;

    protected void configure(ComponentContext context) {
        if (context == null) {
            logger.error("The ComponentContext is null");
            return;
        }

        Object prop = context.getProperties().get(PRIORITY);
        if (prop instanceof Float) priority = (float) prop;

        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_NT);
        if (prop instanceof String) setApplyOnNodeTypes((String) prop);
        else if (prop == null) {
            prop = context.getProperties().get(ExecutionCondition.SKIP_ON_NT);
            if (prop instanceof String) setSkipOnNodeTypes((String) prop);
        }

        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_SUBTREES);
        if (prop instanceof String) setApplyOnSubTrees((String) prop);
        else if (prop == null) {
            prop = context.getProperties().get(ExecutionCondition.SKIP_ON_SUBTREES);
            if (prop instanceof String) setSkipOnSubTrees((String) prop);
        }

        prop = context.getProperties().get(ExecutionCondition.APPLY_ON_WS);
        if (prop instanceof String) setApplyOnWorkspace((String) prop);
        else if (prop == null) {
            prop = context.getProperties().get(ExecutionCondition.SKIP_ON_WS);
            if (prop instanceof String) setSkipOnWorkspace((String) prop);
        }
    }

    @Override
    public boolean areConditionsMatched(Node node) {
        if (disabled) return false;
        for (ExecutionCondition condition : conditions) {
            if (!condition.matches(node)) return false;
        }
        return true;
    }

    public void addCondition(ExecutionCondition condition) {
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

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
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

    public String getCheckName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return String.format("%s (priority: %s)", this.getClass().getSimpleName(), priority);
    }

    @Override
    public String toFullString() {
        return String.format("%s (priority: %s) %s", this.getClass().getSimpleName(), priority, printConditions());
    }

    private String printConditions() {
        if (CollectionUtils.isEmpty(conditions)) return StringUtils.EMPTY;
        final StringBuilder sb = new StringBuilder("[");
        for (ExecutionCondition condition : conditions) {
            sb.append(condition).append(";");  // TODO improve the output of the conditins
        }

        return sb.append("]").toString();
    }

    /*
        Utility methods
    */

    protected boolean isInDefaultWorkspace(Node node) {
        try {
            return Constants.EDIT_WORKSPACE.equals(node.getSession().getWorkspace().getName());
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    protected boolean isInLiveWorkspace(Node node) {
        return !isInDefaultWorkspace(node);
    }

    public static class NotCondition implements ExecutionCondition {

        private ExecutionCondition condition;

        public NotCondition(ExecutionCondition condition) {
            this.condition = condition;
        }

        @Override
        public boolean matches(Node node) {
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
        public boolean matches(Node node) {
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
                if (out.length() > 0) {
                    out.append(" || ");
                }
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
        public boolean matches(Node node) {
            try {
                return node.isNodeType(nodeType);
            } catch (RepositoryException e) {
                logger.error("An error occured while ", e);
            }
            return false;
        }
    }

    public void setApplyOnNodeTypes(String nodeTypes) {
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

    public void setSkipOnNodeTypes(String nodeTypes) {
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
        public boolean matches(Node node) {
            try {
                return workspace != null && workspace.equalsIgnoreCase(node.getSession().getWorkspace().getName());
            } catch (RepositoryException e) {
                logger.error("", e);
                return false;
            }
        }
    }

    public void setApplyOnWorkspace(String workspace) {
        addCondition(new WorkspaceCondition(workspace));
    }

    public void setSkipOnWorkspace(String workspace) {
        addCondition(new NotCondition(new WorkspaceCondition(workspace)));
    }

    public static class SubtreeCondition implements ExecutionCondition {

        private String treePath;

        public SubtreeCondition(String treePath) {
            if (treePath.endsWith("/")) this.treePath = treePath.substring(0, treePath.length() - 1);
            else this.treePath = treePath;
        }

        @Override
        public boolean matches(Node node) {
            try {
                final String path = node.getPath();
                return path.equals(treePath) || path.startsWith(treePath + "/");
            } catch (RepositoryException e) {
                logger.error("", e);
                return false;
            }
        }
    }

    public void setApplyOnSubTrees(String trees) {
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

    public void setSkipOnSubTrees(String trees) {
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
}
