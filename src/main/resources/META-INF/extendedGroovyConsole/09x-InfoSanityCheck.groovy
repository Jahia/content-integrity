import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRObservationManager

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException
import java.util.function.Function

def fix = { String property, Function<JCRNodeWrapper, String> getProperty ->
    for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
        log.info "Traversing workspace ${workspace}"
        JCRObservationManager.setAllEventListenersDisabled(true)
        try {
            BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
                def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/InfoSanityCheck-${workspace}.txt"))
                def reader = new FileReader(file)
                def count = 0
                try {
                    IOUtils.readLines(reader).each { String uuid ->
                        try {
                            def node = session.getNodeByIdentifier(uuid)
                            log.info "#${++count} Set property ${property} on node ${node.path}"
                            node.setProperty(property, getProperty.apply(node))
                            if (SAVE) session.save()
                        } catch (ItemNotFoundException e) {
                            log.warn "#${++count} uuid not found: ${uuid}"
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
}

fix(Constants.NODENAME, { node -> node.name })
fix(Constants.FULLPATH, { node -> node.path })

log.info "<<< END InfoSanityCheck"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
