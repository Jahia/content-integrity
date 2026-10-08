// Dates the report REPORT_PATH back to DATE (ISO-8601), as if its scan had run then
import org.apache.jackrabbit.util.ISO8601
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
session.getNode("REPORT_PATH").setProperty("integrity:executionDate", ISO8601.parse("DATE"))
session.save()
JcrSessionFilter.endRequest()
