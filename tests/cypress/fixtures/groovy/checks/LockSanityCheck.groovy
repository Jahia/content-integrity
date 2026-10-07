// Fixtures of LockSanityCheck, under /sites/SITEKEY/contents/locks
// - INCONSISTENT_LOCK: a node with j:lockTypes, but without the other lock properties
// - DELETION_LOCK_ON_I18N: a translation node locked for deletion, while its node is not marked for deletion
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("locks")) contents.getNode("locks").remove()
def folder = contents.addNode("locks", "jnt:contentFolder")
folder.addNode("inconsistent-lock", "jnt:text").setProperty("text", "Inconsistent lock")
folder.addNode("deletion-lock-on-translation", "jnt:text").setProperty("text", "Deletion lock on the translation")
folder.addNode("not-locked", "jnt:text").setProperty("text", "Not locked")
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def inconsistent = folder.getNode("inconsistent-lock").getRealNode()
    inconsistent.setProperty("j:lockTypes", ["root:user"] as String[])
    def translation = folder.getNode("deletion-lock-on-translation").getRealNode().getNode("j:translation_en")
    translation.setProperty("j:lockTypes", [" deletion :deletion"] as String[])
    inconsistent.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
