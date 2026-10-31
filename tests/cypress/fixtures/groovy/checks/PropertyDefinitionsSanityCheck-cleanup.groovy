// Removes the PropertyDefinitionsSanityCheck fixtures which are not deleted with the test site, then unregisters the test definitions
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry

final String SYSTEM_ID = "content-integrity-tests-property-definitions"

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    ["/mounts/ci-tests-validation", "/sites/SITEKEY/contents/property-definitions"].each { path ->
        if (session.nodeExists(path)) {
            def real = session.getNode(path).getRealNode()
            def realSession = real.getSession()
            real.remove()
            realSession.save()
        }
    }
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
try {
    BundleUtils.getOsgiService(JCRStoreService.class, null).undeployDefinitions(SYSTEM_ID)
} catch (ignored) {
}
BundleUtils.getOsgiService(NodeTypeRegistry.class, null).unregisterNodeTypes(SYSTEM_ID)
JcrSessionFilter.endRequest()
