import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

import javax.jcr.RepositoryException

final String path = StringUtils.defaultIfBlank(rootPath, "/")
final String nodeTypes = nodeTypes
if (Arrays.asList("default", "all").contains(workspace)) scan(path, Constants.EDIT_WORKSPACE, nodeTypes)
if (Arrays.asList("live", "all").contains(workspace)) scan(path, Constants.LIVE_WORKSPACE, nodeTypes)

void scan(String root, String ws, String nodeTypeFilter) {
    JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, ws, null, new JCRCallback<Object>() {

        final long REFRESH_INTERVAL = 10000L
        long count = 0L
        final List<String> filter = StringUtils.isBlank(nodeTypeFilter) ? null : Arrays.asList(StringUtils.split(nodeTypeFilter))

        @Override
        Object doInJCR(JCRSessionWrapper session) throws RepositoryException {

            log.info("Running on the workspace " + session.getWorkspace().getName())
            final Map<String, Long> sizes = new HashMap<>()
            JCRNodeWrapper r = session.getNode(root)
            for (JCRNodeWrapper c : r.getNodes()) {
                final String path = c.getPath()
                if ("/jcr:system".equals(path)) {
                    log.info("Skipping /jcr:system")
                    continue
                }
                sizes.put(path, getTreeSize(c, filter))
            }
            ArrayList<Map.Entry<String, Long>> entries = new ArrayList<>(sizes.entrySet())
            entries.sort(Map.Entry.comparingByValue())
            for (Map.Entry e : entries.reverse()) {
                log.info(e.getKey() + " : " + e.getValue())
            }
            log.info("")

            return null
        }

        private long getTreeSize(JCRNodeWrapper n, List<String> nodeTypes) {
            if (++count % REFRESH_INTERVAL == 0) n.getSession().refresh(false)
            long size = matches(n, nodeTypes) ? 1L : 0L
            for (JCRNodeWrapper c : n.getNodes()) {
                size += getTreeSize(c, nodeTypes)
            }
            return size
        }

        private boolean matches(JCRNodeWrapper n, List<String> nodeTypes) {
            if (CollectionUtils.isEmpty(nodeTypes)) return true
            for (String nt : nodeTypes) {
                if (n.isNodeType(nt)) return true
            }
            return false
        }
    })
}
