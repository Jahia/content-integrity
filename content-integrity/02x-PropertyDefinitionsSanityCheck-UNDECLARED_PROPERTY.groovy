import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.apache.jackrabbit.core.NodeImpl
import org.apache.jackrabbit.core.id.NodeId
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
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/PropertyDefinitionsSanityCheck-UNDECLARED_PROPERTY-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    def data = row.split(';')
                    def uuid = data[0]
                    def locale = data[1]
                    def property = data[2]
                    try {
                        def node = session.getNodeByIdentifier(uuid)
                        if (StringUtils.isNotBlank(locale)) {
                            node = node.getI18N(LanguageCodeConverters.languageCodeToLocale(locale))
                        }
                        if (node.hasProperty(property)) {
                            log.info "#${++count} Remove property ${property} for node ${node.path}"
                            node.getProperty(property).remove()
                            if (SAVE) session.save()
                        } else {
                            log.warn "#${++count} [WARN] Property ${property} not found for node ${node.path}"
                        }
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

log.info "<<< END PropertyDefinitionsSanityCheck-UNDECLARED_PROPERTY"
