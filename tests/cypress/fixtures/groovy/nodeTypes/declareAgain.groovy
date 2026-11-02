// Declares again the type of the node declared-again of the UndeclaredNodeTypesCheck fixtures, unregistered by the fixtures.
// A system ID prefixed with system- is the one of a type of Jahia core, which the check takes as declared
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry
import org.springframework.core.io.ByteArrayResource

final String SYSTEM_ID = "system-ci-tests-declared-again"
final String CND = "<jnt = 'http://www.jahia.org/jahia/nt/1.0'>\n<jmix = 'http://www.jahia.org/jahia/mix/1.0'>\n" +
        "[jnt:ciTestDeclaredAgain] > jnt:content, jmix:droppableContent\n"

JcrSessionFilter.endRequest()
BundleUtils.getOsgiService(NodeTypeRegistry.class, null).addDefinitionsFile(new ByteArrayResource(CND.getBytes("UTF-8")) {
    String getFilename() { SYSTEM_ID + ".cnd" }
}, SYSTEM_ID)
BundleUtils.getOsgiService(JCRStoreService.class, null).deployDefinitions(SYSTEM_ID)
JcrSessionFilter.endRequest()
