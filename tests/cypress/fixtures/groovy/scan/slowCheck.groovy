// Registers CiSlowCheck, a check which finds no error but waits on every node it scans, so that a scan lasts long enough
// to be stopped, or to be running while another one is started. The delay, in ms, replaces the token passed by the test.
// The scanned subtree, /sites/systemsite/contents/ci-slow-scan, is created with a fixed number of nodes, so that the scan
// lasts as long whatever the content of the instance. scan/slowCheck-cleanup.groovy unregisters the check and removes it.
// The check is a proxy of the interface, built from the class loader of the module: the module does not export its classes
// to the scripts.
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

final int SCANNED_NODES = 60
JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def site = session.getNode("/sites/systemsite")
def contents = site.hasNode("contents") ? site.getNode("contents") : site.addNode("contents", "jnt:contentList")
if (contents.hasNode("ci-slow-scan")) contents.getNode("ci-slow-scan").remove()
def root = contents.addNode("ci-slow-scan", "jnt:contentFolder")
(1..SCANNED_NODES - 1).each { root.addNode("node-" + it, "jnt:contentFolder") }
session.save()

def service = BundleUtils.getOsgiService("org.jahia.modules.contentintegrity.api.ContentIntegrityService", null)
def existing = service.getContentIntegrityCheck("CiSlowCheck")
if (existing != null) service.unregisterIntegrityCheck(existing)

def checkClass = service.getClass().getClassLoader().loadClass("org.jahia.modules.contentintegrity.api.ContentIntegrityCheck")
long delay = Long.parseLong("DELAY")
def handler = { proxy, method, args ->
    switch (method.name) {
        case "getId": case "getName": case "toString": case "toFullString": return "CiSlowCheck"
        case "hashCode": return System.identityHashCode(proxy)
        case "equals": return proxy.is(args[0])
        case "isValid": case "isEnabled": case "canRun": case "areConditionsMatched": case "areConditionsReachable": return true
        case "getPriority": return 1000f
        case "getOwnTime": return 0L
        case "checkIntegrityBeforeChildren":
            Thread.sleep(delay)
            return null
        default: return null
    }
} as InvocationHandler
def check = Proxy.newProxyInstance(checkClass.getClassLoader(), [checkClass] as Class[], handler)
service.registerIntegrityCheck(check)
