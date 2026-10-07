// Fixtures of SiteLevelSystemGroupsCheck, on the site SITEKEY, for the scenario SCENARIO
// - GROUP_DOES_NOT_EXIST: the group site-privileged of the site is deleted
// - MISSING_MEMBERSHIP: the group site-privileged of the site is not a member of the server group privileged anymore
// The scenario RESTORE recreates the group and its membership
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaGroupManagerService

final String SITE_KEY = "SITEKEY"
final String SCENARIO = "SCENARIO"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def groupManager = JahiaGroupManagerService.getInstance()
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
    case "RESTORE":
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
