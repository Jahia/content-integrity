import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager
import org.jahia.utils.LanguageCodeConverters

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

def MOUNTPOINT = '/sites/systemsite/files/content-integrity'
def SAVE = false

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/PropertyDefinitionsSanityCheck-EMPTY_MANDATORY_PROPERTY-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    def data = row.split(';')
                    def uuid = data[0]
                    def locale = data[1]
                    def property = data[2]
                    def defaultValue = data[3]
                    try {
                        def node = session.getNodeByIdentifier(uuid)
                        if (locale != null) {
                            node = node.getI18N(LanguageCodeConverters.languageCodeToLocale(locale))
                        }
                        log.info "#${++count} Set property ${property} node ${node.path} with value ${defaultValue}"
                        node.setProperty(property, defaultValue)
                        if (SAVE) session.save()
                    } catch (ItemNotFoundException e) {
                        // Nothing to do
                    } catch (RepositoryException e) {
                        log.error("", e)
                    }
                }
            } finally {
                IOUtils.closeQuietly(reader)
            }
        })
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
}

log.info "<<< END PropertyDefinitionsSanityCheck-EMPTY_MANDATORY_PROPERTY"
