// Removes the external mapping and the VFS mount point created by the ReferencesSanityCheck fixtures
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory
import org.jahia.utils.DatabaseUtils

def conn = DatabaseUtils.getDatasource().getConnection()
try {
    def statement = conn.prepareStatement("delete from jahia_external_mapping where internalUuid=?")
    statement.setString(1, "0c1e57ed-0000-4000-9000-000000000000")
    statement.executeUpdate()
    statement.close()
} finally {
    conn.close()
}

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
if (session.nodeExists("/mounts/SITEKEY-backref-mount")) {
    session.getNode("/mounts/SITEKEY-backref-mount").remove()
    session.save()
}
JcrSessionFilter.endRequest()
new File(System.getProperty("java.io.tmpdir"), "SITEKEY-backref").deleteDir()
