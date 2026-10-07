// Fixtures of ReferencesSanityCheck, under /sites/SITEKEY/contents/references
// - BROKEN_REF: a reference to a node which does not exist
// - BROKEN_REF_TO_VN: a reference to a virtual node, known in jahia_external_mapping, but which can't be resolved
// - INVALID_BACK_REF is not reproducible: the referencing node is always readable with the system session of the scan
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.utils.DatabaseUtils

import javax.jcr.PropertyType

final String MISSING_UUID = "0c1e57ed-0000-4000-8000-000000000000"
final String VIRTUAL_UUID = "0c1e57ed-0000-4000-9000-000000000000"

def deleteMapping = { conn ->
    def statement = conn.prepareStatement("delete from jahia_external_mapping where internalUuid=?")
    statement.setString(1, VIRTUAL_UUID)
    statement.executeUpdate()
    statement.close()
}

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("references")) contents.getNode("references").remove()
def folder = contents.addNode("references", "jnt:contentFolder")
["broken-reference", "broken-reference-to-virtual-node", "valid-reference"].each { folder.addNode(it, "jnt:contentReference") }
folder.getNode("valid-reference").setProperty("j:node", folder.addNode("target", "jnt:text"))
session.save()

def conn = DatabaseUtils.getDatasource().getConnection()
try {
    deleteMapping(conn)
    def statement = conn.prepareStatement("insert into jahia_external_mapping (internalUuid, externalId, externalIdHash, providerKey) values (?, ?, ?, ?)")
    statement.setString(1, VIRTUAL_UUID)
    statement.setString(2, "/content-integrity-tests/missing")
    statement.setInt(3, "/content-integrity-tests/missing".hashCode())
    statement.setString(4, "content-integrity-tests-missing-provider")
    statement.executeUpdate()
    statement.close()
} finally {
    conn.close()
}

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def realFolder = folder.getRealNode()
    def factory = realFolder.getSession().getValueFactory()
    realFolder.getNode("broken-reference").setProperty("j:node", factory.createValue(MISSING_UUID, PropertyType.WEAKREFERENCE))
    realFolder.getNode("broken-reference-to-virtual-node").setProperty("j:node", factory.createValue(VIRTUAL_UUID, PropertyType.WEAKREFERENCE))
    realFolder.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
