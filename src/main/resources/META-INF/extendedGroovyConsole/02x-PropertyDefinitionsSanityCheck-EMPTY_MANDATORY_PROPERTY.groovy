import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.decorator.JCRSiteNode
import org.jahia.utils.LanguageCodeConverters

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            String path = "${MOUNTPOINT}/PropertyDefinitionsSanityCheck-EMPTY_MANDATORY_PROPERTY-${workspace}.txt"
            if (!session.nodeExists(path)) {
                log.info "${path} does not exists"
                return null
            }
            def file = JCRContentUtils.downloadFileContent(session.getNode(path))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    ++count
                    def data = row.split(';')
                    def uuid = data[0]
                    def locale = data[1]
                    def property = data[2]
                    def defaultValue = data[3]
                    try {
                        def node = session.getNodeByIdentifier(uuid)
                        JCRSiteNode site = node.getResolveSite()
                        if (node.path.startsWith("/modules/"))  {
                            log.warn "#${count} [WARN] [IGNORE]: Set property ${property} node ${node.path} with value ${defaultValue}"
                            return null
                        }
                        if (StringUtils.isNotBlank(locale)) {
                            node = node.getI18N(LanguageCodeConverters.languageCodeToLocale(locale))
                        }
                        if (StringUtils.equals(defaultValue, "[site.home]")) {
                            if (site == null) {
                                log.warn "#${count} [WARN] ${StringUtils.repeat(" ", 2)}Impossible to calculate the default value for ${node.path}/${property}"
                                return null
                            }
                            JCRNodeWrapper home = site.getHome()
                            if (home == null) {
                                log.warn "#${count} [WARN] ${StringUtils.repeat(" ", 2)}Impossible to calculate the default value for ${node.path}/${property}"
                                return null
                            }
                            defaultValue = home.identifier
                        }
                        log.info "#${count} Set property ${property} node ${node.path} with value ${defaultValue}"
                        node.setProperty(property, defaultValue)
                        if (SAVE) session.save()
                    } catch (ItemNotFoundException e) {
                        log.warn "#${count} [WARN] uuid not found: ${uuid}"
                        // Nothing to do
                    } catch (RepositoryException e) {
                        log.error "#${count} [ERROR]", e
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

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
