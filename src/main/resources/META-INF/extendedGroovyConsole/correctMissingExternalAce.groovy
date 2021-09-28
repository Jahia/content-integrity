import org.jahia.api.Constants
import org.jahia.services.SpringContextSingleton

def service = SpringContextSingleton.getBean("aclcleanup.integrityService")
Map<String,Boolean> config = new HashMap<>()
if (scriptactive) config.put("fix-errors", true)
config.put("check-regular-ace", true)
config.put("check-missing-external-ace", true)

def node = service.getRootNode(rootNode, Constants.EDIT_WORKSPACE)
if (node == null) {
    log.info("The specified root node doesn't exist: " + rootNode)
} else {
    log.info("Scanning from " + node.getPath())
    if (scriptactive)
        log.info("Correction of the errors enabled")
    else
        log.info("Only logging the identified errors")
    log.info("")
    log.info("Testing the workspace DEFAULT")
    List<String> editErrors = service.checkIntegrity(rootNode, Constants.EDIT_WORKSPACE, config)
    log.info(String.format("  %d errors found", service.countErrors(editErrors)))
    for (String line : (editErrors)) {
        log.info(line)
    }
    log.info("")
    log.info("Testing the workspace LIVE")
    List<String> liveErrors = service.checkIntegrity(rootNode, Constants.LIVE_WORKSPACE, config)
    log.info(String.format("  %d errors found", service.countErrors(liveErrors)))
    for (String line : (liveErrors)) {
        log.info(line)
    }
}