// Fixtures of UserAccountSanityCheck, on the user ci-SITEKEY-user
// - NOT_OWNER: the user has an ACE on its account node, but with the role reader instead of owner
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

final String USERNAME = "ci-SITEKEY-user"

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def userManager = BundleUtils.getOsgiService(JahiaUserManagerService.class, null)
def user = userManager.lookupUser(USERNAME, session)
if (user == null) {
    user = userManager.createUser(USERNAME, "ci-SITEKEY-password", new java.util.Properties(), session)
    session.save()
}
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def ace = user.getRealNode().getNode("j:acl").getNodes().find { it.getProperty("j:principal").getString() == "u:" + USERNAME }
    ace.setProperty("j:roles", ["reader"] as String[])
    ace.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
