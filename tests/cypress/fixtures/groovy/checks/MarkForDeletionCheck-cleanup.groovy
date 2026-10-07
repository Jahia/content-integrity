// Deletes the user created by the MarkForDeletionCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def userManager = JahiaUserManagerService.getInstance()
def user = userManager.lookupUser("ci-SITEKEY", session)
if (user != null) {
    userManager.deleteUser(user.getPath(), session)
    session.save()
}
JcrSessionFilter.endRequest()
