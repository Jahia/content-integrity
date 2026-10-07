// Unregisters CiSlowCheck, registered by scan/slowCheck.groovy
import org.jahia.osgi.BundleUtils

def service = BundleUtils.getOsgiService("org.jahia.modules.contentintegrity.api.ContentIntegrityService", null)
def check = service.getContentIntegrityCheck("CiSlowCheck")
if (check != null) service.unregisterIntegrityCheck(check)
