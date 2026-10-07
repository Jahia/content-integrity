// Fixtures of WipSanityCheck, under /sites/SITEKEY/contents/wip. The site languages are en and fr
// - WIP_ON_TRANSLATION_NODE: j:workInProgressStatus on a translation node
// - WIP_LEGACY_FORMAT: the legacy property j:workInProgress
// - WIP_UNEXPECTED_LANG: the status LANGUAGES with a language which is not a language of the site
// - WIP_MISSING_PROP: the status LANGUAGES without j:workInProgressLanguages
// - WIP_UNEXPECTED_PROP: the status ALL_CONTENT with j:workInProgressLanguages
// - WIP_INCONSISTENT_STATUS_PROP: an unknown status
// - WIP_IN_LIVE: a published node with a WIP status in the live workspace
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService
import org.apache.jackrabbit.core.id.PropertyId
import org.apache.jackrabbit.core.value.InternalValue

JcrSessionFilter.endRequest()
// A previous run may have published the fixtures: they are removed from both workspaces
["live", "default"].each { workspace ->
    def previous = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (previous.nodeExists("/sites/SITEKEY/contents/wip")) {
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            def real = previous.getNode("/sites/SITEKEY/contents/wip").getRealNode()
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
def folder = contents.addNode("wip", "jnt:contentFolder")
["on-translation", "legacy-format", "unexpected-language", "missing-languages", "unexpected-languages", "inconsistent-status", "in-live", "valid-wip"]
        .each { folder.addNode(it, "jnt:text").setProperty("text", it) }
session.save()
def user = factory.getCurrentUser()
factory.setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser())
try {
    JCRPublicationService.getInstance().publishByMainId(folder.getIdentifier(), "default", "live", null, true, null)
} finally {
    factory.setCurrentUser(user)
}
def valid = folder.getNode("valid-wip")
valid.setProperty("j:workInProgressStatus", "LANGUAGES")
valid.setProperty("j:workInProgressLanguages", ["en"] as String[])
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realFolder = folder.getRealNode()
    realFolder.getNode("on-translation").getNode("j:translation_en").setProperty("j:workInProgressStatus", "ALL_CONTENT")
    realFolder.getNode("legacy-format").setProperty("j:workInProgress", true)
    def unexpectedLanguage = realFolder.getNode("unexpected-language")
    unexpectedLanguage.setProperty("j:workInProgressStatus", "LANGUAGES")
    unexpectedLanguage.setProperty("j:workInProgressLanguages", ["en", "de"] as String[])
    realFolder.getNode("missing-languages").setProperty("j:workInProgressStatus", "LANGUAGES")
    def unexpectedLanguages = realFolder.getNode("unexpected-languages")
    unexpectedLanguages.setProperty("j:workInProgressStatus", "ALL_CONTENT")
    unexpectedLanguages.setProperty("j:workInProgressLanguages", ["en"] as String[])
    realFolder.getNode("inconsistent-status").setProperty("j:workInProgressStatus", "DISABLED")
    realFolder.getSession().save()

    // Jackrabbit refuses a status out of the choice list, even on save: the stored value is replaced in the workspace
    // item states, below the session validation
    def inconsistent = realFolder.getNode("inconsistent-status")
    def itemStates = inconsistent.getSession().getWorkspace().getItemStateManager()
    def statusId = new PropertyId(inconsistent.getNodeId(), inconsistent.getSession().getQName("j:workInProgressStatus"))
    itemStates.edit()
    def status = itemStates.getItemState(statusId)
    status.setValues([InternalValue.create("NOT_A_STATUS")] as InternalValue[])
    itemStates.store(status)
    itemStates.update()

    def live = factory.getCurrentSystemSession("live", null, null).getNode(folder.getPath() + "/in-live").getRealNode()
    live.setProperty("j:workInProgressStatus", "ALL_CONTENT")
    live.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
