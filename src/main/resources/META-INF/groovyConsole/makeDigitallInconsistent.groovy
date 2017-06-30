import org.jahia.api.Constants
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRSessionWrapper

final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.getNode("/sites/digitall/home/j:translation_de").setProperty("jcr:language", "it")
session.getNode("/sites/digitall/home/j:translation_fr").getProperty("jcr:language").remove()
session.save()