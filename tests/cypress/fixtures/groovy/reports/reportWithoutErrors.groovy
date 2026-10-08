// A report of the scan RESULTS_ID, finished on 2020-01-15, whose stored errors are missing, as a report written before
// the errors were stored in the JCR
import org.apache.jackrabbit.util.ISO8601
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def folder = session.getNode("/sites/systemsite/files")
["content-integrity-reports", "2020", "01"].each { name ->
    folder = folder.hasNode(name) ? folder.getNode(name) : folder.addNode(name, "jnt:folder")
}
if (folder.hasNode("RESULTS_ID-full")) folder.getNode("RESULTS_ID-full").remove()
def report = folder.addNode("RESULTS_ID-full", "jnt:folder")
report.addMixin("integrity:scanReport")
report.setProperty("integrity:resultsId", "RESULTS_ID")
report.setProperty("integrity:status", "finished")
report.setProperty("integrity:scannedWorkspace", "default")
report.setProperty("integrity:executionDate", ISO8601.parse("2020-01-15T10:00:00.000Z"))
report.setProperty("integrity:errorsCount", 3L)
session.save()
JcrSessionFilter.endRequest()
