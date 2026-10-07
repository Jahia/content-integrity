// Fixtures of BinaryPropertiesSanityCheck, under /sites/SITEKEY/files/binary-properties
// - INVALID_BINARY: a file whose binary is empty, which is invalid when the zero byte binaries are not accepted
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def files = session.getNode("/sites/SITEKEY/files")
if (files.hasNode("binary-properties")) files.getNode("binary-properties").remove()
def folder = files.addNode("binary-properties", "jnt:folder")
folder.uploadFile("empty.txt", new ByteArrayInputStream(new byte[0]), "text/plain")
folder.uploadFile("valid.txt", new ByteArrayInputStream("Not empty".getBytes("UTF-8")), "text/plain")
session.save()
JcrSessionFilter.endRequest()
