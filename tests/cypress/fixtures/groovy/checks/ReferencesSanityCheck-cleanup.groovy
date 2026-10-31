// Removes the external mapping created by the ReferencesSanityCheck fixtures
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
