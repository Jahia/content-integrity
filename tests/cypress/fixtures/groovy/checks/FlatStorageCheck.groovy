// Fixtures of FlatStorageCheck, under /sites/SITEKEY/contents/flat-storage
// - TOO_MANY_CHILD_NODES: a folder with 5 children, which is too many once the threshold of the check is configured to 3
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("flat-storage")) contents.getNode("flat-storage").remove()
def folder = contents.addNode("flat-storage", "jnt:contentFolder")
def flat = folder.addNode("too-many-children", "jnt:contentFolder")
(1..5).each { flat.addNode("child-" + it, "jnt:text") }
def small = folder.addNode("few-children", "jnt:contentFolder")
(1..2).each { small.addNode("child-" + it, "jnt:text") }
session.save()
JcrSessionFilter.endRequest()
