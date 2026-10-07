// Registers CiSlowCheck, a check which finds no error but waits on every node it scans, so that a scan lasts long enough
// to be stopped, or to be running while another one is started. The delay, in ms, replaces the token passed by the test.
// scan/slowCheck-cleanup.groovy unregisters the check.
// The check is a proxy of the interface, built from the class loader of the module: the module does not export its classes
// to the scripts.
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.jahia.osgi.BundleUtils

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
