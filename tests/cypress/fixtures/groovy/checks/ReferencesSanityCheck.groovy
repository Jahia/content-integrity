// Fixtures of ReferencesSanityCheck, under /sites/SITEKEY/contents/references
// - BROKEN_REF: a reference to a node which does not exist
// - BROKEN_REF_TO_VN: a reference to a virtual node, known in jahia_external_mapping, but which can't be resolved
// - BROKEN_REF_IN_FIELD: a link of a text without a jnt:referenceInField node, and one whose jnt:referenceInField node has
//   no j:reference, as left by the deletion of the linked node; and a placeholder in the text itself, outside of a tag,
//   followed by many unclosed tags, which the fix can't remove
// - BROKEN_REF in a translation whose jcr:language names another language: an i18n reference to a file in English, and
//   to a file which does not exist in French, the French translation node declaring the language en
// - INVALID_BACK_REF: a node referenced by a category, stored in the JCR as an extension of a file of a VFS mount point,
//   whose file is then deleted from the disk: the reference is left, from a node which can't be read anymore
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory
import org.jahia.utils.DatabaseUtils

import javax.jcr.PropertyType

final String MISSING_UUID = "0c1e57ed-0000-4000-8000-000000000000"
final String VIRTUAL_UUID = "0c1e57ed-0000-4000-9000-000000000000"
// A link of a text, as stored once the URLInterceptor has replaced the path of the linked node by a placeholder
final String LINK = "<a href=\"##cms-context##/{mode}/{lang}/##ref:link1##.html\">link</a>"
final List<String> TEXTS = ["link-without-reference-node", "link-without-reference", "valid-link"]

def deleteMapping = { conn ->
    def statement = conn.prepareStatement("delete from jahia_external_mapping where internalUuid=?")
    statement.setString(1, VIRTUAL_UUID)
    statement.executeUpdate()
    statement.close()
}

JcrSessionFilter.endRequest()
def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def contents = session.getNode("/sites/SITEKEY/contents")
if (contents.hasNode("references")) contents.getNode("references").remove()
def folder = contents.addNode("references", "jnt:contentFolder")
["broken-reference", "broken-reference-to-virtual-node", "valid-reference"].each { folder.addNode(it, "jnt:contentReference") }
folder.addNode("back-referenced", "jnt:contentFolder")
folder.getNode("valid-reference").setProperty("j:node", folder.addNode("target", "jnt:text"))
session.save()
def englishSession = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", Locale.ENGLISH, null)
(TEXTS + "placeholder-in-text").each { englishSession.getNode(folder.getPath()).addNode(it, "jnt:bigText").setProperty("text", "text") }
englishSession.save()

def files = session.getNode("/sites/SITEKEY/files")
if (files.hasNode("i18n-target.txt")) files.getNode("i18n-target.txt").remove()
def i18nTarget = files.uploadFile("i18n-target.txt", new ByteArrayInputStream("target".getBytes("UTF-8")), "text/plain")
folder.addNode("i18n-reference", "jnt:fileI18nReference")
session.save()
["en", "fr"].each { lang ->
    def langSession = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", new Locale(lang), null)
    langSession.getNode(folder.getPath() + "/i18n-reference").setProperty("j:node", langSession.getNodeByIdentifier(i18nTarget.getIdentifier()))
    langSession.save()
}

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
    TEXTS.each { realFolder.getNode(it).getNode("j:translation_en").setProperty("text", LINK) }
    realFolder.getNode("placeholder-in-text").getNode("j:translation_en").setProperty("text", "##ref:link1## " + "<a " * 2000)
    def french = realFolder.getNode("i18n-reference").getNode("j:translation_fr")
    french.setProperty("j:node", factory.createValue(MISSING_UUID, PropertyType.WEAKREFERENCE))
    french.setProperty("jcr:language", "en")
    ["link-without-reference", "valid-link"].each {
        def text = realFolder.getNode(it)
        text.addMixin("jmix:referencesInField")
        def reference = text.addNode("j:referenceInField_text_en_1", "jnt:referenceInField")
        reference.setProperty("j:fieldName", "text_en")
        if (it == "valid-link") reference.setProperty("j:reference", factory.createValue(realFolder.getNode("target"), true))
    }
    realFolder.getSession().save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()

// INVALID_BACK_REF: a file of a VFS mount point gets a category which references back-referenced, stored in the JCR as an
// extension of the virtual node, then the file is deleted from the disk
def mountFolder = new File(System.getProperty("java.io.tmpdir"), "SITEKEY-backref")
mountFolder.mkdirs()
new File(mountFolder, "linked.txt").text = "linked"
session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def mounts = session.getNode("/mounts")
if (mounts.hasNode("SITEKEY-backref-mount")) {
    mounts.getNode("SITEKEY-backref-mount").remove()
    session.save()
}
mounts.addNode("SITEKEY-backref-mount", "jnt:vfsMountPoint").setProperty("j:rootPath", mountFolder.toURI().toString())
session.save()
JcrSessionFilter.endRequest()
session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession("default", null, null)
def linked = session.getNode("/mounts/SITEKEY-backref/linked.txt")
linked.addMixin("jmix:categorized")
def backReferenced = session.getNode("/sites/SITEKEY/contents/references/back-referenced")
linked.setProperty("j:defaultCategory", [session.getValueFactory().createValue(backReferenced, true)] as javax.jcr.Value[])
session.save()
JcrSessionFilter.endRequest()
new File(mountFolder, "linked.txt").delete()
