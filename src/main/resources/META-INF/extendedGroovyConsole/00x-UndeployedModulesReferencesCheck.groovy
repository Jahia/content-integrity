import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRValueWrapper
import org.jahia.services.content.nodetypes.ValueImpl
import org.jahia.services.sites.SitesSettings

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def MOUNTPOINT = '/sites/systemsite/files/content-integrity'
def SAVE = false
def MODULES_TO_REMOVED = ['v8-modules-helper']

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/UndeployedModulesReferencesCheck-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String uuid ->
                    try {
                        def siteNode = session.getNodeByIdentifier(uuid)
                        if (siteNode.isNodeType(Constants.JAHIANT_VIRTUALSITE) && siteNode.hasProperty(SitesSettings.INSTALLED_MODULES)) {
                            List<JCRValueWrapper> modules = []
                            siteNode.getProperty(SitesSettings.INSTALLED_MODULES).getValues().each { v ->
                                if (!MODULES_TO_REMOVED.contains(v.getString())) {
                                    modules.add(v)
                                }
                            }
                            log.info "#${++count} SiteNode ${siteNode} set installed modules ${modules.each { v -> v.getString() }}"
                            siteNode.setProperty(SitesSettings.INSTALLED_MODULES, modules.toArray(new ValueImpl[0]) as ValueImpl[])
                            if (SAVE) session.save()
                        }
                    } catch (ItemNotFoundException e) {
                        // Nothing to do
                    } catch (RepositoryException e) {
                        log.error("", e)
                    }
                }
            } finally {
                IOUtils.closeQuietly(reader)
            }
        })
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}

log.info "<<< END UndeployedModulesReferencesCheck"
