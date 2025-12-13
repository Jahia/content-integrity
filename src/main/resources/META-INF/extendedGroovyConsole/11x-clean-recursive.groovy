import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRObservationManager

import javax.jcr.query.Query

def jcrTemplate = BundleUtils.getOsgiService(JCRTemplate.class, null)
def SAVE = true

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
            return wipLive(session.rootNode as JCRNodeWrapper, SAVE, 0)
        })
        log.info "#${counter} nodes in workspace ${Constants.LIVE_WORKSPACE}"
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
    log.info "<<< ...END WipSanityCheck"
}

def unmarkForDeletionDefault(JCRNodeWrapper node, boolean save, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION) && JCRContentUtils.getParentOfType(node as JCRNodeWrapper, 'jmix:autoPublish') != null) {
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

def fixNonUgc = { ->
    log.info ">>> START NonUgc..."
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        def count = 0
        jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
            log.info "Traversing workspace ${session.workspace.name}"
            session.workspace.queryManager.createQuery("SELECT * FROM [nt:base] WHERE [j:originWS] = 'live'", Query.JCR_SQL2).execute().nodes.each { JCRNodeWrapper node ->
                log.info "#${++count} Reset non UGC node ${node.path}"
                node.getProperty(Constants.ORIGIN_WORKSPACE).remove()
                if (SAVE) node.saveSession()
            }
        })
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
    log.info "<<< ...END NonUgc"
}

static def nodeExistsInLive(String uuid) {
    return BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, { session ->
        try {
            return session.getNodeByIdentifier(uuid).path
        } catch (Exception e) {
            return null
        }
    })
}

def fixFullpath(JCRNodeWrapper node, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (!node.isNodeType('jmix:nodenameInfo')) {
            if (node.hasProperty(Constants.NODENAME)) {
                node.getProperty(Constants.NODENAME).remove()
            }
            if (node.hasProperty(Constants.FULLPATH)) {
                node.getProperty(Constants.FULLPATH).remove()
            }
        } else if (node.session.workspace.name == Constants.EDIT_WORKSPACE) {
            def nodePathInLive = nodeExistsInLive(node.identifier)
            if (nodePathInLive == null) {
                if (node.hasProperty(Constants.FULLPATH)) {
                    node.getProperty(Constants.FULLPATH).remove()
                }
            } else {
                node.setProperty(Constants.FULLPATH, nodePathInLive)
            }
        } else {
            node.setProperty(Constants.FULLPATH, node.path)
        }
        ++counter
        node.saveSession()

        if (node.hasNodes()) {
            node.nodes.forEach { child -> counter = fixFullpath(child, counter) }
        }
    }
    return counter
}

def checkFullpath = { ->
    log.info ">>> START Fullpath..."
    for (def workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            log.info "Traversing workspace ${workspace}"
            long counter = jcrTemplate.doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
                return fixFullpath(session.rootNode, 0)
            })
            log.info "#${counter} nodes in workspace ${workspace}"
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
    }
    log.info "<<< ...END Fullpath"
}

def fixLiveProperties(JCRNodeWrapper node, long counter) {
    if (!"jcr:system".equals(node.name)) {
        if (node.isNodeType('jmix:liveProperties')) {
            ++counter
            if (node.hasProperty('j:liveProperties')) node.getProperty('j:liveProperties').remove()
            node.removeMixin('jmix:liveProperties')
            node.saveSession()
        }
        if (node.hasNodes()) {
            node.nodes.forEach { child -> counter = fixLiveProperties(child, counter) }
        }
    }
    return counter
}

def checkLiveProperties = { ->
    log.info ">>> START LiveProperties..."
    for (def workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            log.info "Traversing workspace ${workspace}"
            long counter = jcrTemplate.doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
                return fixLiveProperties(session.rootNode, 0)
            })
            log.info "#${counter} nodes in workspace ${workspace}"
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
    }
    log.info "<<< ...END LiveProperties"
}

fixWipLive()
fixMarkForDeletion()
fixNonUgc()
// checkFullpath()
checkLiveProperties()

log.info ""
