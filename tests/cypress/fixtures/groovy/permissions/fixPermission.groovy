// Fixtures of the permission to fix the errors. Two server roles, granted on / to two users. Both hold administrationAccess,
// to open the administration, and adminContentIntegrity: ci-SITEKEY-fixer holds adminContentIntegrityFix as well.
// The permission names of a role are protected properties, set as the import of a roles.xml file sets them.
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaUserManagerService

import javax.jcr.Value

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def findPermission = { name ->
    def query = session.getWorkspace().getQueryManager().createQuery("select * from [jnt:permission] where localname() = '" + name + "' and isdescendantnode('/modules')", "JCR-SQL2")
    def it = query.execute().getNodes()
    if (!it.hasNext()) throw new IllegalStateException("Permission not found: " + name)
    return it.nextNode()
}
def users = JahiaUserManagerService.getInstance()
["viewer": ["administrationAccess", "adminContentIntegrity"], "fixer": ["administrationAccess", "adminContentIntegrity", "adminContentIntegrityFix"]].each { profile, names ->
    def roleName = "ci-SITEKEY-" + profile
    def roles = session.getNode("/roles")
    if (!roles.hasNode(roleName)) {
        def role = roles.addNode(roleName, "jnt:role")
        role.setProperty("j:roleGroup", "server-role")
        role.setProperty("j:privilegedAccess", true)
        role.setProperty("j:permissions", names.collect { session.getValueFactory().createValue(findPermission(it), true) } as Value[])
        role.setProperty("j:permissionNames", names as String[])
    }
    def userName = "ci-SITEKEY-" + profile
    if (users.lookupUser(userName, session) == null) users.createUser(userName, userName + "-Pwd1!", new java.util.Properties(), session)
    session.save()
    session.getNode("/").changeRoles("u:" + userName, [(roleName): "GRANT"])
    session.save()
}
JcrSessionFilter.endRequest()
