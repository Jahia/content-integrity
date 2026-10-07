// Fixtures of LivePropertiesCheck, under /sites/SITEKEY/contents/live-properties
// - LIVE_PROPERTIES: a node flagged with jmix:liveProperties, with a value for j:liveProperties
// - EMPTY_LIVE_PROPERTIES: a node flagged with jmix:liveProperties, without j:liveProperties
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("live-properties")) contents.getNode("live-properties").remove()
def folder = contents.addNode("live-properties", "jnt:contentFolder")
["with-value", "without-value"].each { folder.addNode(it, "jnt:text") }
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realFolder = folder.getRealNode()
    def withValue = realFolder.getNode("with-value")
    withValue.addMixin("jmix:liveProperties")
    withValue.setProperty("j:liveProperties", ["text"] as String[])
    realFolder.getNode("without-value").addMixin("jmix:liveProperties")
    realFolder.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
