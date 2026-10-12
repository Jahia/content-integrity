// Unregisters the test definitions of the ChildNodeDefinitionsSanityCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry

final String SYSTEM_ID = "content-integrity-tests-child-node-definitions"

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
if (session.nodeExists("/sites/SITEKEY/contents/child-node-definitions")) {
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        def real = session.getNode("/sites/SITEKEY/contents/child-node-definitions").getRealNode()
        def realSession = real.getSession()
        real.remove()
        realSession.save()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}
try {
    BundleUtils.getOsgiService(JCRStoreService.class, null).undeployDefinitions(SYSTEM_ID)
} catch (ignored) {
}
BundleUtils.getOsgiService(NodeTypeRegistry.class, null).unregisterNodeTypes(SYSTEM_ID)
JcrSessionFilter.endRequest()
