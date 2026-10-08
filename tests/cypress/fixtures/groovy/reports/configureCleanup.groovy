// Configures the cleanup of the old scan reports: org.jahia.modules.contentintegrity.reports
// A change of this configuration runs a cleanup at once
import org.jahia.osgi.BundleUtils
import org.osgi.service.cm.ConfigurationAdmin

def configuration = BundleUtils.getOsgiService(ConfigurationAdmin.class, null).getConfiguration("org.jahia.modules.contentintegrity.reports", null)
def properties = new Hashtable<String, Object>()
properties.put("retentionDays", "RETENTION_DAYS")
properties.put("cleanupIntervalHours", "CLEANUP_INTERVAL_HOURS")
configuration.update(properties)
