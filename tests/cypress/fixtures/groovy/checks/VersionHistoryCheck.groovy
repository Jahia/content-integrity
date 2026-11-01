// Fixtures of VersionHistoryCheck, under /sites/SITEKEY/contents/version-history
// - TOO_MANY_VERSIONS: a node with 4 versions, which is too many once the threshold of the check is configured to 2
// - TOO_MANY_VERSIONS which the purge can't fix: a node published twice, whose versions are still used by the default
//   and the live nodes, so that the purge keeps more versions than the threshold of 2
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def factory = BundleUtils.getOsgiService(JCRSessionFactory.class, null)
factory.setCurrentUser(BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupRootUser().getJahiaUser())
def session = factory.getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("version-history")) contents.getNode("version-history").remove()
def folder = contents.addNode("version-history", "jnt:contentFolder")
def versioned = folder.addNode("many-versions", "jnt:contentFolder")
folder.addNode("few-versions", "jnt:contentFolder")
session.save()
def real = versioned.getRealNode()
def versionManager = real.getSession().getWorkspace().getVersionManager()
3.times { versionManager.checkpoint(real.getPath()) }

def published = folder.addNode("used-versions", "jnt:text")
published.setProperty("text", "first")
session.save()
def publicationService = BundleUtils.getOsgiService(JCRPublicationService.class, null)
publicationService.publishByMainId(folder.getIdentifier(), "default", "live", null, true, null)
published.setProperty("text", "second")
session.save()
publicationService.publishByMainId(published.getIdentifier(), "default", "live", null, true, null)
