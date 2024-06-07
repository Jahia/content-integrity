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
        def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/PublicationSanityDefaultCheck-NO_LIVE_NODE-AutoPublish-${workspace}.txt"))
        def reader = new FileReader(file)
        def count = 0
        try {
            IOUtils.readLines(reader).each { String uuid ->
                try {
                    def node = session.getNodeByIdentifier(uuid)
                    if (node.hasProperty(Constants.PUBLISHED)) {
                        node.getProperty(Constants.PUBLISHED).remove()
                    }
                    node.setProperty(Constants.JCR_LASTMODIFIEDBY, 'root')
                    if (SAVE) session.save()
                    publicationService.publishByMainId(uuid)
                    log.info "#${++count} Autopublish node ${node.path}"
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

log.info "<<< END PublicationSanityDefaultCheck-NO_LIVE_NODE-AutoPublish"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
