// Fixtures of PublicationSanityDefaultCheck, under the deployed module templates-system
// - NO_LIVE_NODE: a folder of the module, in the default workspace only, as its content would be if its deployment had
//   not published it
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def version = session.getNode("/modules/templates-system").getNodes().find { it.isNodeType("jnt:moduleVersion") }
def contents = version.getNode("contents")
if (contents.hasNode("ci-unpublished-SITEKEY")) contents.getNode("ci-unpublished-SITEKEY").remove()
contents.addNode("ci-unpublished-SITEKEY", "jnt:contentFolder")
session.save()
JcrSessionFilter.endRequest()
