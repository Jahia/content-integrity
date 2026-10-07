// Fixtures of PublicationSanityLiveCheck, under /sites/SITEKEY/contents/publication-live
// - NO_DEFAULT_NODE: a published node, deleted from the default workspace only
// - UNEXPECTED_UGC: a published node, flagged as UGC (j:originWS=live) in live
// - INCONSISTENT_UGC: a published node, without j:originWS in live
// Once the deep comparison of the published nodes is configured:
// - MISSING_PROP_LIVE: a property removed in live
// - MISSING_PROP_DEFAULT: a property added in live
// - DIFFERENT_PROP_VAL: a property whose value is changed in live
// - DIFFERENT_MIXINS: a mixin added in live
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

final String FOLDER = "/sites/SITEKEY/contents/publication-live"

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
def folder = session.getNode("/sites/SITEKEY/contents").addNode("publication-live", "jnt:contentFolder")
["no-default-node", "unexpected-ugc", "inconsistent-ugc", "different-mixins"].each { folder.addNode(it, "jnt:contentFolder") }
def target = folder.addNode("target", "jnt:text")
target.setProperty("text", "Referenced in default")
folder.addNode("other-target", "jnt:text").setProperty("text", "Referenced in live")
folder.addNode("missing-property-live", "jnt:contentReference").setProperty("j:node", target)
folder.addNode("missing-property-default", "jnt:contentReference")
folder.addNode("different-property-value", "jnt:contentReference").setProperty("j:node", target)
session.save()
publish(folder.getIdentifier())

JcrSessionFilter.endRequest()
withoutListeners {
    def realDefault = factory.getCurrentSystemSession("default", null, null).getNode(FOLDER).getRealNode()
    realDefault.getNode("no-default-node").remove()
    realDefault.getSession().save()

    def realLive = factory.getCurrentSystemSession("live", null, null).getNode(FOLDER).getRealNode()
    realLive.getNode("unexpected-ugc").setProperty("j:originWS", "live")
    realLive.getNode("inconsistent-ugc").getProperty("j:originWS").remove()
    def otherTarget = realLive.getNode("other-target")
    realLive.getNode("missing-property-live").getProperty("j:node").remove()
    realLive.getNode("missing-property-default").setProperty("j:node", otherTarget)
    realLive.getNode("different-property-value").setProperty("j:node", otherTarget)
    realLive.getNode("different-mixins").addMixin("jmix:tagged")
    realLive.getSession().save()
}
JcrSessionFilter.endRequest()
