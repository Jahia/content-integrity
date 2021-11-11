import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

import javax.jcr.RepositoryException

final String path = StringUtils.defaultIfBlank(rootPath, "/")
if (Arrays.asList("default", "all").contains(workspace)) scan(path, Constants.EDIT_WORKSPACE)
if (Arrays.asList("live", "all").contains(workspace)) scan(path, Constants.LIVE_WORKSPACE)

void scan(String root, String ws) {
    JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, ws, null, new JCRCallback<Object>() {


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
                sizes.put(path, getTreeSize(c))
            }
            ArrayList<Map.Entry<String, Long>> entries = new ArrayList<>(sizes.entrySet())
            entries.sort(Map.Entry.comparingByValue())
            for (Map.Entry e : entries.reverse()) {
                log.info(e.getKey() + " : " + e.getValue())
            }
            log.info("")

            return null
        }

        private long getTreeSize(JCRNodeWrapper n) {
            long size = 1
            for (JCRNodeWrapper c : n.getNodes()) {
                size += getTreeSize(c)
            }
            return size
        }
    })
}
