// Fixtures of UnreadablePublicationStatusCheck, under /sites/SITEKEY/contents/unreadable-publication-status
// - UNREADABLE_PUBLICATION_STATUS: in live, a translation node in German, a language which is not active on the site
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
// A previous run may have published the fixtures: they are removed from both workspaces
["live", "default"].each { workspace ->
    def previous = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (previous.nodeExists("/sites/SITEKEY/contents/unreadable-publication-status")) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            def real = previous.getNode("/sites/SITEKEY/contents/unreadable-publication-status").getRealNode()
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
def folder = contents.addNode("unreadable-publication-status", "jnt:contentFolder")
folder.addNode("translated-in-inactive-language", "jnt:text").setProperty("text", "English")
folder.addNode("translated-in-active-language", "jnt:text").setProperty("text", "English")
session.save()
def factory = JCRSessionFactory.getInstance()
def user = factory.getCurrentUser()
factory.setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser())
try {
    JCRPublicationService.getInstance().publishByMainId(folder.getIdentifier(), "default", "live", null, true, null)
} finally {
    factory.setCurrentUser(user)
}

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def live = factory.getCurrentSystemSession("live", null, null)
    def text = live.getNode(folder.getPath() + "/translated-in-inactive-language").getRealNode()
    def translation = text.addNode("j:translation_de", "jnt:translation")
    translation.setProperty("jcr:language", "de")
    translation.setProperty("text", "Deutsch")
    text.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
