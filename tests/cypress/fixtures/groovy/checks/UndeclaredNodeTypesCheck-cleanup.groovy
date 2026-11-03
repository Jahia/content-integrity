// Unregisters the ghost and orphan types of the UndeclaredNodeTypesCheck fixtures, once the nodes using them are deleted
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry
import org.springframework.core.io.ByteArrayResource

final String ORPHAN_SYSTEM_ID = "content-integrity-tests-orphan"
final String ORPHAN_CND = "<jnt = 'http://www.jahia.org/jahia/nt/1.0'>\n<jmix = 'http://www.jahia.org/jahia/mix/1.0'>\n" +
        "[jnt:ciTestOrphan] > jnt:content, jmix:droppableContent\n + * (jmix:droppableContent)\n"

JcrSessionFilter.endRequest()
def registry = BundleUtils.getOsgiService(NodeTypeRegistry.class, null)
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
if (session.nodeExists("/sites/SITEKEY/contents/undeclared-node-types")) {
    // Jackrabbit removes the child of a node only when it knows the type of that node. The cleanup of another site, such as
    // the one of the demo, may have unregistered the orphan type already: register it again
    registry.addDefinitionsFile(new ByteArrayResource(ORPHAN_CND.getBytes("UTF-8")) {
        String getFilename() { ORPHAN_SYSTEM_ID + ".cnd" }
    }, ORPHAN_SYSTEM_ID)
    BundleUtils.getOsgiService(JCRStoreService.class, null).deployDefinitions(ORPHAN_SYSTEM_ID)
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
try {
    registry.unregisterNodeType("jnt:ciTestGhost")
} catch (ignored) {
}
BundleUtils.getOsgiService(JCRStoreService.class, null).deployDefinitions("content-integrity")
[ORPHAN_SYSTEM_ID, "system-ci-tests-declared-again"].each { systemId ->
    try {
        BundleUtils.getOsgiService(JCRStoreService.class, null).undeployDefinitions(systemId)
        registry.unregisterNodeTypes(systemId)
    } catch (ignored) {
    }
}
JcrSessionFilter.endRequest()
