package org.jahia.modules.contentintegrity.services.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.commons.Version;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorImpl;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorListImpl;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorTypeImpl;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorTypeImplLegacy;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.utils.Patterns;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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

        Object prop = context.getProperties().get(StringUtils.substringBefore(PRIORITY, ":"));
        if (prop instanceof Float) priority = (float) prop;
        else if (prop instanceof String) {
            try {
                priority = Float.parseFloat((String) prop);
            } catch (NumberFormatException ignored) {}
        }

        prop = context.getProperties().get(StringUtils.substringBefore(ENABLED, ":"));
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
        processProperty(ExecutionCondition.APPLY_ON_NT, context, this::parseString, this::setApplyOnNodeTypes);
        processProperty(ExecutionCondition.SKIP_ON_NT, context, this::parseString, this::setSkipOnNodeTypes);

        processProperty(ExecutionCondition.APPLY_ON_SUBTREES, context, this::parseString, this::setApplyOnSubTrees);
        processProperty(ExecutionCondition.SKIP_ON_SUBTREES, context, this::parseString, this::setSkipOnSubTrees);

        processProperty(ExecutionCondition.APPLY_ON_WS, context, this::parseString, this::setApplyOnWorkspace);
        processProperty(ExecutionCondition.SKIP_ON_WS, context, this::parseString, this::setSkipOnWorkspace);

        processProperty(ExecutionCondition.APPLY_IF_HAS_PROP, context, this::parseString, this::setApplyIfHasProp);
        processProperty(ExecutionCondition.SKIP_IF_HAS_PROP, context, this::parseString, this::setSkipIfHasProp);

        processProperty(ExecutionCondition.APPLY_ON_EXTERNAL_NODES, context, this::parseBoolean, this::setApplyOnExternalNodes);
        processProperty(ExecutionCondition.SKIP_ON_EXTERNAL_NODES, context, this::parseBoolean, this::setSkipOnExternalNodes);

        activateInternal(context);
    }

    private <T> void processProperty(String key, ComponentContext context, Function<Object, T> parser, Consumer<T> processor) {
        Object prop = context.getProperties().get(StringUtils.substringBefore(key, ":"));
        if (prop == null) return;
        processor.accept(parser.apply(prop));
    }

    private String parseString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private Boolean parseBoolean(Object o) {
        return o instanceof Boolean ? (Boolean) o : null;
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

    protected static ContentIntegrityErrorType createErrorType(String key, String defaultMessage) {
        return new ContentIntegrityErrorTypeImpl(key).setDefaultMessage(defaultMessage);
    }

    protected static ContentIntegrityErrorType createErrorType(String key, String defaultMessage, boolean isBlockingImport) {
        return new ContentIntegrityErrorTypeImpl(key, isBlockingImport).setDefaultMessage(defaultMessage);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, ContentIntegrityErrorType errorType) {
        return createError(node, (String) null, errorType);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, ContentIntegrityErrorType errorType, String message) {
        return createError(node, (String) null, errorType, message);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String locale, ContentIntegrityErrorType errorType) {
        return createError(node, locale, errorType, null);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, String locale, ContentIntegrityErrorType errorType, String message) {
        return ContentIntegrityErrorImpl.createError(node, locale, errorType, message, this);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, Locale locale, ContentIntegrityErrorType errorType) {
        return createError(node, locale, errorType, null);
    }

    protected final ContentIntegrityError createError(JCRNodeWrapper node, Locale locale, ContentIntegrityErrorType errorType, String message) {
        return createError(node, Optional.ofNullable(locale).map(Locale::toString).orElse(null), errorType, message);
    }

    @Deprecated
    protected final ContentIntegrityError createError(JCRNodeWrapper node, String message) {
        return createError(node, (String) null, message);
    }

    @Deprecated
    protected final ContentIntegrityError createError(JCRNodeWrapper node, String locale, String message) {
        String key = StringUtils.defaultIfBlank(JCRContentUtils.generateNodeName(message, message.length()), "undefined");
        key = Patterns.DASH.matcher(key).replaceAll(Patterns.UNDERSCORE.pattern());
        key = StringUtils.upperCase(key);
        return createError(node, locale, new ContentIntegrityErrorTypeImplLegacy(key), message);
    }

    @Deprecated
    protected final ContentIntegrityError createError(JCRNodeWrapper node, Locale locale, String message) {
        return createError(node, locale.toString(), message);
    }

    /**
     * Creates an error related to a property.
     * If the node is a translation node, the error will be linked to the parent node, and the locale will be calculated from the translation node.
     * Otherwise, the effect is the same as with {@link #createError(JCRNodeWrapper, ContentIntegrityErrorType) createError}
     *
     * @param node      the node which holds the property
     * @param errorType the error type
     * @return an error object
     */
    protected final ContentIntegrityError createPropertyRelatedError(JCRNodeWrapper node, ContentIntegrityErrorType errorType) {
        return JCRUtils.runJcrSupplierCallBack(() -> {
            if (node.isNodeType(Constants.JAHIANT_TRANSLATION))
                return createError(node.getParent(), JCRUtils.getTranslationNodeLocale(node), errorType);
            return createError(node, errorType);
        });
    }

    protected final ContentIntegrityErrorList createEmptyErrorsList() {
        return ContentIntegrityErrorListImpl.createEmptyList();
    }

    protected final ContentIntegrityErrorList createSingleError(ContentIntegrityError error) {
        return ContentIntegrityErrorListImpl.createSingleError(error);
    }

    protected final ContentIntegrityErrorList trackError(ContentIntegrityErrorList errorList, ContentIntegrityError error) {
        return Optional.ofNullable(errorList).orElseGet(this::createEmptyErrorsList).addError(error);
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
     * It has the possibility to send a last list of errors, for example if the check is buffering the nodes instead of analyzing them one by one.
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

    public static class ExternalNodeCondition implements ExecutionCondition {

        private final boolean processExternalNodes;

        public ExternalNodeCondition(boolean processExternalNodes) {
            this.processExternalNodes = processExternalNodes;
        }

        @Override
        public boolean matches(JCRNodeWrapper node) {
            return processExternalNodes || !JCRUtils.isExternalNode(node);
        }

        @Override
        public String toString() {
            return String.format("processExternalNodes = %s", processExternalNodes);
        }
    }

    private void setApplyOnExternalNodes(boolean processExternalNodes) {
        addCondition(new ExternalNodeCondition(processExternalNodes));
    }

    private void setSkipOnExternalNodes(boolean skipExternalNodes) {
        addCondition(new ExternalNodeCondition(!skipExternalNodes));
    }
}
