// Fixtures of NodeNameInfoSanityCheck, under /sites/SITEKEY/contents/node-name-info
// - INVALID_FULLPATH: j:fullpath different from the path of the node
// - MISSING_NODENAME: no j:nodename
// - INVALID_NODENAME: j:nodename different from the name of the node
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("node-name-info")) contents.getNode("node-name-info").remove()
def folder = contents.addNode("node-name-info", "jnt:contentFolder")
["invalid-fullpath", "missing-nodename", "invalid-nodename"].each { folder.addNode(it, "jnt:contentFolder") }
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realFolder = folder.getRealNode()
    realFolder.getNode("invalid-fullpath").setProperty("j:fullpath", "/sites/SITEKEY/contents/somewhere-else")
    def missing = realFolder.getNode("missing-nodename")
    if (missing.hasProperty("j:nodename")) missing.getProperty("j:nodename").remove()
    realFolder.getNode("invalid-nodename").setProperty("j:nodename", "another-name")
    realFolder.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
