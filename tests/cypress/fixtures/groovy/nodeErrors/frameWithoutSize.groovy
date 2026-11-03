// A node with two errors which block each other, under /sites/SITEKEY/contents/node-errors: a jnt:frame without its two
// mandatory properties, width and height. A node is saved only once all its mandatory properties are set, so neither
// error is fixed alone. The properties are removed with the Jackrabbit node, below the Jahia validation which refuses it
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("node-errors")) contents.getNode("node-errors").remove()
def folder = contents.addNode("node-errors", "jnt:contentFolder")
def frame = folder.addNode("frame-without-size", "jnt:frame")
frame.setProperty("width", 640L)
frame.setProperty("height", 480L)
session.save()
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def real = frame.getRealNode()
    real.getProperty("width").remove()
    real.getProperty("height").remove()
    real.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
