// Deletes the users created by the AceSanityCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def userManager = BundleUtils.getOsgiService(JahiaUserManagerService.class, null)
(["ace", "ace-privileged"] + (0..10).collect { "ace-" + it }).each { suffix ->
    def user = userManager.lookupUser("ci-SITEKEY-" + suffix, session)
    if (user != null) userManager.deleteUser(user.getPath(), session)
}
session.save()
JcrSessionFilter.endRequest()
