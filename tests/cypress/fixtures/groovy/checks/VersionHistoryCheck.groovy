// Fixtures of VersionHistoryCheck, under /sites/SITEKEY/contents/version-history
// - TOO_MANY_VERSIONS: a node with 4 versions, which is too many once the threshold of the check is configured to 2
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("version-history")) contents.getNode("version-history").remove()
def folder = contents.addNode("version-history", "jnt:contentFolder")
def versioned = folder.addNode("many-versions", "jnt:contentFolder")
folder.addNode("few-versions", "jnt:contentFolder")
session.save()
def real = versioned.getRealNode()
def versionManager = real.getSession().getWorkspace().getVersionManager()
3.times { versionManager.checkpoint(real.getPath()) }
JcrSessionFilter.endRequest()
