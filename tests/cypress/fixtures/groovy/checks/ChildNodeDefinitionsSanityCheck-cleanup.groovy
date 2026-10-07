// Unregisters the test definitions of the ChildNodeDefinitionsSanityCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry

final String SYSTEM_ID = "content-integrity-tests-child-node-definitions"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
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
    JCRStoreService.getInstance().undeployDefinitions(SYSTEM_ID)
} catch (ignored) {
}
NodeTypeRegistry.getInstance().unregisterNodeTypes(SYSTEM_ID)
JcrSessionFilter.endRequest()
