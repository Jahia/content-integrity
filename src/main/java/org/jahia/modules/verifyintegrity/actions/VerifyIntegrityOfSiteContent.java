package org.jahia.modules.verifyintegrity.actions;


import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.verifyintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.modules.verifyintegrity.services.VerifyIntegrityService;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jahia action to be called on a site to check integrity of its content
 */
public class VerifyIntegrityOfSiteContent extends Action {

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityOfSiteContent.class);

	private VerifyIntegrityService verifyIntegrityService;

	private JahiaUserManagerService jahiaUserManagerService;

	public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
		this.jahiaUserManagerService = jahiaUserManagerService;
	}

	public void setVerifyIntegrityService(VerifyIntegrityService verifyIntegrityService) {
		this.verifyIntegrityService = verifyIntegrityService;
	}

	@Override
	public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
		final JCRSiteNode siteNode = renderContext.getSite();
		logger.debug("VerifyIntegrityOfSiteContent action has been called on site : " + siteNode.getName());

		final String workspaceToUse = session.getWorkspace().getName();
		logger.debug("Workspace to use : " + workspaceToUse);

		String allLocalesParameter = getParameter(parameters, "allLocales");
		Locale localeToUse = null;
		if (allLocalesParameter != null && "true".equals(allLocalesParameter)) {
			logger.debug("Locale to use : all");
		} else {
			localeToUse = session.getLocale();
			logger.debug("Locale to use : " + localeToUse.getDisplayName());
		}


		JCRUserNode rootUser = jahiaUserManagerService.lookupUser("root");

		CompositeIntegrityViolationException cive = (CompositeIntegrityViolationException) JCRTemplate.getInstance()
				.doExecuteWithSystemSessionAsUser(rootUser.getJahiaUser(), workspaceToUse, localeToUse, new
						JCRCallback() {
							@Override
							public CompositeIntegrityViolationException doInJCR(JCRSessionWrapper session) throws RepositoryException {
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

								try {
									Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:page] " +
											"WHERE " +
											"ISDESCENDANTNODE('" + siteNode.getPath() + "')", Query.JCR_SQL2);
									QueryResultWrapper queryResult = (QueryResultWrapper) query.execute();
									for (Node node : queryResult.getNodes()) {
										cive = verifyIntegrityService.validateNodeIntegrity((JCRNodeWrapper) node, session, cive);
									}
								} catch (RepositoryException e) {
									e.printStackTrace();
								}

								return cive;
							}
						});


		JSONObject resultAsJSON = new JSONObject();

		if ((cive != null) && (cive.getErrors() != null)) {
			logger.info("Content of the site '" + siteNode.getName() + "' is wrong.");
			logger.info("Number of incorrect nodes : " + cive.getErrors().size());
			logger.info(cive.getMessage());
			resultAsJSON.put("siteContentIsValid", "false");
			resultAsJSON.put("numberOfErrors", Integer.toString(cive.getErrors().size()));

			JSONArray jsonArray = new JSONArray(cive.getMessage());
			resultAsJSON.put("errors", jsonArray);

		} else {
			logger.info("No integrity error found for the site : " + siteNode.getName());
			resultAsJSON.put("siteContentIsValid", "true");
			resultAsJSON.put("numberOfErrors", "0");
		}

		logger.debug("resultAsJSON={}", resultAsJSON);

		return new ActionResult(200, null, resultAsJSON);
	}
}