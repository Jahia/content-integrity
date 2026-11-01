// Fixtures of NodeNameInfoSanityCheck, under /sites/SITEKEY/contents/node-name-info
// - INVALID_FULLPATH: j:fullpath different from the path of the node
// - MISSING_NODENAME: no j:nodename
// - INVALID_NODENAME: j:nodename different from the name of the node
// And a template of the module content-integrity, without j:nodename, which is not checked under /modules, and with an
// invalid j:fullpath, which is
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.templates.JahiaTemplateManagerService

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("node-name-info")) contents.getNode("node-name-info").remove()
def folder = contents.addNode("node-name-info", "jnt:contentFolder")
["invalid-fullpath", "missing-nodename", "invalid-nodename"].each { folder.addNode(it, "jnt:contentFolder") }
// The templates of the running version: the folder of a version installed before may remain, without templates
def module = BundleUtils.getOsgiService(JahiaTemplateManagerService.class, null).getTemplatePackageById("content-integrity")
def templates = session.getNode("/modules/content-integrity/" + module.getVersion().toString() + "/templates")
if (templates.hasNode("ci-node-name-SITEKEY")) templates.getNode("ci-node-name-SITEKEY").remove()
def template = templates.addNode("ci-node-name-SITEKEY", "jnt:template")
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realFolder = folder.getRealNode()
    realFolder.getNode("invalid-fullpath").setProperty("j:fullpath", "/sites/SITEKEY/contents/somewhere-else")
    def missing = realFolder.getNode("missing-nodename")
    if (missing.hasProperty("j:nodename")) missing.getProperty("j:nodename").remove()
    realFolder.getNode("invalid-nodename").setProperty("j:nodename", "another-name")
    realFolder.getSession().save()
    def realTemplate = template.getRealNode()
    if (realTemplate.hasProperty("j:nodename")) realTemplate.getProperty("j:nodename").remove()
    realTemplate.setProperty("j:fullpath", "/modules/somewhere-else")
    realTemplate.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
