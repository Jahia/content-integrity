package org.jahia.modules.verifyintegrity.actions;


import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.verifyintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.modules.verifyintegrity.services.VerifyIntegrityService;
import org.jahia.services.content.*;
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
import java.util.*;

/**
 * Jahia action to be called on a site to check integrity of its content
 */
public class VerifyIntegrityOfSiteContent extends Action {

	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityOfSiteContent.class);

	private static final String PARAM_MULTI_LOCALE_CHECK = "performMultiLocaleVerification";

	private VerifyIntegrityService verifyIntegrityService;

	public void setVerifyIntegrityService(VerifyIntegrityService verifyIntegrityService) {
		this.verifyIntegrityService = verifyIntegrityService;
	}

	@Override
	public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
		final JCRSiteNode siteNode = renderContext.getSite();

		LOGGER.debug("VerifyIntegrityOfSiteContent action has been called on site : " + siteNode.getName());

		CompositeIntegrityViolationException cive = new CompositeIntegrityViolationException();

		boolean performMultiLocaleVerification = Boolean.parseBoolean(getParameter(parameters,
				PARAM_MULTI_LOCALE_CHECK, "true"));
		List<Locale> localesToCheck;
		if (performMultiLocaleVerification) {
			localesToCheck = siteNode.getLanguagesAsLocales();
		}
		else {
			localesToCheck = new ArrayList();
			localesToCheck.add(session.getLocale());
		}

		for (Locale locale : localesToCheck) {
			CompositeIntegrityViolationException civeForThisLocale = (((CompositeIntegrityViolationException)
					JCRTemplate.getInstance()
					.doExecuteWithSystemSession
					(null,
					Constants.EDIT_WORKSPACE, locale, new JCRCallback
					() {
				@Override
				public CompositeIntegrityViolationException doInJCR(JCRSessionWrapper sessionTemp) throws
						RepositoryException {
					LOGGER.debug("Locale : " + sessionTemp.getLocale());

					CompositeIntegrityViolationException civeTemp = null;

					try {
						Query query = sessionTemp.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:content] WHERE " +
								"ISDESCENDANTNODE('" + siteNode.getPath() + "')", Query.JCR_SQL2);
						QueryResultWrapper queryResult = (QueryResultWrapper) query.execute();
						for (Node node : queryResult.getNodes()) {
							civeTemp = verifyIntegrityService.validateNodeIntegrity((JCRNodeWrapper) node, sessionTemp,
									civeTemp);
						}
					} catch (RepositoryException e) {
						e.printStackTrace();
					}

					return civeTemp;
				}
			})));

			if ((civeForThisLocale != null) && (civeForThisLocale.getErrors() != null)) {
				cive.addExceptions(civeForThisLocale.getErrors());
			}
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
