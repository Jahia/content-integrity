// Fixtures of LockSanityCheck for the locks of the workflows, under /sites/SITEKEY/contents/workflow-locks
// A workflow locks its nodes, publishes them, then unlocks them when it ends (JCRPublicationService, PublicationJob).
// The processes 999999990 to 999999999 don't exist, as the process of a workflow removed without ending.
// - LOCK_OF_UNKNOWN_PROCESS: unknown-process, locked for validation by a publication process which doesn't exist,
//   custom-lock-of-unknown-process, locked by the lock step of a workflow definition, of a process which doesn't exist
// - LOCK_PROPERTIES_IN_LIVE: workflow-ended and unknown-process, published for the first time while locked: their lock
//   type and token are cloned to live, without the JCR lock
// - user-lock: locked by a user, and published-before-lock, published before its lock: no error
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

JcrSessionFilter.endRequest()
def factory = BundleUtils.getOsgiService(JCRSessionFactory.class, null)
// The publication service locks the nodes as the current user
factory.setCurrentUser(BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupRootUser().getJahiaUser())
def session = factory.getCurrentUserSession("default", Locale.ENGLISH)
// Applied once, on a new site: the locked nodes of a previous application can't be removed by this script
def contents = session.getNode("/sites/SITEKEY/contents")
def folder = contents.addNode("workflow-locks", "jnt:contentFolder")
def text = { String name -> def n = folder.addNode(name, "jnt:text"); n.setProperty("text", name); n }
def unknownProcess = text("unknown-process")
def workflowEnded = text("workflow-ended")
def customLock = text("custom-lock-of-unknown-process")
def userLock = text("user-lock")
def publishedBeforeLock = text("published-before-lock")
session.save()

def publication = JCRPublicationService.getInstance()
publication.publish([folder.identifier, publishedBeforeLock.identifier], "default", "live", false, null)

publication.lockForPublication([unknownProcess.identifier], "default", "publication-process-999999999")
publication.publish([unknownProcess.identifier], "default", "live", false, null)

publication.lockForPublication([workflowEnded.identifier], "default", "publication-process-999999998")
publication.publish([workflowEnded.identifier], "default", "live", false, null)
publication.unlockForPublication([workflowEnded.identifier], "default", "publication-process-999999998")

publication.lockForPublication([publishedBeforeLock.identifier], "default", "publication-process-999999997")
publication.publish([publishedBeforeLock.identifier], "default", "live", false, null)
publication.unlockForPublication([publishedBeforeLock.identifier], "default", "publication-process-999999997")

// As the workflows, with a session without locale: the translation nodes are not locked
def noLocale = factory.getCurrentUserSession("default")
noLocale.getNodeByIdentifier(customLock.identifier).lockAndStoreToken("custom", " process-999999996 ")
noLocale.getNodeByIdentifier(userLock.identifier).lockAndStoreToken("user")
JcrSessionFilter.endRequest()
