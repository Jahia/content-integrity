// Fixtures of ChildNodeDefinitionsSanityCheck, under /sites/SITEKEY/contents/child-node-definitions
// - NOT_ALLOWED_BY_PARENT_DEF: a node created when its type was droppable content, then its type stops being droppable,
//   so that its parent, a jnt:contentFolder, does not accept it anymore
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry
import org.springframework.core.io.ByteArrayResource

final String SYSTEM_ID = "content-integrity-tests-child-node-definitions"
final String HEADER = "<jnt = 'http://www.jahia.org/jahia/nt/1.0'>\n<jmix = 'http://www.jahia.org/jahia/mix/1.0'>\n"

def registry = NodeTypeRegistry.getInstance()
def register = { String cnd ->
    try {
        JCRStoreService.getInstance().undeployDefinitions(SYSTEM_ID)
    } catch (ignored) {
    }
    registry.unregisterNodeTypes(SYSTEM_ID)
    registry.addDefinitionsFile(new ByteArrayResource((HEADER + cnd).getBytes("UTF-8")) {
        String getFilename() { SYSTEM_ID + ".cnd" }
    }, SYSTEM_ID)
    JCRStoreService.getInstance().deployDefinitions(SYSTEM_ID)
}

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("child-node-definitions")) contents.getNode("child-node-definitions").remove()
def folder = contents.addNode("child-node-definitions", "jnt:contentFolder")
register("[jnt:ciTestChild] > jnt:content, jmix:droppableContent")
folder.addNode("not-allowed", "jnt:ciTestChild")
folder.addNode("allowed", "jnt:text")
session.save()
register("[jnt:ciTestChild] > jnt:content")
JcrSessionFilter.endRequest()
