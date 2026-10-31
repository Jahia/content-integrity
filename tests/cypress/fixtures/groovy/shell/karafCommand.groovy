// Runs the Karaf shell command KARAF_COMMAND, and writes what it prints in /sites/systemsite/files/ci-karaf/output.txt:
// the provisioning API runs a command, but does not return its output. The commands of the module print through
// System.out, which Karaf redirects to the streams of the shell session.
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.osgi.FrameworkService
import org.jahia.services.content.JCRSessionFactory

def output = new ByteArrayOutputStream()
def stream = new PrintStream(output, true, "UTF-8")
def context = FrameworkService.getBundleContext()
// The Karaf API is not exported to the scripts: the service is called through the name of its interface
def factory = context.getService(context.getServiceReference("org.apache.karaf.shell.api.console.SessionFactory"))
def shell = factory.create(new ByteArrayInputStream(new byte[0]), stream, stream)
try {
    shell.execute('''KARAF_COMMAND''')
} catch (Exception e) {
    stream.println("Command failed: " + e)
} finally {
    stream.flush()
    shell.close()
}

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def files = session.getNode("/sites/systemsite/files")
def folder = files.hasNode("ci-karaf") ? files.getNode("ci-karaf") : files.addNode("ci-karaf", "jnt:folder")
folder.uploadFile("output.txt", new ByteArrayInputStream(output.toByteArray()), "text/plain")
session.save()
JcrSessionFilter.endRequest()
