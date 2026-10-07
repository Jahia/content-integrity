// Deletes the orphaned version history created by the VersionSanityCheck fixtures: a history is removed with its last version
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

final String HISTORY = "/jcr:system/jcr:versionStorage/0c/1e/57/0c1e57ed-0000-4000-a000-000000000001"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
if (session.nodeExists(HISTORY)) {
    def history = session.getNode(HISTORY).getRealNode()
    history.getAllVersions().toList().findAll { it.getName() != "jcr:rootVersion" }.each { history.removeVersion(it.getName()) }
}
JcrSessionFilter.endRequest()
