import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def publicationService = BundleUtils.getOsgiService(JCRPublicationService.class, null)

def workspace = Constants.EDIT_WORKSPACE
log.info "Traversing workspace ${workspace}"
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
        String path = "${MOUNTPOINT}/PublicationSanityDefaultCheck-NO_LIVE_NODE_AUTO_PUBLISH-${workspace}.txt"
        if (!session.nodeExists(path)) {
            log.info "${path} does not exists"
            return null
        }
        def file = JCRContentUtils.downloadFileContent(session.getNode(path))
        def reader = new FileReader(file)
        def count = 0
        try {
            IOUtils.readLines(reader).each { String uuid ->
                ++count
                try {
                    def node = session.getNodeByIdentifier(uuid)
                    if (node.hasProperty(Constants.PUBLISHED)) {
                        node.getProperty(Constants.PUBLISHED).remove()
                    }
                    node.setProperty(Constants.JCR_LASTMODIFIEDBY, 'root')
                    if (SAVE) session.save()
                    publicationService.publishByMainId(uuid)
                    log.info "#${count} Autopublish node ${node.path}"
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

log.info "<<< END PublicationSanityDefaultCheck-NO_LIVE_NODE_AUTO_PUBLISH"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
