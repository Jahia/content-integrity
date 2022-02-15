import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

import javax.jcr.Property
import javax.jcr.RepositoryException

boolean hide = hideFP
if (Arrays.asList("all", "default").contains(workspace))
    scanSite(site, Constants.EDIT_WORKSPACE, hide)
if (Arrays.asList("all", "live").contains(workspace))
    scanSite(site, Constants.LIVE_WORKSPACE, hide)

void scanSite(String siteNodeID, String ws, boolean hideReferencesToIgnore) {

    log.info("Running on the workspace " + ws)
    log.info(" ------------------------------------")

    JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, ws, null, new JCRCallback<Object>() {
        @Override
        Object doInJCR(JCRSessionWrapper session) throws RepositoryException {

            JCRNodeWrapper siteNode = session.getNodeByIdentifier(siteNodeID)
            String sitePath = siteNode.getPath()
            scan(siteNode, sitePath, hideReferencesToIgnore)
            return null
        }

        void scan(JCRNodeWrapper node, String sitePath, boolean hideRefsToIgnore) {
            for (Property p : node.getWeakReferences()) {
                String path = p.getPath()
                if (!path.startsWith(sitePath) && logReference(path, node, hideReferencesToIgnore)) {
                    log.info(path + " -> " + node.getPath())
                }
            }

            for (JCRNodeWrapper child : node.getNodes()) {
                scan(child, sitePath, hideReferencesToIgnore)
            }
        }

        boolean logReference(String propertyPath, JCRNodeWrapper targetNode, boolean hideRefsToIgnore) {
            if (!hideReferencesToIgnore) return true

            if (StringUtils.equals(propertyPath, "/sites/j:defaultSite")) return false
            if (StringUtils.equals(propertyPath, String.format("/groups/privileged/j:members%s/j:member", targetNode.getPath()))
                    && StringUtils.equals(targetNode.getName(), "site-privileged")) return false

            return true
        }
    })

    log.info("")
}