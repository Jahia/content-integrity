// Unregisters the ghost type of the UndeclaredNodeTypesCheck fixtures, once the nodes using it are deleted
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
if (session.nodeExists("/sites/SITEKEY/contents/undeclared-node-types")) {
    // The nodes of undeclared types can't be removed with the Jahia API
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        def real = session.getNode("/sites/SITEKEY/contents/undeclared-node-types").getRealNode()
        real.remove()
        real.getSession().save()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}
def registry = BundleUtils.getOsgiService(NodeTypeRegistry.class, null)
try {
    registry.unregisterNodeType("jnt:ciTestGhost")
} catch (ignored) {
}
BundleUtils.getOsgiService(JCRStoreService.class, null).deployDefinitions("content-integrity")
JcrSessionFilter.endRequest()
