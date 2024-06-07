import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def workspace = Constants.LIVE_WORKSPACE
log.info "Traversing workspace ${workspace}"
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
        def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/PublicationSanityLiveCheck-NO_DEFAULT_NODE-${workspace}.txt"))
        def reader = new FileReader(file)
        def count = 0
        try {
            IOUtils.readLines(reader).each { String uuid ->
                try {
                    def node = session.getNodeByIdentifier(uuid)
                    if (StringUtils.endsWith(node.path, 'jcr:content')) {
                        node = node.parent
                    }
                    log.info "#${++count} Remove node ${node.path}"
                    node.remove()
                    if (SAVE) session.save()
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

log.info "<<< END PublicationSanityLiveCheck-NO_DEFAULT_NODE"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
