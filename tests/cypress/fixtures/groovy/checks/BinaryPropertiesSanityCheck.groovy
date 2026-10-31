// Fixtures of BinaryPropertiesSanityCheck, under /sites/SITEKEY/files/binary-properties
// - INVALID_BINARY: a file whose binary is empty, which is invalid when the zero byte binaries are not accepted
// - INVALID_BINARY under long-path: an empty file whose name makes the path of its binary longer than the 100 characters
//   an extra info is cut to, next to a valid file which the fix must keep, with their folder
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def files = session.getNode("/sites/SITEKEY/files")
if (files.hasNode("binary-properties")) files.getNode("binary-properties").remove()
def folder = files.addNode("binary-properties", "jnt:folder")
folder.uploadFile("empty.txt", new ByteArrayInputStream(new byte[0]), "text/plain")
folder.uploadFile("valid.txt", new ByteArrayInputStream("Not empty".getBytes("UTF-8")), "text/plain")
def longPath = folder.addNode("long-path", "jnt:folder")
longPath.uploadFile("empty-file-with-a-long-name-".padRight(86, "x") + ".txt", new ByteArrayInputStream(new byte[0]), "text/plain")
longPath.uploadFile("kept.txt", new ByteArrayInputStream("Not empty".getBytes("UTF-8")), "text/plain")
session.save()
JcrSessionFilter.endRequest()
