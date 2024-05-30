import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def MOUNTPOINT = '/sites/systemsite/files/content-integrity'
def SAVE = false

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/LockSanityCheck-INCONSISTENT_LOCK-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String uuid ->
                    try {
                        def node = session.getNodeByIdentifier(uuid)
                        node.lock(true, false)
                        node.unlock()
                        if (node.hasProperty('j:lockTypes')) node.getProperty('j:lockTypes').remove()
                        if (node.hasProperty('j:locktoken')) node.getProperty('j:locktoken').remove()
                        if (SAVE) session.save()
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

log.info "<<< END LockSanityCheck-INCONSISTENT_LOCK"
