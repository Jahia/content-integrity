import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.apache.jackrabbit.core.NodeImpl
import org.apache.jackrabbit.core.id.NodeId
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
            String path = "${MOUNTPOINT}/BinaryPropertiesSanityCheck-${workspace}.txt"
            if (!session.nodeExists(path)) {
                log.info "${path} does not exists"
                return null
            }
            def file = JCRContentUtils.downloadFileContent(session.getNode(path))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String uuid ->
                    try {
                        def node = session.getNodeByIdentifier(uuid).getRealNode()
                        log.info "#${++count} Remove node ${node.path}"
                        if(StringUtils.endsWith(node.path, 'jcr:content')) {
                            node = node.parent
                            uuid = node.identifier
                        }
                        (node.getParent() as NodeImpl).removeChildNode(NodeId.valueOf(uuid))
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
}

log.info "<<< END BinaryPropertiesSanityCheck"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
