// Fixtures of UndeclaredNodeTypesCheck, under /sites/SITEKEY/contents/undeclared-node-types
// - UNDECLARED_NODE_TYPE: nodes created with a primary type and a mixin which are then unregistered
// - GHOST_NODE_TYPE: a node created with a type registered for the started module content-integrity, which does not declare it
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry
import org.springframework.core.io.ByteArrayResource

final String TESTS_SYSTEM_ID = "content-integrity-tests-undeclared"
final String GHOST_SYSTEM_ID = "content-integrity"
final String HEADER = "<jnt = 'http://www.jahia.org/jahia/nt/1.0'>\n<jmix = 'http://www.jahia.org/jahia/mix/1.0'>\n"

def registry = NodeTypeRegistry.getInstance()
def register = { String systemId, String cnd ->
    registry.addDefinitionsFile(new ByteArrayResource((HEADER + cnd).getBytes("UTF-8")) {
        String getFilename() { systemId + ".cnd" }
    }, systemId)
    JCRStoreService.getInstance().deployDefinitions(systemId)
}

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("undeclared-node-types")) contents.getNode("undeclared-node-types").remove()
def folder = contents.addNode("undeclared-node-types", "jnt:contentFolder")
session.save()

register(TESTS_SYSTEM_ID, """
[jnt:ciTestUndeclared] > jnt:content, jmix:droppableContent
[jmix:ciTestUndeclaredMixin] mixin
""")
register(GHOST_SYSTEM_ID, """
[jnt:ciTestGhost] > jnt:content, jmix:droppableContent
""")

folder.addNode("undeclared-primary-type", "jnt:ciTestUndeclared")
folder.addNode("undeclared-mixin", "jnt:text").addMixin("jmix:ciTestUndeclaredMixin")
folder.addNode("ghost-type", "jnt:ciTestGhost")
session.save()

// The ghost type stays registered, the other ones are removed so that the nodes reference undeclared types
JCRStoreService.getInstance().undeployDefinitions(TESTS_SYSTEM_ID)
registry.unregisterNodeTypes(TESTS_SYSTEM_ID)
JcrSessionFilter.endRequest()
