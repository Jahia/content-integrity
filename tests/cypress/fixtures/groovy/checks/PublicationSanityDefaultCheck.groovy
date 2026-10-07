// Fixtures of PublicationSanityDefaultCheck, under /sites/SITEKEY/contents/publication-default
// - NO_LIVE_NODE: a node flagged as published, which has never been published
// - DIFFERENT_PATH: a published node, moved in live only (scanned from a parent node)
// - DIFFERENT_PATH_POTENTIAL_FP: the same error, when the moved node is the root of the scan
// - PATH_CONFLICT: a node of the default workspace, and another node with the same path in live
// - DIFFERENT_PT: a published node, whose live node has another primary type
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

final String FOLDER = "/sites/SITEKEY/contents/publication-default"

def factory = JCRSessionFactory.getInstance()
def withoutListeners = { Closure c ->
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        c.call()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}
def removeEverywhere = { String path ->
    ["live", "default"].each { workspace ->
        def session = factory.getCurrentSystemSession(workspace, null, null)
        if (session.nodeExists(path)) withoutListeners {
            def real = session.getNode(path).getRealNode()
            def realSession = real.getSession()
            real.remove()
            realSession.save()
        }
    }
}
def publish = { String uuid ->
    def user = factory.getCurrentUser()
    factory.setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser())
    try {
        JCRPublicationService.getInstance().publishByMainId(uuid, "default", "live", null, true, null)
    } finally {
        factory.setCurrentUser(user)
    }
}

JcrSessionFilter.endRequest()
removeEverywhere(FOLDER)
def session = factory.getCurrentSystemSession("default", Locale.ENGLISH, null)
def folder = session.getNode("/sites/SITEKEY/contents").addNode("publication-default", "jnt:contentFolder")
def moved = folder.addNode("moved-in-live", "jnt:contentFolder")
moved.addNode("child", "jnt:text").setProperty("text", "Child of a node moved in live")
folder.addNode("live-destination", "jnt:contentFolder")
folder.addNode("different-primary-type", "jnt:text").setProperty("text", "Different primary type in live")
folder.addNode("path-conflict", "jnt:contentFolder")
session.save()
publish(folder.getIdentifier())

def neverPublished = folder.addNode("flagged-as-published", "jnt:text")
neverPublished.setProperty("text", "Flagged as published, but never published")
session.save()

withoutListeners {
    def realFlagged = neverPublished.getRealNode()
    realFlagged.setProperty("j:published", true)
    realFlagged.getSession().save()

    def live = factory.getCurrentSystemSession("live", null, null)
    def realLive = live.getNode(FOLDER).getRealNode()
    def liveSession = realLive.getSession()
    liveSession.move(FOLDER + "/moved-in-live", FOLDER + "/live-destination/moved-in-live")
    realLive.getNode("different-primary-type").setPrimaryType("jnt:bigText")
    // A node with another identifier, at the path of a node of the default workspace
    def conflicting = realLive.getNode("path-conflict")
    conflicting.remove()
    realLive.addNode("path-conflict", "jnt:contentFolder")
    liveSession.save()
}
JcrSessionFilter.endRequest()
