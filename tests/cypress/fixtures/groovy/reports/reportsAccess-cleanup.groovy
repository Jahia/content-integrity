// Deletes the users created by the reports access fixtures, and the server administrator role they hold on /
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
session.getNode("/").changeRoles("u:ci-SITEKEY-server-admin", ["server-administrator": "REMOVE"])
def users = JahiaUserManagerService.getInstance()
["ci-SITEKEY-editor", "ci-SITEKEY-server-admin"].each { name ->
    def user = users.lookupUser(name, session)
    if (user != null) users.deleteUser(user.getPath(), session)
}
session.save()
JcrSessionFilter.endRequest()
