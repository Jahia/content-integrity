package org.jahia.modules.contentintegrity.services.util;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.PropertyImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.spi.Name;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.LanguageCodeConverters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Operations shared by the fixError() implementations of the integrity checks. They come from the fix scripts
 * of jcr-scripts, which run on content that the regular API can't always load: a node whose type is not
 * declared, or that its parent definition doesn't allow. Such items are removed at the Jackrabbit level.
 */
public final class RepairUtils {

    private static final Logger logger = LoggerFactory.getLogger(RepairUtils.class);

    private RepairUtils() {
    }

    @FunctionalInterface
    public interface JcrOperation {
        void execute() throws RepositoryException;
    }

    /**
     * Runs a repair with the JCR listeners disabled, so that the platform doesn't react to the correction
     * (no rule, no ACL propagation, no auto-publication).
     */
    public static void runWithListenersDisabled(JcrOperation operation) throws RepositoryException {
        JCRObservationManager.setAllEventListenersDisabled(true);
        try {
            operation.execute();
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false);
        }
    }

    /**
     * Returns the node to repair. An error raised on a translation node is linked to its parent node, with the
     * locale of the translation: in this case, the translation node is returned.
     */
    public static JCRNodeWrapper getErrorTarget(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final String locale = error.getLocale();
        if (StringUtils.isBlank(locale) || node.isNodeType(Constants.JAHIANT_TRANSLATION)) return node;
        final Node i18n = node.getI18N(LanguageCodeConverters.languageCodeToLocale(locale));
        return i18n instanceof JCRNodeWrapper ? (JCRNodeWrapper) i18n : node.getSession().getNodeByIdentifier(i18n.getIdentifier());
    }

    /**
     * Removes a node at the Jackrabbit level, without loading its definition, then saves.
     */
    public static void removeNodeRaw(JCRNodeWrapper node) throws RepositoryException {
        final Node realNode = node.getRealNode();
        if (!(realNode instanceof NodeImpl)) {
            node.remove();
            node.saveSession();
            return;
        }
        final NodeImpl parent = (NodeImpl) realNode.getParent();
        invoke(parent, "removeChildNode", NodeId.class, ((NodeImpl) realNode).getNodeId());
        realNode.getSession().save();
        node.getSession().refresh(false);
    }

    /**
     * Removes a property at the Jackrabbit level, without checking its definition, then saves.
     *
     * @return false if the node has no such property
     */
    public static boolean removePropertyRaw(JCRNodeWrapper node, String propertyName) throws RepositoryException {
        final Node realNode = node.getRealNode();
        if (!realNode.hasProperty(propertyName)) return false;
        if (!(realNode instanceof NodeImpl)) {
            node.getProperty(propertyName).remove();
            node.saveSession();
            return true;
        }
        final Name name = ((PropertyImpl) realNode.getProperty(propertyName)).getQName();
        invoke(realNode, "removeChildProperty", Name.class, name);
        realNode.getSession().save();
        node.getSession().refresh(false);
        return true;
    }

    /**
     * Removes a mixin at the Jackrabbit level, then saves. Jackrabbit refuses to remove a mixin whose type is not
     * registered anymore, since it is not part of the effective type of the node: the node state is updated the way
     * Jackrabbit's RemoveMixinOperation does it, without resolving the type.
     *
     * @return false if the node has no such mixin
     */
    public static boolean removeMixinRaw(JCRNodeWrapper node, String mixin) throws RepositoryException {
        final Node realNode = node.getRealNode();
        if (!(realNode instanceof NodeImpl)) {
            node.removeMixin(mixin);
            node.saveSession();
            return true;
        }
        final NodeImpl nodeImpl = (NodeImpl) realNode;
        final Name name = ((SessionImpl) nodeImpl.getSession()).getQName(mixin);
        // When the mixin type is not registered, Jackrabbit leaves it out of the node state, but jcr:mixinTypes still lists it
        final Set<Name> mixins = new HashSet<>(nodeImpl.getMixinTypeNames());
        final boolean inState = mixins.remove(name);
        boolean inProperty = false;
        if (realNode.hasProperty(Property.JCR_MIXIN_TYPES)) {
            for (Value value : realNode.getProperty(Property.JCR_MIXIN_TYPES).getValues()) {
                if (StringUtils.equals(value.getString(), mixin)) inProperty = true;
            }
        }
        if (!inState && !inProperty) return false;
        if (inState) {
            final NodeState state = (NodeState) invoke(nodeImpl, "getOrCreateTransientItemState");
            state.setMixinTypeNames(mixins);
        }
        // Rewrites jcr:mixinTypes from the remaining mixins
        invoke(nodeImpl, "setMixinTypesProperty", Set.class, mixins);
        realNode.getSession().save();
        node.getSession().refresh(false);
        return true;
    }

    /**
     * Publishes some nodes as root, from a workspace to the other one.
     */
    public static void publishAsRoot(List<String> uuids, String sourceWorkspace, String destinationWorkspace) throws RepositoryException {
        runAsRoot(() -> JCRPublicationService.getInstance().publish(uuids, sourceWorkspace, destinationWorkspace, false, null));
    }

    /**
     * Runs an operation as root: the publication service checks the permissions of the current user.
     */
    public static void runAsRoot(JcrOperation operation) throws RepositoryException {
        final JCRSessionFactory sessionFactory = JCRSessionFactory.getInstance();
        final JahiaUser currentUser = sessionFactory.getCurrentUser();
        final boolean switchToRoot = currentUser == null || !currentUser.isRoot();
        if (switchToRoot) {
            final JCRUserNode rootUser = JahiaUserManagerService.getInstance().lookupRootUser();
            sessionFactory.setCurrentUser(rootUser.getJahiaUser());
        }
        try {
            operation.execute();
        } finally {
            if (switchToRoot) sessionFactory.setCurrentUser(currentUser);
        }
    }

    /**
     * Marks a node for deletion, then unmarks it. Unmarking alone fails when the deletion information is
     * partial, while marking it again rebuilds a consistent state first. Failures are logged, not thrown.
     */
    public static void markThenUnmarkForDeletion(JCRNodeWrapper node, String comment) {
        try {
            node.markForDeletion(comment);
        } catch (RepositoryException e) {
            logger.debug("Impossible to mark {} for deletion", node.getPath(), e);
        }
        try {
            node.unmarkForDeletion();
        } catch (RepositoryException e) {
            logger.debug("Impossible to unmark {} for deletion", node.getPath(), e);
        }
    }

    // NodeImpl.removeChildNode(), removeChildProperty(), getOrCreateTransientItemState() and setMixinTypesProperty() are not public
    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object parameter) throws RepositoryException {
        return invoke(target, methodName, new Class<?>[]{parameterType}, new Object[]{parameter});
    }

    private static Object invoke(Object target, String methodName) throws RepositoryException {
        return invoke(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] parameters) throws RepositoryException {
        try {
            final Method method = findMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, parameters);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RepositoryException) throw (RepositoryException) e.getCause();
            throw new RepositoryException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RepositoryException(String.format("Impossible to call NodeImpl.%s()", methodName), e);
        }
    }

    // Some methods are declared by a superclass of NodeImpl
    private static Method findMethod(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        for (Class<?> type = NodeImpl.class; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Looked up in the superclass
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
