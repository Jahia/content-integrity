// Fixtures of MarkForDeletionCheck, under /sites/SITEKEY/contents/mark-for-deletion and in the files of the user ci-SITEKEY
// - NO_ROOT_DELETION: a node flagged as deleted, without any node flagged as the root of the deletion above it
// - DELETION_MARK_IN_LIVE: a published node, flagged as deleted in the live workspace
// - DELETION_MARK_UNDER_USERS: a folder of a user, marked for deletion
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

final String USERNAME = "ci-SITEKEY"

def asRoot = { Closure c ->
    def factory = JCRSessionFactory.getInstance()
    def user = factory.getCurrentUser()
    factory.setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser())
    try {
        c.call()
    } finally {
        factory.setCurrentUser(user)
    }
}
def withoutListeners = { Closure c ->
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        c.call()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}

JcrSessionFilter.endRequest()
// A previous run may have published the fixtures: they are removed from both workspaces
["live", "default"].each { workspace ->
    def previous = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (previous.nodeExists("/sites/SITEKEY/contents/mark-for-deletion")) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            def real = previous.getNode("/sites/SITEKEY/contents/mark-for-deletion").getRealNode()
            def realSession = real.getSession()
            real.remove()
            realSession.save()
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
    }
}
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
def folder = contents.addNode("mark-for-deletion", "jnt:contentFolder")
folder.addNode("no-root-deletion", "jnt:text").setProperty("text", "Flagged as deleted without root")
folder.addNode("deleted-in-live", "jnt:text").setProperty("text", "Flagged as deleted in live")
folder.addNode("regular-deletion", "jnt:text").setProperty("text", "Regularly marked for deletion")
session.save()
asRoot { JCRPublicationService.getInstance().publishByMainId(folder.getIdentifier(), "default", "live", null, true, null) }
folder.getNode("regular-deletion").markForDeletion("Regular deletion")
session.save()

def userManager = JahiaUserManagerService.getInstance()
def user = userManager.lookupUser(USERNAME, session)
if (user == null) {
    user = userManager.createUser(USERNAME, "ci-SITEKEY-password", new java.util.Properties(), session)
    session.save()
}
def files = user.hasNode("files") ? user.getNode("files") : user.addNode("files", "jnt:folder")
if (files.hasNode("marked-for-deletion")) files.getNode("marked-for-deletion").remove()
files.addNode("marked-for-deletion", "jnt:folder")
session.save()
files.getNode("marked-for-deletion").markForDeletion("Deletion under /users")
session.save()

withoutListeners {
    def noRoot = folder.getNode("no-root-deletion").getRealNode()
    noRoot.addMixin("jmix:markedForDeletion")
    noRoot.getSession().save()
    def live = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
    def inLive = live.getNode(folder.getPath() + "/deleted-in-live").getRealNode()
    inLive.addMixin("jmix:markedForDeletion")
    inLive.getSession().save()
}
JcrSessionFilter.endRequest()
