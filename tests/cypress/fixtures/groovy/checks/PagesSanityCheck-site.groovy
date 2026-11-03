// Fixtures of PagesSanityCheck, on the site SITEKEY, apart from the ones of the pages, which other specs share
// - MISSING_TEMPLATE: the site, whose default template does not exist
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", Locale.ENGLISH, null)
session.getNode("/sites/SITEKEY").setProperty("j:defaultTemplateName", "ci-default-template-which-does-not-exist")
session.save()
JcrSessionFilter.endRequest()
