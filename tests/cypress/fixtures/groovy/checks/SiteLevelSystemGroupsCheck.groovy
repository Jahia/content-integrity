// Fixtures of SiteLevelSystemGroupsCheck, on the site SITEKEY, for the scenario SCENARIO
// - GROUP_DOES_NOT_EXIST: the group site-privileged of the site is deleted
// - MISSING_MEMBERSHIP: the group site-privileged of the site is not a member of the server group privileged anymore
// - PLANTED_ACE: an ACE which grants editor to the user ci-SITEKEY-outsider, created directly under a page of the site,
//   outside of its ACL, as an editor can do since a page accepts any child. It grants nothing.
// The scenario RESTORE recreates the group and its membership, and deletes the user ci-SITEKEY-outsider
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaGroupManagerService
import org.jahia.services.usermanager.JahiaUserManagerService

final String SITE_KEY = "SITEKEY"
final String SCENARIO = "SCENARIO"
final String OUTSIDER = "ci-" + SITE_KEY + "-outsider"

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def groupManager = BundleUtils.getOsgiService(JahiaGroupManagerService.class, null)
def privileged = groupManager.lookupGroup(null, "privileged", session)
def sitePrivileged = groupManager.lookupGroup(SITE_KEY, "site-privileged", session)

switch (SCENARIO) {
    case "GROUP_DOES_NOT_EXIST":
        if (sitePrivileged != null) {
            groupManager.deleteGroup(sitePrivileged.getPath(), session)
            session.save()
        }
        break
    case "MISSING_MEMBERSHIP":
        if (sitePrivileged != null && privileged.isMember(sitePrivileged)) {
            privileged.removeMember(sitePrivileged)
            session.save()
        }
        break
    case "PLANTED_ACE":
        def userManager = BundleUtils.getOsgiService(JahiaUserManagerService.class, null)
        if (userManager.lookupUser(OUTSIDER, session) == null) {
            userManager.createUser(OUTSIDER, OUTSIDER + "-password", new Properties(), session)
            session.save()
        }
        def home = session.getNode("/sites/" + SITE_KEY + "/home")
        if (home.hasNode("planted-ace")) home.getNode("planted-ace").remove()
        def page = home.addNode("planted-ace", "jnt:page")
        page.setProperty("j:templateName", "home")
        session.save()
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            def ace = page.getRealNode().addNode("GRANT_u_" + OUTSIDER, "jnt:ace")
            ace.setProperty("j:aceType", "GRANT")
            ace.setProperty("j:principal", "u:" + OUTSIDER)
            ace.setProperty("j:roles", ["editor"] as String[])
            ace.setProperty("j:protected", false)
            ace.getSession().save()
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false)
        }
        break
    case "RESTORE":
        def outsider = BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupUser(OUTSIDER, session)
        if (outsider != null) {
            BundleUtils.getOsgiService(JahiaUserManagerService.class, null).deleteUser(outsider.getPath(), session)
            session.save()
        }
        if (sitePrivileged == null) {
            sitePrivileged = groupManager.createGroup(SITE_KEY, "site-privileged", new java.util.Properties(), true, session)
            // The site administrators are members of the group created with the site
            sitePrivileged.addMember(groupManager.lookupGroup(SITE_KEY, "site-administrators", session))
            session.save()
        }
        if (!privileged.isMember(sitePrivileged)) {
            privileged.addMember(sitePrivileged)
            session.save()
        }
        break
}
JcrSessionFilter.endRequest()
