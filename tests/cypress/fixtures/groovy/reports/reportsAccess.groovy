// Fixtures of the access to the scan reports: an editor of the site SITEKEY, and a server administrator who is not root
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def users = JahiaUserManagerService.getInstance()
["ci-SITEKEY-editor", "ci-SITEKEY-server-admin"].each { name ->
    if (users.lookupUser(name, session) == null) users.createUser(name, name + "-Pwd1!", new java.util.Properties(), session)
}
session.save()
session.getNode("/sites/SITEKEY").changeRoles("u:ci-SITEKEY-editor", ["editor": "GRANT"])
session.getNode("/").changeRoles("u:ci-SITEKEY-server-admin", ["server-administrator": "GRANT"])
session.save()
JcrSessionFilter.endRequest()
