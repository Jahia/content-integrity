import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService

import javax.jcr.ItemNotFoundException
import javax.jcr.PathNotFoundException
import javax.jcr.RepositoryException

def jcrTemplate = BundleUtils.getOsgiService(JCRTemplate.class, null)
def jcrPublicationService = BundleUtils.getOsgiService(JCRPublicationService.class, null)

def getNodeInLive = { String path ->
    return jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
        try {
            return session.getNode(path).identifier
        } catch (RepositoryException e) {
            log.info "Node ${path} not found in live workspace"
            return null
        }
    })
}

def deleteNodeInLive = { String path ->
    jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
        try {
            def node = session.getNode(path)
            node.remove()
            if (SAVE) node.saveSession()
        } catch (PathNotFoundException e) {
            // Ignore it
        }
    })
}

def workspace = Constants.EDIT_WORKSPACE
log.info "Traversing workspace ${workspace}"
try {
    jcrTemplate.doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
        String path = "${MOUNTPOINT}/PublicationSanityDefaultCheck-PATH_CONFLICT-${workspace}.txt"
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
                    String nodeIdentifier = getNodeInLive(node.path)
                    if (nodeIdentifier != null) {
                        try {
                            // check node in default workspace
                            session.getNodeByIdentifier(nodeIdentifier)
                            log.warn "#${count} [WARN] Node ${node.path} already found in live workspace with another uuid, publish it to update it in live workspace"
                            if (SAVE) jcrPublicationService.publishByMainId(nodeIdentifier)
                        } catch (ItemNotFoundException e) {
                            log.info "#${count} [WARN] Delete node ${node.path} in live workspace and republish it"
                            deleteNodeInLive(node.path)
                            node.setProperty(Constants.PUBLISHED, false)
                            if (SAVE) jcrPublicationService.publishByMainId(node.identifier)
                        }
                    } else {
                        log.warn "#${count} [WARN] Node ${node.path} not found in live workspace"
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

log.info "<<< END PublicationSanityDefaultCheck-PATH_CONFLICT"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
