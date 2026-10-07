// Fixtures of PagesSanityCheck, under /sites/SITEKEY/home
// - MISSING_TEMPLATE: a page which uses a template which does not exist
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null)
def home = session.getNode("/sites/SITEKEY/home")
if (home.hasNode("missing-template")) home.getNode("missing-template").remove()
def page = home.addNode("missing-template", "jnt:page")
page.setProperty("jcr:title", "Missing template")
page.setProperty("j:templateName", "ci-template-which-does-not-exist")
session.save()
JcrSessionFilter.endRequest()
