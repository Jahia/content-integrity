import org.jahia.api.Constants
import org.jahia.services.SpringContextSingleton

def service = SpringContextSingleton.getBean("aclcleanup.integrityService")
Map<String,Boolean> config = new HashMap<>()
if (scriptactive) config.put("fix-errors", true)
config.put("check-external-ace", true)
config.put("check-useless-external-ace", true)

log.info("Scanning from /")
if (scriptactive)
    log.info("Correction of the errors enabled")
else
    log.info("Only logging the identified errors")
log.info("")
log.info("Testing the workspace DEFAULT")
List<String> editErrors = service.checkIntegrity("/", Constants.EDIT_WORKSPACE, config)
log.info(String.format("  %d errors found", service.countErrors(editErrors)))
for (String line : editErrors) {
    log.info(line)
}
log.info("")
log.info("Testing the workspace LIVE")
List<String> liveErrors = service.checkIntegrity("/", Constants.LIVE_WORKSPACE, config)
log.info(String.format("  %d errors found", service.countErrors(liveErrors)))
for (String line : liveErrors) {
    log.info(line)
}