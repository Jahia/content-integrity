// Deletes the templates created by the TemplatesIndexationCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def version = session.getNode("/modules/content-integrity").getNodes().find { it.isNodeType("jnt:moduleVersion") }
def templates = version.getNode("templates")
["ci-not-indexed-SITEKEY", "ci-indexed-SITEKEY"].each { if (templates.hasNode(it)) templates.getNode(it).remove() }
session.save()
JcrSessionFilter.endRequest()
