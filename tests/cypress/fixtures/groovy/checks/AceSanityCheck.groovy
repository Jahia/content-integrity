// Fixtures of AceSanityCheck, under /sites/SITEKEY/contents/ace: one node per scenario, named after the error type.
// A role is granted on the node, then its ACE, or the external ACE created on the site for the role editor, is altered with
// the listeners disabled. The scenarios which alter an external ACE use their own user, so that they have their own external ACE.
// - NO_PRINCIPAL, NO_ACE_TYPE_PROP, NO_ROLES_PROP: a property removed from the ACE
// - INVALID_PRINCIPAL: an ACE for a user which does not exist
// - INVALID_NODENAME: an ACE renamed
// - ROLE_DOESNT_EXIST: an ACE for a role which does not exist
// - MISSING_SITE_PRIVILEGED_GRP_MEMBER: a user granted editor, removed from the group site-privileged
// - MISSING_EXTERNAL_ACE: the external ACE of a GRANT ACE deleted
// - ACE_NON_GRANT_WITH_EXTERNAL_ACE / SOURCE_ACE_NOT_TYPE_GRANT: the type of an ACE changed to DENY, while it has an external ACE
// - INVALID_ACE_TYPE_PROP, NO_SOURCE_ACE_PROP, EMPTY_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF, INVALID_EXTERNAL_PERMISSIONS,
//   DUPLICATED_REF_SRC_ACE, INVALID_ROLES_PROP: an external ACE altered
// - INVALID_EXTERNAL_ACE_PATH: an external ACE moved to the ACL of another node
// - ROLES_DIFFER_ON_SOURCE_ACE: the role of an ACE changed, while its external ACE is still for the former role
// - TOO_MANY_ACE: raised on the site once the threshold of the check is configured
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaGroupManagerService
import org.jahia.services.usermanager.JahiaUserManagerService

import javax.jcr.PropertyType
import javax.jcr.Value

final String SITE_KEY = "SITEKEY"
final String FOLDER = "/sites/" + SITE_KEY + "/contents/ace"
final String MISSING_UUID = "0c1e57ed-0000-4000-b000-000000000000"

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def userManager = JahiaUserManagerService.getInstance()
def contents = session.getNode("/sites/" + SITE_KEY + "/contents")
if (contents.hasNode("ace")) contents.getNode("ace").remove()
def folder = contents.addNode("ace", "jnt:contentFolder")
session.save()

def user = { String suffix ->
    def name = "ci-" + SITE_KEY + "-" + suffix
    if (userManager.lookupUser(name, session) == null) {
        userManager.createUser(name, name + "-password", new java.util.Properties(), session)
        session.save()
    }
    return name
}
// Grants the role on a new node named after the scenario
def grant = { String scenario, String username, String role ->
    def node = folder.addNode(scenario, "jnt:contentFolder")
    session.save()
    node.changeRoles("u:" + username, [(role): "GRANT"])
    session.save()
    return scenario
}

def regularUser = user("ace")
grant("NO_PRINCIPAL", regularUser, "reader")
grant("NO_ACE_TYPE_PROP", regularUser, "reader")
grant("NO_ROLES_PROP", regularUser, "reader")
grant("INVALID_PRINCIPAL", regularUser, "reader")
grant("INVALID_NODENAME", regularUser, "reader")
grant("ROLE_DOESNT_EXIST", regularUser, "reader")
def privilegedUser = user("ace-privileged")
grant("MISSING_SITE_PRIVILEGED_GRP_MEMBER", privilegedUser, "editor")
def externalScenarios = ["MISSING_EXTERNAL_ACE", "ACE_NON_GRANT_WITH_EXTERNAL_ACE", "INVALID_ACE_TYPE_PROP", "NO_SOURCE_ACE_PROP",
                         "EMPTY_SOURCE_ACE_PROP", "SOURCE_ACE_BROKEN_REF", "INVALID_EXTERNAL_PERMISSIONS", "INVALID_EXTERNAL_ACE_PATH",
                         "DUPLICATED_REF_SRC_ACE", "INVALID_ROLES_PROP", "ROLES_DIFFER_ON_SOURCE_ACE"]
