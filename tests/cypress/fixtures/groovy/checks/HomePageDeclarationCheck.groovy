// Fixtures of HomePageDeclarationCheck, on the site SITEKEY, for the scenario SCENARIO
// Each scenario first restores a site whose page named home is the only page flagged as home
// - MULTIPLE_HOMES: a second page flagged as home
// - FALLBACK_ON_NAME: no page flagged as home, but a page named home
// - NO_HOME: no page flagged as home, and no page named home
// - FALLBACK_ON_NAME_WRONG_TYPE: no page flagged as home, and a sub-node named home which is not a page
import org.jahia.bin.filters.jcr.JcrSessionFilter
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

final String SITE = "/sites/SITEKEY"
final String SCENARIO = "SCENARIO"

JcrSessionFilter.endRequest()
JCRObservationManager.setAllEventListenersDisabled(true)
try {
    def site = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null).getNode(SITE).getRealNode()
    def session = site.getSession()
    // Restores the regular declaration
    if (site.hasNode("home") && !site.getNode("home").isNodeType("jnt:page")) site.getNode("home").remove()
    if (site.hasNode("former-home")) session.move(SITE + "/former-home", SITE + "/home")
    if (site.hasNode("second-home")) site.getNode("second-home").remove()
    site.getNode("home").setProperty("j:isHomePage", true)
    session.save()

    switch (SCENARIO) {
        case "MULTIPLE_HOMES":
            def second = site.addNode("second-home", "jnt:page")
            second.setProperty("j:templateName", "home")
            second.setProperty("j:isHomePage", true)
            break
        case "FALLBACK_ON_NAME":
            site.getNode("home").getProperty("j:isHomePage").remove()
            break
        case "NO_HOME":
            site.getNode("home").getProperty("j:isHomePage").remove()
            session.move(SITE + "/home", SITE + "/former-home")
            break
        case "FALLBACK_ON_NAME_WRONG_TYPE":
            site.getNode("home").getProperty("j:isHomePage").remove()
            session.move(SITE + "/home", SITE + "/former-home")
            site.addNode("home", "jnt:contentFolder")
            break
    }
    session.save()
} finally {
    JCRObservationManager.setAllEventListenersDisabled(false)
}
JcrSessionFilter.endRequest()
