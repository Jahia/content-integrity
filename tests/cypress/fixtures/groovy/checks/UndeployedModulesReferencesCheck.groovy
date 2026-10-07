// Fixtures of UndeployedModulesReferencesCheck, on the site SITEKEY
// - UNDEPLOYED_MODULE_ON_SITE: the site references a module which is not deployed on the server, in both workspaces
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    ["default", "live"].each { workspace ->
        def site = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null).getNode("/sites/SITEKEY").getRealNode()
        def modules = site.getProperty("j:installedModules").getValues().collect { it.getString() }
        if (!modules.contains("ci-undeployed-module")) modules << "ci-undeployed-module"
        site.setProperty("j:installedModules", modules as String[])
        site.getSession().save()
    }
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
