// Fixtures of WorkspaceSpecificDefinitionsCheck, under /sites/SITEKEY/contents/workspace-specific-definitions
// - UNEXPECTED_TYPE: in live, a node flagged with jmix:markedForDeletion, a type allowed only in default (default configuration)
// - UNEXPECTED_PROP_VALUE: in live, j:workInProgressStatus=ALL_CONTENT, while only DISABLED is allowed in live (default configuration)
// - UNEXPECTED_PROP: in default, a node with j:liveProperties, once the check is configured to allow it only in live
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
// A previous run may have published the fixtures: they are removed from both workspaces
["live", "default"].each { workspace ->
    def previous = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (previous.nodeExists("/sites/SITEKEY/contents/workspace-specific-definitions")) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            def real = previous.getNode("/sites/SITEKEY/contents/workspace-specific-definitions").getRealNode()
            def realSession = real.getSession()
            real.remove()
            realSession.save()
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
    }
}
def factory = JCRSessionFactory.getInstance()
def session = factory.getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
def folder = contents.addNode("workspace-specific-definitions", "jnt:contentFolder")
["unexpected-type", "unexpected-property-value", "unexpected-property"].each { folder.addNode(it, "jnt:text").setProperty("text", it) }
session.save()
def user = factory.getCurrentUser()
factory.setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser())
try {
    JCRPublicationService.getInstance().publishByMainId(folder.getIdentifier(), "default", "live", null, true, null)
} finally {
    factory.setCurrentUser(user)
}

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realDefault = folder.getRealNode().getNode("unexpected-property")
    realDefault.addMixin("jmix:liveProperties")
    realDefault.setProperty("j:liveProperties", ["text"] as String[])
    realDefault.getSession().save()

    def live = factory.getCurrentSystemSession("live", null, null).getNode(folder.getPath()).getRealNode()
    live.getNode("unexpected-type").addMixin("jmix:markedForDeletion")
    live.getNode("unexpected-property-value").setProperty("j:workInProgressStatus", "ALL_CONTENT")
    live.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
