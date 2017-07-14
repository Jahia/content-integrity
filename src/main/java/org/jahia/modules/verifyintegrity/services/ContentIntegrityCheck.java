package org.jahia.modules.verifyintegrity.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.utils.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.LinkedList;
import java.util.List;

public abstract class ContentIntegrityCheck implements InitializingBean, DisposableBean, Comparable<ContentIntegrityCheck> {

    public interface SupportsIntegrityErrorFix {
        boolean fixError(Node node, Object errorExtraInfos) throws RepositoryException;
    }

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityCheck.class);

    private float priority = 100f;
    private boolean disabled;
    private String description;
    private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();
    private long id = -1L;

    public abstract ContentIntegrityError checkIntegrityBeforeChildren(Node node);

    public abstract ContentIntegrityError checkIntegrityAfterChildren(Node node);

    @Override
    public void afterPropertiesSet() throws Exception {
        ContentIntegrityService.getInstance().registerIntegrityCheck(this);
    }

    @Override
    public void destroy() throws Exception {
        ContentIntegrityService.getInstance().unregisterIntegrityCheck(this);
    }

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

    public void setPriority(float priority) {
        this.priority = priority;
    }

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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public int compareTo(ContentIntegrityCheck o) {
        return (int) (priority - o.getPriority());
    }

    @Override
    public String toString() {
        return String.format("%s (priority: %s)", this.getClass().getName(), priority);
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

    /*
        Execution conditions
    */
    public interface ExecutionCondition {
        boolean matches(Node node);
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
}
