// Deletes the users and the roles created by the fixtures of the permission to fix the errors
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def users = BundleUtils.getOsgiService(JahiaUserManagerService.class, null)
["viewer", "fixer"].each { profile ->
    def name = "ci-SITEKEY-" + profile
    session.getNode("/").changeRoles("u:" + name, [(name): "REMOVE"])
    def user = users.lookupUser(name, session)
    if (user != null) users.deleteUser(user.getPath(), session)
    if (session.nodeExists("/roles/" + name)) session.getNode("/roles/" + name).remove()
}
session.save()
JcrSessionFilter.endRequest()
