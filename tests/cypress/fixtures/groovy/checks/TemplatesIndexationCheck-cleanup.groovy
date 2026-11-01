// Deletes the templates created by the TemplatesIndexationCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.templates.JahiaTemplateManagerService

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
// The templates of the running version: the folder of a version installed before may remain, without templates
def module = BundleUtils.getOsgiService(JahiaTemplateManagerService.class, null).getTemplatePackageById("content-integrity")
def version = session.getNode("/modules/content-integrity/" + module.getVersion().toString())
def templates = version.getNode("templates")
["ci-not-indexed-SITEKEY", "ci-indexed-SITEKEY"].each { if (templates.hasNode(it)) templates.getNode(it).remove() }
session.save()
JcrSessionFilter.endRequest()
