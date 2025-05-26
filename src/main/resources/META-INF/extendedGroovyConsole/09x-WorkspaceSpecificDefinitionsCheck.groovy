import org.jahia.api.Constants
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRObservationManager

def wipLive(JCRNodeWrapper node, boolean save, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (node.hasProperty(Constants.WORKINPROGRESS_STATUS) || node.hasProperty(Constants.WORKINPROGRESS_LANGUAGES) || node.hasProperty(Constants.WORKINPROGRESS)) {
            if (node.hasProperty(Constants.WORKINPROGRESS_STATUS)) node.getProperty(Constants.WORKINPROGRESS_STATUS).remove()
            if (node.hasProperty(Constants.WORKINPROGRESS_LANGUAGES)) node.getProperty(Constants.WORKINPROGRESS_LANGUAGES).remove()
            if (node.hasProperty(Constants.WORKINPROGRESS)) node.getProperty(Constants.WORKINPROGRESS).remove()
            log.info "Remove WIP on node ${node.path}"
            counter++
            if (save) node.saveSession()
        }

        if (node.hasNodes()) {
            node.nodes.forEach { JCRNodeWrapper child -> counter = wipLive(child, save, counter) }
        }
    }
    return counter
}

def fixWipLive = { ->
    log.info ">>> START WipSanityCheck..."
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        long counter = jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
            return wipLive(session.rootNode, SAVE, 0)
        })
        log.info "#${counter} nodes in workspace ${Constants.LIVE_WORKSPACE}"
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
    log.info "<<< ...END WipSanityCheck"
}

def unmarkForDeletionDefault(JCRNodeWrapper node, boolean save, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION) && JCRContentUtils.getParentOfType(node, 'jmix:autoPublish') != null) {
            log.info "Unmark node ${node.path} for deletion because my parent is ${JCRContentUtils.getParentOfType(node, 'jmix:autoPublish')}"
            counter++
            if (save) {
                node.unmarkForDeletion()
                node.saveSession()
            }
        }

        if (node.hasNodes()) {
            node.nodes.forEach { JCRNodeWrapper child -> counter = unmarkForDeletionDefault(child, save, counter) }
        }
    }
    return counter
}

def unmarkForDeletionLive(JCRNodeWrapper node, boolean save, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION)) {
            log.info "Unmark node ${node.path} for deletion"
            counter++
            if (save) {
                try {
                    node.markForDeletion('unmarkForDeletionLive')
                } catch (Exception e) {
                    // Nothing to do
                }
                try {
                    node.unmarkForDeletion()
                } catch (Exception e) {
                    // Nothing to do
                }
                node.saveSession()
            }
        }

        if (node.hasNodes()) {
            node.nodes.forEach { JCRNodeWrapper child -> counter = unmarkForDeletionLive(child, save, counter) }
        }
    }
    return counter
}

def fixMarkForDeletion = { ->
    log.info ">>> START MarkForDeletionCheck..."
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        log.info "Traversing workspace ${Constants.EDIT_WORKSPACE}"
        long counter = jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, { session ->
            return unmarkForDeletionDefault(session.getNode('/users'), SAVE, 0)
        })
        log.info "#${counter} nodes in workspace ${Constants.EDIT_WORKSPACE}"

        log.info "Traversing workspace ${Constants.LIVE_WORKSPACE}"
        counter = jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
            return unmarkForDeletionLive(session.rootNode, SAVE, 0)
        })
        log.info "#${counter} nodes in workspace ${Constants.LIVE_WORKSPACE}"
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
    log.info "<<< ...END MarkForDeletionCheck"
}

log.info "<<< END WorkspaceSpecificDefinitionsCheck"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
