package org.jahia.modules.verifyintegrity.actions;


import com.sun.star.rdf.Repository;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodeProperty;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodePropertyValue;
import org.jahia.ajax.gwt.helper.ContentDefinitionHelper;
import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.verifyintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRFileNode;
import org.jahia.services.content.decorator.JCRReferenceNode;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.validation.JCRNodeValidator;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.slf4j.Logger;

import javax.jcr.*;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.Query;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by jroussel on 31/03/16.
 */
public class VerifyIntegrityOfSiteContent extends Action {

	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityOfSiteContent.class);

	private JCRSessionFactory jcrSessionFactory;

	private ContentDefinitionHelper contentDefinition;

	public void setJcrSessionFactory(JCRSessionFactory jcrSessionFactory) {
		this.jcrSessionFactory = jcrSessionFactory;
	}

	public void setContentDefinition(ContentDefinitionHelper contentDefinition) {
		this.contentDefinition = contentDefinition;
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
				cive = validateNode((JCRNodeWrapper) node, session, cive);
				cive = validateConstraints((JCRNodeWrapper) node, session, cive);
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}

		if ((cive != null) && (cive.getErrors() != null)) {
			LOGGER.info("Content of the site '" + siteNode.getName() + "' is wrong :");
			LOGGER.error(cive.getMessage());
		} else {
			LOGGER.info("No integrity error found for the site : " + siteNode.getName());
		}

		return null;
	}

	// Verify mandatory field missing
	// TODO improve for checking constraint (regex & range)
	private CompositeIntegrityViolationException validateNode(JCRNodeWrapper node, JCRSessionWrapper session,
															  CompositeIntegrityViolationException cive) throws RepositoryException {
		try {
			for (String s : node.getNodeTypes()) {
				Collection<ExtendedPropertyDefinition> propDefs = NodeTypeRegistry.getInstance().getNodeType(s).getPropertyDefinitionsAsMap().values();
				for (ExtendedPropertyDefinition propertyDefinition : propDefs) {
					String propertyName = propertyDefinition.getName();
					if (propertyDefinition.isMandatory() &&
							propertyDefinition.getRequiredType() != PropertyType.WEAKREFERENCE &&
							propertyDefinition.getRequiredType() != PropertyType.REFERENCE &&
							!propertyDefinition.isProtected() &&
							(!propertyDefinition.isInternationalized() || session.getLocale() != null) &&
							(
									!node.hasProperty(propertyName) ||
											(!propertyDefinition.isMultiple() &&
													propertyDefinition.getRequiredType() != PropertyType.BINARY &&
													StringUtils.isEmpty(node.getProperty(propertyName).getString()))

							)) {

						Locale errorLocale = null;
						if (propertyDefinition.isInternationalized()) {
							errorLocale = session.getLocale();
						}
						cive = addError(cive, new PropertyConstraintViolationException(node, "This field is mandatory", errorLocale,
								propertyDefinition));
						LOGGER.debug("Mandatory field");
					}
				}
			}
		} catch (InvalidItemStateException e) {
			LOGGER.debug("A new node can no longer be accessed to run validation checks", e);
		}

		Map<String, Constructor<?>> validators = jcrSessionFactory.getDefaultProvider().getValidators();
		Set<ConstraintViolation<JCRNodeValidator>> constraintViolations = new LinkedHashSet<ConstraintViolation<JCRNodeValidator>>();
		for (Map.Entry<String, Constructor<?>> validatorEntry : validators.entrySet()) {
			if (node.isNodeType(validatorEntry.getKey())) {
				try {
					JCRNodeValidator validatorDecoratedNode = (JCRNodeValidator) validatorEntry.getValue().newInstance(node);
					Set<ConstraintViolation<JCRNodeValidator>> validate = jcrSessionFactory.getValidatorFactoryBean().validate(
							validatorDecoratedNode);
					constraintViolations.addAll(validate);
				} catch (InstantiationException e) {
					LOGGER.error(e.getMessage(), e);
				} catch (IllegalAccessException e) {
					LOGGER.error(e.getMessage(), e);
				} catch (InvocationTargetException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}
		}
		for (ConstraintViolation<JCRNodeValidator> constraintViolation : constraintViolations) {
			String propertyName;
			try {
				Method propertyNameGetter = constraintViolation.getConstraintDescriptor().getAnnotation().annotationType().getMethod(
						"propertyName");
				propertyName = (String) propertyNameGetter.invoke(
						constraintViolation.getConstraintDescriptor().getAnnotation());
			} catch (Exception e) {
				propertyName = constraintViolation.getPropertyPath().toString();
			}
			if (StringUtils.isNotBlank(propertyName)) {
				ExtendedPropertyDefinition propertyDefinition = node.getApplicablePropertyDefinition(
						propertyName);
				if (propertyDefinition == null) {
					propertyDefinition = node.getApplicablePropertyDefinition(propertyName.replaceFirst("_", ":"));
				}
				if (propertyDefinition != null) {
					Locale errorLocale = null;
					if (propertyDefinition.isInternationalized()) {
						errorLocale = session.getLocale();
						if (errorLocale == null) {
							continue;
						}
					}
					cive = addError(cive, new PropertyConstraintViolationException(node, constraintViolation.getMessage(), errorLocale, propertyDefinition));
					LOGGER.debug(constraintViolation.getMessage());
				} else {
					cive = addError(cive, new NodeConstraintViolationException(node, constraintViolation.getMessage(), null));
					LOGGER.debug(constraintViolation.getMessage());
				}
			} else {
				cive = addError(cive, new NodeConstraintViolationException(node, constraintViolation.getMessage(), null));
				LOGGER.debug(constraintViolation.getMessage());
			}
		}
		return cive;
	}

	// Detect property type modified (i.e. property was previously a string, is now a date, but the value is
	// incorrect for a date field)
	private CompositeIntegrityViolationException validateConstraints(JCRNodeWrapper node, JCRSessionWrapper session, CompositeIntegrityViolationException cive) {
		String propName = "null";

		try {
			PropertyIterator itProp = node.getProperties();

			while (itProp.hasNext()) {
				Property prop = itProp.nextProperty();
				PropertyDefinition def = prop.getDefinition();

				if (def != null && ((ExtendedPropertyDefinition) def).getSelectorOptions().get("password") == null) {
					propName = def.getName();

					// check that we're not dealing with a not-set property from the translation nodes,
					// in which case it needs to be omitted
					// TODO improve the following, have to test it on every locale
					final Locale locale = session.getLocale();
					if(Constants.nonI18nPropertiesCopiedToTranslationNodes.contains(propName) && node.hasI18N(locale,
							false)) {
						// get the translation node for the current locale
						final Node i18N = node.getI18N(locale, false);
						if(!i18N.hasProperty(propName)) {
							// if the translation node doesn't have the property and it's part of the set of copied
							// properties, then we shouldn't test it
							continue;
						}
					}



					Value[] values;
					if (!def.isMultiple()) {
						Value oneValue = prop.getValue();
						values = new Value[]{oneValue};
					} else {
						values = prop.getValues();
					}

					for (Value val : values) {
						try {
							contentDefinition.convertValue(val, (ExtendedPropertyDefinition) def);
						} catch (RepositoryException rex) {
							ExtendedPropertyDefinition propertyDefinition = node.getApplicablePropertyDefinition(
									propName);
							cive = addError(cive, new PropertyConstraintViolationException(node, "This field has a " +
									"wrong value for its type (i.e. 'string' instead of 'date')",
									null,
									propertyDefinition));
						}

					}
				}
			}
		} catch (RepositoryException rex) {
			//LOGGER.error("Cannot access property " + propName + " of node " + node.getName(), rex);
			cive = addError(cive, new NodeConstraintViolationException(node, rex.getMessage(), null));
		}

		return cive;
	}

	private CompositeIntegrityViolationException addError(CompositeIntegrityViolationException cive, Exception
			exception) {
		if (cive == null) {
			cive = new CompositeIntegrityViolationException();
		}
		cive.addException(exception);
		return cive;
	}
}
