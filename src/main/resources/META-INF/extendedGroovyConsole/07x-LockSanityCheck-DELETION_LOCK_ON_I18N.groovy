import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            String path = "${MOUNTPOINT}/LockSanityCheck-DELETION_LOCK_ON_I18N-${workspace}.txt"
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
                        log.info "#${count} Repair LockSanityCheck-DELETION_LOCK_ON_I18N for node ${node.path}"
                        node.markForDeletion('LockSanityCheck-DELETION_LOCK_ON_I18N')
                        node.unmarkForDeletion()
                        if (node.hasProperty('jcr:lockIsDeep')) node.getProperty('jcr:lockIsDeep').remove()
                        if (node.hasProperty('jcr:lockOwner')) node.getProperty('jcr:lockOwner').remove()
                        if (node.hasProperty('j:lockTypes')) node.getProperty('j:lockTypes').remove()
                        if (node.hasProperty('j:locktoken')) node.getProperty('j:locktoken').remove()
                        if (node.hasProperty('j:deletionMessage')) node.getProperty('j:deletionMessage').remove()
                        if (node.hasProperty('j:deletionDate')) node.getProperty('j:deletionDate').remove()
                        if (node.hasProperty('j:deletionUser')) node.getProperty('j:deletionUser').remove()
                        if (SAVE) session.save()
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

log.info "<<< END LockSanityCheck-DELETION_LOCK_ON_I18N"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
