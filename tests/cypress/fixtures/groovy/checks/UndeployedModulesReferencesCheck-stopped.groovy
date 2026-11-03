// Fixtures of UndeployedModulesReferencesCheck, on the site SITEKEY, for the scenario SCENARIO
// - STOP: the module jahia-dashboard-docs, installed on Jahia 8.1 and 8.2 and which no other module depends on, is
//   stopped, and the site references it in both workspaces: it is not available, so the site references a module which
//   is not deployed
// - START: the module is started again
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.osgi.FrameworkService
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

final String MODULE = "jahia-dashboard-docs"
final String SCENARIO = "SCENARIO"

def bundle = FrameworkService.getBundleContext().getBundles().find { it.getSymbolicName() == MODULE }
if (bundle == null) throw new IllegalStateException("Module not installed: " + MODULE)

switch (SCENARIO) {
    case "STOP":
        bundle.stop()
        JcrSessionFilter.endRequest()
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            ["default", "live"].each { workspace ->
                def site = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession(workspace, null, null).getNode("/sites/SITEKEY").getRealNode()
                def modules = site.getProperty("j:installedModules").getValues().collect { it.getString() }
                if (!modules.contains(MODULE)) modules << MODULE
                site.setProperty("j:installedModules", modules as String[])
                site.getSession().save()
            }
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
        JcrSessionFilter.endRequest()
        break
    case "START":
        bundle.start()
        break
}
