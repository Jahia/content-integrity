// Deletes the template created by the NodeNameInfoSanityCheck fixture under /modules
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.templates.JahiaTemplateManagerService

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def module = BundleUtils.getOsgiService(JahiaTemplateManagerService.class, null).getTemplatePackageById("content-integrity")
def templates = session.getNode("/modules/content-integrity/" + module.getVersion().toString() + "/templates")
if (templates.hasNode("ci-node-name-SITEKEY")) {
    templates.getNode("ci-node-name-SITEKEY").remove()
    session.save()
}
JcrSessionFilter.endRequest()
