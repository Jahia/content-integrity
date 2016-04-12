package org.jahia.modules.verifyintegrity.actions;


import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.apache.jackrabbit.spi.commons.value.QValueValue;
import org.jahia.ajax.gwt.helper.ContentDefinitionHelper;
import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.verifyintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.slf4j.Logger;

import javax.jcr.*;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.query.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Jahia action to be called on a site to check integrity of its content
 */
public class VerifyIntegrityOfSiteContent extends Action {

	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityOfSiteContent.class);

	private JCRSessionFactory jcrSessionFactory;

	private ContentDefinitionHelper contentDefinition;

	private List<String> propertiesToIgnore = Arrays.asList("jcr:predecessors", "j:nodename", "jcr:versionHistory",
			"jcr:baseVersion", "jcr:isCheckedOut", "jcr:uuid", "jcr:mergeFailed");

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
				cive = validate((JCRNodeWrapper) node, session, cive);
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

	private CompositeIntegrityViolationException validate(JCRNodeWrapper node, JCRSessionWrapper session,
														  CompositeIntegrityViolationException cive) {
		SortedSet<String> sortedProps = new TreeSet<String>();

		try {
			for (String s : node.getNodeTypes()) {
				Collection<ExtendedPropertyDefinition> propDefs = NodeTypeRegistry.getInstance().getNodeType(s).getPropertyDefinitionsAsMap().values();
				for (ExtendedPropertyDefinition propertyDefinition : propDefs) {
					String propertyName = propertyDefinition.getName();

					if (propertiesToIgnore.contains(propertyName)) {
						continue;
					}

					// Following condition checks mandatory missing properties
					if (propertyDefinition.isMandatory() &&
							propertyDefinition.getRequiredType() != PropertyType.WEAKREFERENCE &&
							propertyDefinition.getRequiredType() != PropertyType.REFERENCE &&
							!propertyDefinition.isProtected() &&
							(!propertyDefinition.isInternationalized() || session.getLocale() != null) &&
							(!node.hasProperty(propertyName) ||
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
					} else {
						Property prop = node.getProperty(propertyName);
						boolean wrongValueForTheType = false;


						// Following checks for constraint not fulfiled
						if (propertyDefinition.getValueConstraints().length != 0) {
							if (node.hasProperty(propertyName)) {
								if (!propertyDefinition.isMultiple()) {
									Value value = node.getProperty(propertyName).getValue();
									InternalValue internalValue = null;
									if (value.getType() != PropertyType.BINARY && !((value.getType() == PropertyType.PATH || value.getType() == PropertyType.NAME) && !(value instanceof QValueValue))) {
										internalValue = InternalValue.create(value, null, null);
									}
									if (internalValue != null) {
										cive = validateConstraints(node, propertyDefinition, new InternalValue[]{
												internalValue}, cive);
									}
								} else {
									Value[] values = node.getProperty(propertyName).getValues();
									List<InternalValue> list = new ArrayList<InternalValue>();
									for (Value value : values) {
										if (value != null) {
											// perform type conversion as necessary and create InternalValue
											// from (converted) Value
											InternalValue internalValue = null;
											if (value.getType() != PropertyType.BINARY
													&& !((value.getType() == PropertyType.PATH || value.getType() == PropertyType.NAME) && !(value instanceof QValueValue))) {
												internalValue = InternalValue.create(value, null, null);
											}
											list.add(internalValue);
										}
									}
									if (!list.isEmpty()) {
										InternalValue[] internalValues = list.toArray(new InternalValue[list.size()]);
										cive = validateConstraints(node, propertyDefinition, internalValues, cive);
									}
								}


							}
						}


						// Following condition checks for values incoherent with its type (i.e. string instead of date)
						if (propertyDefinition != null && propertyDefinition.getSelectorOptions().get("password") == null) {

							// check that we're not dealing with a not-set property from the translation nodes,
							// in which case it needs to be omitted
							final Locale locale = session.getLocale();
							if (Constants.nonI18nPropertiesCopiedToTranslationNodes.contains(propertyName) && node.hasI18N(locale,
									false)) {
								// get the translation node for the current locale
								final Node i18N = node.getI18N(locale, false);
								if (!i18N.hasProperty(propertyName)) {
									// if the translation node doesn't have the property and it's part of the set of copied
									// properties, then we shouldn't test it
									continue;
								}
							}

							Value[] values;
							if (!propertyDefinition.isMultiple()) {
								Value oneValue = prop.getValue();
								values = new Value[]{oneValue};
							} else {
								values = prop.getValues();
							}

							for (Value val : values) {
								try {
									contentDefinition.convertValue(val, (ExtendedPropertyDefinition) propertyDefinition);
								} catch (RepositoryException rex) {
									cive = addError(cive, new PropertyConstraintViolationException(node, "This field has a " +
											"wrong value for its type (i.e. 'string' instead of 'date')",
											null,
											propertyDefinition));
									wrongValueForTheType = true;
								}

							}
						}


						if (!wrongValueForTheType && node.getProvider().canExportProperty(prop)) {
							sortedProps.add(propertyDefinition.getName());
						}
					}
				}
			}
		} catch (InvalidItemStateException e) {
			LOGGER.debug("A new node can no longer be accessed to run validation checks", e);
		} catch (PathNotFoundException e) {

		} catch (RepositoryException e) {
			LOGGER.error("RepositoryException", e);
		}

		/**
		 * Check for removed properties
		 */
		if (!(sortedProps.size() == 0)) {
			try {
				for (String sortedProp : sortedProps) {
					Property property = node.getRealNode().getProperty(sortedProp);
					if ((property.getType() != PropertyType.BINARY) && !propertiesToIgnore.contains(property.getName())
							&& !property.isMultiple()) {

						try {
							property.getDefinition();
						} catch (ConstraintViolationException ex) {
							LOGGER.debug(node.getPath() + " Removed property found : " + property.getName());
							ExtendedPropertyDefinition propertyDefinition = node.getApplicablePropertyDefinition(
									property.getName());
							cive = addError(cive, new PropertyConstraintViolationException(node, ex.getMessage(),
									null, propertyDefinition));
						}
					}
				}
			} catch (PathNotFoundException ex) {

			} catch (RepositoryException rex) {
				LOGGER.debug("Cannot export property", rex);
			}
		}


		return cive;
	}

	private CompositeIntegrityViolationException validateConstraints(JCRNodeWrapper node, ExtendedPropertyDefinition
			propertyDefinition, InternalValue[] values, CompositeIntegrityViolationException cive) {
		try {
			// check multi-value flag
			if (!propertyDefinition.isMultiple() && values != null && values.length > 1) {
				LOGGER.debug("Property is not multi-valued : " + propertyDefinition.getName() + " | Node : " + node
						.getPath());
				throw new ConstraintViolationException("the property is not multi-valued");
			}

			ValueConstraint[] constraints = propertyDefinition.getValueConstraintObjects();

			if (values != null && values.length > 0) {
				// check value constraints on every value
				for (InternalValue value : values) {
					// constraints are OR-ed together
					boolean satisfied = false;
					ConstraintViolationException cve = null;
					for (ValueConstraint constraint : constraints) {
						try {
							constraint.check(value);
							satisfied = true;
							break;
						} catch (ConstraintViolationException e) {
							cve = e;
						}
					}
					if (!satisfied) {
						// re-throw last exception we encountered
						throw cve;
					}
				}
			}
		} catch (ConstraintViolationException e) {
			try {
				cive = addError(cive, new PropertyConstraintViolationException(node, e.getMessage(),
						null, propertyDefinition));
			} catch (RepositoryException ex) {
				LOGGER.debug("Repository exception", ex);
			}
		} catch (RepositoryException e) {
			LOGGER.debug("Repository exception", e);
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
