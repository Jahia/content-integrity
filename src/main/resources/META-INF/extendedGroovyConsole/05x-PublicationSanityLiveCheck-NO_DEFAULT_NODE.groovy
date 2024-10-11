import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.api.usermanager.JahiaUserManagerService
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.decorator.JCRUserNode
import org.jahia.services.usermanager.JahiaUser

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def workspace = Constants.LIVE_WORKSPACE
log.info "Traversing workspace ${workspace}"
BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
    String path = "${MOUNTPOINT}/PublicationSanityLiveCheck-NO_DEFAULT_NODE-${workspace}.txt"
    if (!session.nodeExists(path)) {
        log.info "${path} does not exists"
        return null
    }
    def uuids = new ArrayList()
    def file = JCRContentUtils.downloadFileContent(session.getNode(path))
    def reader = new FileReader(file)
    def count = 0
    try {
        IOUtils.readLines(reader).each { String uuid ->
            ++count
            try {
                def node = session.getNodeByIdentifier(uuid)
                uuids.add(uuid)
                log.info "#${count} Restore node ${node.path}"
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
    if (SAVE && uuids.size() > 0) {
        def jcrSessionFactory = BundleUtils.getOsgiService(JCRSessionFactory.class, null)
        JahiaUser realUser = jcrSessionFactory.getCurrentUser()
        boolean changeUser = realUser == null || !realUser.isRoot()
        if (changeUser) {
            JCRUserNode rootUser = BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupRootUser()
            jcrSessionFactory.setCurrentUser(rootUser.getJahiaUser())
        }

        try {
            log.info "Recreating ${uuids.size()} nodes"
            BundleUtils.getOsgiService(JCRPublicationService.class, null).publish(uuids, Constants.LIVE_WORKSPACE, Constants.EDIT_WORKSPACE, false, null)
        } finally {
            if (changeUser) {
                jcrSessionFactory.setCurrentUser(realUser)
            }
        }
    }
})

log.info "<<< END PublicationSanityLiveCheck-NO_DEFAULT_NODE"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
