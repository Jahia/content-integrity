import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRContentUtils
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

import javax.jcr.Property
import javax.jcr.RepositoryException

if (Arrays.asList("all", "default").contains(workspace))
    scanSite(site, Constants.EDIT_WORKSPACE, hideFP, populateRK)
if (Arrays.asList("all", "live").contains(workspace))
    scanSite(site, Constants.LIVE_WORKSPACE, hideFP, populateRK)

void scanSite(String siteNodeID, String ws, boolean hideRefsToIgnore, boolean populateRK) {

    log.info("Running on the workspace " + ws)
    log.info(" ------------------------------------")

    JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, ws, null, new JCRCallback<Object>() {

        long count = 0L
        final REFRESH_INTERVAL = 1000L


        @Override
        Object doInJCR(JCRSessionWrapper session) throws RepositoryException {

            JCRNodeWrapper siteNode = session.getNodeByIdentifier(siteNodeID)
            String sitePath = siteNode.getPath()
            if (isLive(ws)) {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<Object>() {
                    @Override
                    Object doInJCR(JCRSessionWrapper defaultSession) throws RepositoryException {
                        scan(siteNode, sitePath, defaultSession)
                        return null
                    }
                })
            } else {
                scan(siteNode, sitePath, session)
            }
            return null
        }

        void scan(JCRNodeWrapper node, String sitePath, JCRSessionWrapper defaultSession) {
            if (++count % REFRESH_INTERVAL == 0) node.getSession().refresh(false)

            for (Property p : node.getWeakReferences()) {
                String path = p.getPath()
                if (!path.startsWith(sitePath)) {
                    final boolean isFP = isFalsePositive(path, node)
                    if (!isFP || !hideRefsToIgnore)
                        log.info(path + " -> " + node.getPath())
                    if (!isFP && populateRK)
                        createRkEntry(p, node, defaultSession)
                }
            }

            for (JCRNodeWrapper child : node.getNodes()) {
                scan(child, sitePath, defaultSession)
            }
        }

        boolean isFalsePositive(String propertyPath, JCRNodeWrapper targetNode) {
            if (StringUtils.equals(propertyPath, "/sites/j:defaultSite")) return true
            if (StringUtils.equals(propertyPath, String.format("/groups/privileged/j:members%s/j:member", targetNode.getPath()))
                    && StringUtils.equals(targetNode.getName(), "site-privileged")) return true

            return false
        }

        void createRkEntry(Property property, JCRNodeWrapper targetNode, JCRSessionWrapper session) {
            final JCRNodeWrapper rk = session.getNode("/referencesKeeper")
            JCRNodeWrapper rkEntry = rk.addNode(JCRContentUtils.findAvailableNodeName(rk, "sitedeletion"), "jnt:reference")
            rkEntry.setProperty("j:node", property.getParent())
            rkEntry.setProperty("j:propertyName", property.getName())
            rkEntry.setProperty("j:originalUuid", targetNode.getPath())
            rkEntry.setProperty("j:live", isLiveSession(targetNode))
            session.save()
            log.info("  > created " + rkEntry.getPath())
        }

        boolean isLiveSession(JCRNodeWrapper node) {
            return isLive(node.getSession().getWorkspace().getName())
        }

        boolean isLive(String wsName) {
            return StringUtils.equals(wsName, Constants.LIVE_WORKSPACE)
        }
    })

    log.info("")
}