def externalUsers = [:]
externalScenarios.eachWithIndex { scenario, idx ->
    externalUsers[scenario] = user("ace-" + idx)
    grant(scenario, externalUsers[scenario], "editor")
}
// The ACL of this node receives the moved external ACE
grant("external-ace-holder", regularUser, "reader")

def groupManager = JahiaGroupManagerService.getInstance()
def sitePrivileged = groupManager.lookupGroup(SITE_KEY, "site-privileged", session)
sitePrivileged.removeMember(userManager.lookupUser(privilegedUser, session))
session.save()

JCRObservationManager.setAllEventListenersDisabled(true)
try {
    JcrSessionFilter.endRequest()
    def real = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null).getNode(FOLDER).getRealNode()
    def realSession = real.getSession()
    def values = realSession.getValueFactory()
    def ace = { String scenario, String username -> real.getNode(scenario + "/j:acl/GRANT_u_" + username) }
    def externalAce = { String scenario ->
        realSession.getNode("/sites/" + SITE_KEY + "/j:acl/REFeditor_currentSite-access_u_" + externalUsers[scenario])
    }

    ace("NO_PRINCIPAL", regularUser).getProperty("j:principal").remove()
    ace("NO_ACE_TYPE_PROP", regularUser).getProperty("j:aceType").remove()
    ace("NO_ROLES_PROP", regularUser).getProperty("j:roles").remove()
    def invalidPrincipal = real.getNode("INVALID_PRINCIPAL/j:acl").addNode("GRANT_u_ci-user-which-does-not-exist", "jnt:ace")
    invalidPrincipal.setProperty("j:principal", "u:ci-user-which-does-not-exist")
    invalidPrincipal.setProperty("j:aceType", "GRANT")
    invalidPrincipal.setProperty("j:roles", ["reader"] as String[])
    invalidPrincipal.setProperty("j:protected", false)
    realSession.move(FOLDER + "/INVALID_NODENAME/j:acl/GRANT_u_" + regularUser, FOLDER + "/INVALID_NODENAME/j:acl/GRANT_u_another-name")
    ace("ROLE_DOESNT_EXIST", regularUser).setProperty("j:roles", ["ci-role-which-does-not-exist"] as String[])

    externalAce("MISSING_EXTERNAL_ACE").remove()
    ace("ACE_NON_GRANT_WITH_EXTERNAL_ACE", externalUsers["ACE_NON_GRANT_WITH_EXTERNAL_ACE"]).setProperty("j:aceType", "DENY")
    externalAce("INVALID_ACE_TYPE_PROP").setProperty("j:aceType", "DENY")
    externalAce("NO_SOURCE_ACE_PROP").getProperty("j:sourceAce").remove()
    externalAce("EMPTY_SOURCE_ACE_PROP").setProperty("j:sourceAce", new Value[0])
    def brokenRef = externalAce("SOURCE_ACE_BROKEN_REF")
    brokenRef.setProperty("j:sourceAce", [brokenRef.getProperty("j:sourceAce").getValues()[0], values.createValue(MISSING_UUID, PropertyType.WEAKREFERENCE)] as Value[])
    externalAce("INVALID_EXTERNAL_PERMISSIONS").setProperty("j:externalPermissionsName", "ci-unknown-permissions")
    def moved = externalAce("INVALID_EXTERNAL_ACE_PATH")
    realSession.move(moved.getPath(), FOLDER + "/external-ace-holder/j:acl/" + moved.getName())
    def duplicated = externalAce("DUPLICATED_REF_SRC_ACE")
    def source = duplicated.getProperty("j:sourceAce").getValues()[0]
    duplicated.setProperty("j:sourceAce", [source, source] as Value[])
    externalAce("INVALID_ROLES_PROP").setProperty("j:roles", ["editor", "reviewer"] as String[])
    ace("ROLES_DIFFER_ON_SOURCE_ACE", externalUsers["ROLES_DIFFER_ON_SOURCE_ACE"]).setProperty("j:roles", ["reviewer"] as String[])
    realSession.save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
