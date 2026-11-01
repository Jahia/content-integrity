// Fixtures of the publication of the fixed ACL by AceSanityCheck, under /sites/SITEKEY/home:
// - synced: a published page whose ACL grants ci-SITEKEY-a the role editor and a role which does not exist, the same in
//   both workspaces: the fix of ROLE_DOESNT_EXIST publishes it
// - pending: the same page, whose ACL also grants ci-SITEKEY-b the role editor in default only, a change which awaits its
//   publication: the fix does not publish the ACL, so that this grant is not published without its review
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

import javax.jcr.PropertyType
import javax.jcr.Value

final String HOME = "/sites/SITEKEY/home"
final String MISSING_ROLE = "ci-role-which-does-not-exist"
final List<String> PAGES = ["synced", "pending"]

def factory = BundleUtils.getOsgiService(JCRSessionFactory.class, null)
def publish = { String uuid ->
    def user = factory.getCurrentUser()
    factory.setCurrentUser(BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupRootUser().getJahiaUser())
    try {
        BundleUtils.getOsgiService(JCRPublicationService.class, null).publishByMainId(uuid, "default", "live", null, true, null)
    } finally {
        factory.setCurrentUser(user)
    }
}

JcrSessionFilter.endRequest()
def session = factory.getCurrentSystemSession("default", Locale.ENGLISH, null)
def home = session.getNode(HOME)
PAGES.each { name ->
    if (home.hasNode(name)) home.getNode(name).remove()
    def page = home.addNode(name, "jnt:page")
    page.setProperty("jcr:title", name)
    page.setProperty("j:templateName", "home")
    page.grantRoles("u:ci-SITEKEY-a", ["editor"] as Set)
}
session.save()

// The role which does not exist is written without the listeners, as a script or an import would
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    PAGES.each { name ->
        def ace = session.getNode("${HOME}/${name}/j:acl/GRANT_u_ci-SITEKEY-a").getRealNode()
        def values = ace.getSession().getValueFactory()
        ace.setProperty("j:roles", [values.createValue("editor", PropertyType.NAME), values.createValue(MISSING_ROLE, PropertyType.NAME)] as Value[])
        ace.getSession().save()
    }
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}

publish(home.getIdentifier())
PAGES.each { publish(session.getNode("${HOME}/${it}").getIdentifier()) }

// A grant which awaits its publication
session.getNode("${HOME}/pending").grantRoles("u:ci-SITEKEY-b", ["editor"] as Set)
session.save()
JcrSessionFilter.endRequest()
