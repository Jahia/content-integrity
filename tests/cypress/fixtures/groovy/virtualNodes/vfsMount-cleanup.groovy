// Removes the mount point created by virtualNodes/vfsMount.groovy, and its folder
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
if (session.nodeExists("/mounts/MOUNT_NAME-mount")) {
    session.getNode("/mounts/MOUNT_NAME-mount").remove()
    session.save()
}
JcrSessionFilter.endRequest()
def folder = new File(System.getProperty("java.io.tmpdir"), "MOUNT_NAME")
folder.listFiles()?.each { it.delete() }
folder.delete()
