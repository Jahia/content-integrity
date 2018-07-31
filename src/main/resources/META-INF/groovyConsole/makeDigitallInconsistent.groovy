import org.jahia.api.Constants
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRSessionWrapper

final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.getNode("/sites/digitall/home/j:translation_de").setProperty("jcr:language", "it")
def home_fr = session.getNode("/sites/digitall/home/j:translation_fr")
if (home_fr.hasProperty("jcr:language")) home_fr.getProperty("jcr:language").remove()
session.getNode("/sites/digitall/home").setProperty("j:isHomePage", false)
session.save()

def list = session.getNode("/sites/digitall/home/investors/contacts/section--container--row--grid-main")
if (!list.isMarkedForDeletion()) list.markForDeletion("")
list.removeMixin("jmix:markedForDeletionRoot")
session.save()

list.unmarkForDeletion()
session.save()

def site = session.getNode("/sites/digitall")
def m = site.getInstalledModules()
m.add("dummy-module")
site.setInstalledModules(m)
session.save()