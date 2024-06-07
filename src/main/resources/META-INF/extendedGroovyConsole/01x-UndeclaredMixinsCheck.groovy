import org.apache.commons.io.IOUtils
import org.jahia.api.Constants
import org.jahia.api.content.JCRTemplate
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRObservationManager

import javax.jcr.ItemNotFoundException
import javax.jcr.RepositoryException

for (String workspace in [Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE]) {
    log.info "Traversing workspace ${workspace}"
    JCRObservationManager.setAllEventListenersDisabled(true)
    try {
        BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, workspace, null, { session ->
            def file = JCRContentUtils.downloadFileContent(session.getNode("${MOUNTPOINT}/UndeclaredMixinsCheck-${workspace}.txt"))
            def reader = new FileReader(file)
            def count = 0
            try {
                IOUtils.readLines(reader).each { String row ->
                    def data = row.split(';')
                    def uuid = data[0]
                    def mixin = data[1]
                    try {
                        def node = session.getNodeByIdentifier(uuid)
                        log.info "#${++count} Remove mixin ${mixin} for node ${node.path}"
                        node.removeMixin(mixin)
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

log.info "<<< END UndeclaredMixinsCheck"

// Script configurations
//script.parameters.names=MOUNTPOINT, SAVE
//script.param.MOUNTPOINT.type=text
//script.param.MOUNTPOINT.default=/sites/systemsite/files/content-integrity
//script.param.MOUNTPOINT.label=Input files location
//script.param.SAVE.default=false
//script.param.SAVE.label=Save
