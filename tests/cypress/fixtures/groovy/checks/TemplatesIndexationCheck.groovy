// Fixtures of TemplatesIndexationCheck, in the templates of the module content-integrity
// - NOT_INDEXED_TEMPLATE: a template whose document is removed from the search index of the default workspace
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.apache.jackrabbit.core.id.NodeId

final String TEMPLATE_NAME = "ci-not-indexed-SITEKEY"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def version = session.getNode("/modules/content-integrity").getNodes().find { it.isNodeType("jnt:moduleVersion") }
def templates = version.getNode("templates")
if (templates.hasNode(TEMPLATE_NAME)) templates.getNode(TEMPLATE_NAME).remove()
def template = templates.addNode(TEMPLATE_NAME, "jnt:template")
templates.addNode("ci-indexed-SITEKEY", "jnt:template")
session.save()

// The template is removed from the index, without being removed from the repository
def repository = JCRSessionFactory.getInstance().getDefaultProvider().getRepository().getRepository()
def getSearchManager = org.apache.jackrabbit.core.RepositoryImpl.getDeclaredMethod("getSearchManager", String)
getSearchManager.setAccessible(true)
def searchIndex = getSearchManager.invoke(repository, "default").getQueryHandler()
searchIndex.updateNodes([NodeId.valueOf(template.getIdentifier())].iterator(), [].iterator())
searchIndex.flush()
JcrSessionFilter.endRequest()
