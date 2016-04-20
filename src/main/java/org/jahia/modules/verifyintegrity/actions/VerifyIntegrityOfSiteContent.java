package org.jahia.modules.verifyintegrity.actions;


import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.verifyintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.modules.verifyintegrity.services.VerifyIntegrityService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jahia action to be called on a site to check integrity of its content
 */
public class VerifyIntegrityOfSiteContent extends Action {

	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityOfSiteContent.class);

	private VerifyIntegrityService verifyIntegrityService;

	public void setVerifyIntegrityService(VerifyIntegrityService verifyIntegrityService) {
		this.verifyIntegrityService = verifyIntegrityService;
	}

	@Override
	public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
		final JCRSiteNode siteNode = renderContext.getSite();


		LOGGER.debug("VerifyIntegrityOfSiteContent action has been called on site : " + siteNode.getName());

		CompositeIntegrityViolationException cive = null;

		try {
			Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:content] WHERE " +
					"ISDESCENDANTNODE('" + siteNode.getPath() + "')", Query.JCR_SQL2);
			QueryResultWrapper queryResult = (QueryResultWrapper) query.execute();
			for (Node node : queryResult.getNodes()) {
				cive = verifyIntegrityService.validateNodeIntegrity((JCRNodeWrapper) node, session, cive);
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}

		Map<String, String> resultAsMap = new HashMap();

		if ((cive != null) && (cive.getErrors() != null)) {
			LOGGER.info("Content of the site '" + siteNode.getName() + "' is wrong.");
			LOGGER.info("Number of incorrect nodes : " + cive.getErrors().size());
			LOGGER.info(cive.getMessage());
			resultAsMap.put("siteContentIsValid", "false");
			resultAsMap.put("numberOfErrors", Integer.toString(cive.getErrors().size()));
			resultAsMap.put("errors", cive.getMessage());
		} else {
			LOGGER.info("No integrity error found for the site : " + siteNode.getName());
			resultAsMap.put("siteContentIsValid", "true");
			resultAsMap.put("numberOfErrors", "0");
		}

		JSONObject resultAsJSON = new JSONObject(resultAsMap);
		LOGGER.debug("resultAsJSON={}", resultAsJSON);

		return new ActionResult(200, null, resultAsJSON);
	}
}
