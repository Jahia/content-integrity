// Unregisters CiSlowCheck, registered by scan/slowCheck.groovy, and removes the subtree it scans
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

def service = BundleUtils.getOsgiService("org.jahia.modules.contentintegrity.api.ContentIntegrityService", null)
def check = service.getContentIntegrityCheck("CiSlowCheck")
if (check != null) service.unregisterIntegrityCheck(check)

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
if (session.nodeExists("/sites/systemsite/contents/ci-slow-scan")) {
    session.getNode("/sites/systemsite/contents/ci-slow-scan").remove()
    session.save()
}
