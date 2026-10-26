// Deletes the folder created under the module templates-system by the fixtures of PublicationSanityDefaultCheck, in both
// workspaces
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.osgi.BundleUtils
import org.jahia.services.content.JCRSessionFactory

JcrSessionFilter.endRequest()
["live", "default"].each { workspace ->
    def session = BundleUtils.getOsgiService(JCRSessionFactory.class, null).getCurrentSystemSession(workspace, null, null)
    session.getNode("/modules/templates-system").getNodes().findAll { it.isNodeType("jnt:moduleVersion") }.each { version ->
        if (version.hasNode("contents/ci-unpublished-SITEKEY")) version.getNode("contents/ci-unpublished-SITEKEY").remove()
    }
    session.save()
}
JcrSessionFilter.endRequest()
