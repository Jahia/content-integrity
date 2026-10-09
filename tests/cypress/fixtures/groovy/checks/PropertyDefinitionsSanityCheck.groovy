// Fixtures of PropertyDefinitionsSanityCheck, under /sites/SITEKEY/contents/property-definitions and /mounts/ci-tests-validation
// The values are written with the Jackrabbit node, below the Jahia validation which refuses them
// - EMPTY_MANDATORY_PROPERTY: jnt:frame without its mandatory width
// - INVALID_VALUE_TYPE / INVALID_MULTI_VALUE_STATUS / INVALID_VALUE_CONSTRAINT / UNDECLARED_PROPERTY: values written with a definition,
//   which is then replaced by an incompatible one (Jackrabbit refuses to write such values)
// - INVALID_VALUE_CONSTRAINT on a multiple property: the second of three values, on the node invalid-choices
// - INVALID_NODE_VALIDATION: a jnt:vfsMountPoint without j:rootPath, which MountPointAvailabilityValidator requires
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRStoreService
import org.jahia.services.content.nodetypes.NodeTypeRegistry
import org.springframework.core.io.ByteArrayResource

final String SYSTEM_ID = "content-integrity-tests-property-definitions"
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
def withoutListeners = { Closure c ->
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        c.call()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("property-definitions")) contents.getNode("property-definitions").remove()
def folder = contents.addNode("property-definitions", "jnt:contentFolder")
def frame = folder.addNode("missing-mandatory", "jnt:frame")
frame.setProperty("width", 640L)
frame.setProperty("height", 480L)
register("""
[jnt:ciTestTypedValues] > jnt:content, jmix:droppableContent
 - ciTestNumber (string)
 - ciTestList (string)
 - ciTestChoice (string)
 - ciTestRemoved (string)
 - ciTestChoices (string) multiple
""")
def typed = folder.addNode("invalid-values", "jnt:ciTestTypedValues")
typed.setProperty("ciTestNumber", "not a number")
typed.setProperty("ciTestList", "single value")
typed.setProperty("ciTestChoice", "not-a-choice")
typed.setProperty("ciTestRemoved", "removed from the definition")
def choices = folder.addNode("invalid-choices", "jnt:ciTestTypedValues")
choices.setProperty("ciTestChoices", ["first", "not-a-choice", "second"] as String[])
session.save()

withoutListeners {
    def realFrame = frame.getRealNode()
    realFrame.getProperty("width").remove()
    realFrame.getSession().save()
}

// The definition changes: the values written with the previous one don't match it anymore
register("""
[jnt:ciTestTypedValues] > jnt:content, jmix:droppableContent
 - ciTestNumber (long)
 - ciTestList (string) multiple
 - ciTestChoice (string) < 'first', 'second'
 - ciTestChoices (string) multiple < 'first', 'second'
""")

// The validator applies to the mount points, which live under /mounts
def mounts = session.getNode("/mounts")
if (mounts.hasNode("ci-tests-validation")) {
    withoutListeners {
        def real = mounts.getNode("ci-tests-validation").getRealNode()
        real.remove()
        real.getSession().save()
    }
}
withoutListeners {
    def realMounts = mounts.getRealNode()
    def mountPoint = realMounts.addNode("ci-tests-validation", "jnt:vfsMountPoint")
    mountPoint.setProperty("j:rootPath", "/tmp/content-integrity-tests")
    realMounts.getSession().save()
    mountPoint.getProperty("j:rootPath").remove()
    realMounts.getSession().save()
}
JcrSessionFilter.endRequest()
