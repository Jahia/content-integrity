// Fixtures of VersionSanityCheck, under /jcr:system/jcr:versionStorage/0c/1e/57
// - ORPHANED_HISTORY: the version history of a node which is then deleted from both workspaces
// - ORPHAN_IN_SUBTREE: the summary of the orphaned histories, raised on the root of the scan
// The node is created with a known identifier, which gives the path of its history:
// /jcr:system/jcr:versionStorage/0c/1e/57/0c1e57ed-0000-4000-a000-000000000001
// HISTORY_WITHOUT_NODE_ID is not reproducible: jcr:versionableUuid is a protected property, set by the version manager
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

final String IDENTIFIER = "0c1e57ed-0000-4000-a000-000000000001"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("version-sanity")) contents.getNode("version-sanity").remove()
def folder = contents.addNode("version-sanity", "jnt:contentFolder")
session.save()
def orphan = folder.addNode("orphan", "jnt:contentFolder", IDENTIFIER, null, null, null, null)
session.save()
def real = orphan.getRealNode()
real.getSession().getWorkspace().getVersionManager().checkpoint(real.getPath())
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    real.remove()
    folder.getRealNode().getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
