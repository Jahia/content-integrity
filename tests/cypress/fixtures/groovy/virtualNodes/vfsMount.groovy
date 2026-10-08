// A VFS mount point, /mounts/MOUNT_NAME, on a folder of the server which holds an empty file: the node of the file is
// virtual, served by an external provider, and BinaryPropertiesSanityCheck reports its empty binary
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

def folder = new File(System.getProperty("java.io.tmpdir"), "MOUNT_NAME")
folder.mkdirs()
new File(folder, "empty.txt").createNewFile()

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def mounts = session.getNode("/mounts")
if (mounts.hasNode("MOUNT_NAME-mount")) {
    mounts.getNode("MOUNT_NAME-mount").remove()
    session.save()
}
mounts.addNode("MOUNT_NAME-mount", "jnt:vfsMountPoint").setProperty("j:rootPath", folder.toURI().toString())
session.save()
JcrSessionFilter.endRequest()
