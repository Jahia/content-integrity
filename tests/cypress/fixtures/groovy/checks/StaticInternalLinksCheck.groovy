// Fixtures of StaticInternalLinksCheck, under /sites/SITEKEY/contents/static-internal-links
// - HARDCODED_DOMAIN: a text containing the domain of a site. The domains are read on the live sites, so the domain of the
//   site is set in both workspaces
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

final String DOMAIN = "SITEKEY.content-integrity.test"

JcrSessionFilter.endRequest()
def factory = JCRSessionFactory.getInstance()
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    ["default", "live"].each { workspace ->
        def site = factory.getCurrentSystemSession(workspace, null, null).getNode("/sites/SITEKEY").getRealNode()
        site.setProperty("j:serverName", DOMAIN)
        site.getSession().save()
    }
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
def session = factory.getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("static-internal-links")) contents.getNode("static-internal-links").remove()
def folder = contents.addNode("static-internal-links", "jnt:contentFolder")
folder.addNode("hardcoded-domain", "jnt:bigText").setProperty("text", "<a href=\"https://" + DOMAIN + "/home.html\">Home</a>")
folder.addNode("relative-link", "jnt:bigText").setProperty("text", "<a href=\"/home.html\">Home</a>")
session.save()
JcrSessionFilter.endRequest()
