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

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/UndeployedModulesReferencesCheck-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    def data = row.split(';')
                    def uuid = data[0]
                    // TODO fix this when more than one module are identified
                    def modulesToRemove = Collections.singletonList(data[1]).toArray()
                    try {
                        def siteNode = session.getNodeByIdentifier(uuid)
                        if (siteNode.isNodeType(Constants.JAHIANT_VIRTUALSITE) && siteNode.hasProperty(SitesSettings.INSTALLED_MODULES)) {
                            List<JCRValueWrapper> modules = []
                            siteNode.getProperty(SitesSettings.INSTALLED_MODULES).getValues().each { v ->
                                if (!modulesToRemove.contains(v.getString())) {
                                    modules.add(v)
                                }
                            }
                            log.info "#${++count} SiteNode ${siteNode} set installed modules ${modules.each { v -> v.getString() }}"
                            siteNode.setProperty(SitesSettings.INSTALLED_MODULES, modules.toArray(new ValueImpl[0]) as ValueImpl[])
                            if (SAVE) session.save()
                        }
                    } catch (ItemNotFoundException e) {
                        log.warn "#${++count} uuid not found: ${uuid}"
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

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
