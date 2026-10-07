// Fixtures of JCRLanguagePropertyCheck, under /sites/SITEKEY/contents/jcr-language
// - MISSING_JCR_LANGUAGE_PROP: a translation node without jcr:language
// - INCONSISTENT_JCR_LANGUAGE_PROP: the translation node j:translation_en with jcr:language=fr
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("jcr-language")) contents.getNode("jcr-language").remove()
def folder = contents.addNode("jcr-language", "jnt:contentFolder")
folder.addNode("missing-language", "jnt:text").setProperty("text", "Missing jcr:language")
folder.addNode("inconsistent-language", "jnt:text").setProperty("text", "Inconsistent jcr:language")
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def missing = folder.getNode("missing-language").getRealNode().getNode("j:translation_en")
    missing.getProperty("jcr:language").remove()
    def inconsistent = folder.getNode("inconsistent-language").getRealNode().getNode("j:translation_en")
    inconsistent.setProperty("jcr:language", "fr")
    missing.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
