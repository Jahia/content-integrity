import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.sites.SitesSettings

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            String path = "${MOUNTPOINT}/UndeployedModulesReferencesCheck-${workspace}.txt"
            if (!session.nodeExists(path)) {
                log.info "${path} does not exists"
                return null
            }
            def file = JCRContentUtils.downloadFileContent(session.getNode(path))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    ++count
                    def data = row.split(';')
                    def uuid = data[0]
                    // TODO fix this when more than one module are identified
                    def modulesToRemove = Collections.singletonList(data[1])
                    try {
                        def siteNode = session.getNodeByIdentifier(uuid)
                        if (siteNode.isNodeType(Constants.JAHIANT_VIRTUALSITE) && siteNode.hasProperty(SitesSettings.INSTALLED_MODULES)) {
                            List<String> modules = []
                            siteNode.getProperty(SitesSettings.INSTALLED_MODULES).getValues().each { v ->
                                if (!modulesToRemove.contains(v.getString())) {
                                    modules.add(v.getString())
                                }
                            }
                            log.info "#${count} SiteNode ${siteNode} set installed modules ${modules.each { v -> v }}"
                            siteNode.setProperty(SitesSettings.INSTALLED_MODULES, modules.toArray(new String[0]) as String[])
                            if (SAVE) session.save()
                        }
                    } catch (ItemNotFoundException e) {
                        log.warn "#${count} [WARN] uuid not found: ${uuid}"
                        // Nothing to do
                    } catch (RepositoryException e) {
                        log.error "#${count} [ERROR]", e
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

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
