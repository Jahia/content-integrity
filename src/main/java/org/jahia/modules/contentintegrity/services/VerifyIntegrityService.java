package org.jahia.modules.contentintegrity.services;


import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.apache.jackrabbit.spi.commons.value.QValueValue;
import org.jahia.ajax.gwt.helper.ContentDefinitionHelper;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.exceptions.CompositeIntegrityViolationException;
import org.jahia.modules.contentintegrity.exceptions.IntegrityViolationException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.slf4j.Logger;

import javax.jcr.*;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.*;

@Deprecated
public class VerifyIntegrityService {
	private static Logger logger = org.slf4j.LoggerFactory.getLogger(VerifyIntegrityService.class);

	private ContentDefinitionHelper contentDefinition;

	private List<String> propertiesToIgnore = Arrays.asList("jcr:predecessors", "j:nodename", "jcr:versionHistory",
			"jcr:baseVersion", "jcr:isCheckedOut", "jcr:uuid", "jcr:mergeFailed", "j:isHomePage", "j:templateNode",
			"j:tags", "j:newTag", "j:tagList", "jcr:activity", "jcr:configuration", "j:legacyRuleSettings",
			"j:processId", "jcr:description", "j:invalidLanguages", "j:workInProgress", "jcr:lockOwner",
			"jcr:configuration", "j:originWS", "j:published", "j:lastPublishedBy", "j:lastPublished",
			"jcr:lastModified", "jcr:LastModifiedBy", "jcr:lockIsDeep", "j:locktoken", "j:lockTypes", "jcr:created",
			"jcr:createdBy", "j:fullpath", "jcr:mixinTypes", "jcr:primaryType"
	);

	public void setContentDefinition(ContentDefinitionHelper contentDefinition) {
		this.contentDefinition = contentDefinition;
	}

	/**
	 * Add an error to the error collection
	 * @param cive the error collection
	 * @param exception the exception to add
	 * @return the error collection containing the new exception
	 */
	private CompositeIntegrityViolationException addError(CompositeIntegrityViolationException cive,
														  IntegrityViolationException
																  exception) {
		if (cive == null) {
			cive = new CompositeIntegrityViolationException();
		}
		cive.addException(exception);
		return cive;
	}

	/**
	 * Verify the integrity of a node
	 *
	 * @param node    node to verify
	 * @param session session to be used for integrity check
	 * @param cive    compositeIntegrityViolationException collection to which add the error is any is found during the
	 *                integrity check
	 * @return the error collection
	 */
	public CompositeIntegrityViolationException validateNodeIntegrity(JCRNodeWrapper node, JCRSessionWrapper session,
																	  CompositeIntegrityViolationException cive) {
		SortedSet<String> sortedProps = new TreeSet<String>();

		try {
			for (String s : node.getNodeTypes()) {
				Collection<ExtendedPropertyDefinition> propDefs = NodeTypeRegistry.getInstance().getNodeType(s).getPropertyDefinitionsAsMap().values();
				for (ExtendedPropertyDefinition propertyDefinition : propDefs) {
					String propertyName = propertyDefinition.getName();
					Locale errorLocale = null;
					if (propertyDefinition.isInternationalized()) {
						errorLocale = session.getLocale();
					}

					if (propertiesToIgnore.contains(propertyName)) {
						continue;
					}

					// Following condition checks mandatory missing properties
					if (propertyDefinition.isMandatory()) {
						if (propertyDefinition.getRequiredType() != PropertyType.WEAKREFERENCE &&
								propertyDefinition.getRequiredType() != PropertyType.REFERENCE &&
								!propertyDefinition.isProtected() &&
								(!propertyDefinition.isInternationalized() || session.getLocale() != null) &&
								(!node.hasProperty(propertyName) ||
										(!propertyDefinition.isMultiple() &&
												propertyDefinition.getRequiredType() != PropertyType.BINARY &&
												StringUtils.isEmpty(node.getProperty(propertyName).getString()))

								)) {

							cive = addError(cive, new IntegrityViolationException(node.getPath(), node
									.getPrimaryNodeTypeName(), propertyDefinition.getName(), errorLocale,
									"This field is " +
											"mandatory"));
							logger.debug("Mandatory field missing on property");
						} else if(propertyDefinition.getRequiredType() == PropertyType.WEAKREFERENCE ||
								propertyDefinition.getRequiredType() == PropertyType.REFERENCE) {
							try {
								JCRNodeWrapper referencedNode = (JCRNodeWrapper) node.getProperty(propertyName)
										.getNode();
							} catch (PathNotFoundException pnfe) {
								cive = addError(cive, new IntegrityViolationException(node.getPath(), node
										.getPrimaryNodeTypeName(), propertyDefinition.getName(), errorLocale,
										"This field is " +
												"mandatory"));
								logger.debug("Mandatory field missing on reference property");
							} catch (ItemNotFoundException infe) {
								cive = addError(cive, new IntegrityViolationException(node.getPath(), node
										.getPrimaryNodeTypeName(), propertyDefinition.getName(), errorLocale,
										"This reference field is " +
												"mandatory. The property is set but toward a no-more existing node"));
								logger.debug("Mandatory field on reference property is set toward a no-more existing " +
										"node");
							}
						}
					} else {

						Property prop = null;
						try {
							prop = node.getProperty(propertyName);
						} catch (PathNotFoundException ex) {
							logger.debug("Property : " + propertyName + " not found on node " + node.getPath() + " so continuing to other properties without validating");
							continue;
						} catch (RepositoryException ex) {
							logger.error("Error getting Property : " + propertyName + " on node " + node.getPath() + " continuing even though error is : " + ex.getMessage());
							continue;
						}
						boolean wrongValueForTheType = false;

						if ("jcr:title".equals(propertyName) && !hasMixTitle(node.getPrimaryNodeType())) {
							cive = addError(cive, new IntegrityViolationException(node.getPath(), node
									.getPrimaryNodeTypeName(), propertyName, errorLocale,
									"This field has a has jcr:title property but, the primary node type does not have mix:title as one of it's supertypes"));
						}

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
												internalValue}, cive, errorLocale);
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
										cive = validateConstraints(node, propertyDefinition, internalValues, cive,
												errorLocale);
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
									cive = addError(cive, new IntegrityViolationException(node.getPath(), node
											.getPrimaryNodeTypeName(), propertyName, locale,
											"This field has a " +
													"wrong value for its type (i.e. string instead of date)"));
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
			logger.debug("A new node can no longer be accessed to run validation checks", e);
		} catch (PathNotFoundException e) {
			logger.debug("Property or node not found: " + e.getMessage());
		} catch (RepositoryException e) {
			logger.error("RepositoryException", e);

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
							logger.debug(node.getPath() + " Removed property found : " + property.getName());
							ExtendedPropertyDefinition propertyDefinition = node.getApplicablePropertyDefinition(
									property.getName());
							Locale errorLocale = null;
							if (propertyDefinition.isInternationalized()) {
								errorLocale = session.getLocale();
							}
							cive = addError(cive, new IntegrityViolationException(node.getPath(), node
									.getPrimaryNodeTypeName(), propertyDefinition.getName(), errorLocale, ex.getMessage
									()));
						}
					}
				}
			} catch (PathNotFoundException ex) {

			} catch (RepositoryException rex) {
				logger.debug("Cannot export property", rex);
			}
		}

		return cive;
	}

	/**
	 * Validate constraints integrity on a node, such as regex or range constraint
	 *
	 * @param node               node on which perform constraint validation check
	 * @param propertyDefinition property definition of the node
	 * @param values             internvalValue of the node
	 * @param cive               compositeIntegrityViolationException collection to which add the error is any is
	 *                           found during the integrity check
	 * @param errorLocale        locale being used/checked
	 * @return
	 */
	private CompositeIntegrityViolationException validateConstraints(JCRNodeWrapper node, ExtendedPropertyDefinition
			propertyDefinition, InternalValue[] values, CompositeIntegrityViolationException cive, Locale errorLocale) {
		try {
			// check multi-value flag
			if (!propertyDefinition.isMultiple() && values != null && values.length > 1) {
				logger.debug("Property is not multi-valued : " + propertyDefinition.getName() + " | Node : " + node
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
				cive = addError(cive, new IntegrityViolationException(node.getPath(), node.getPrimaryNodeTypeName(),
						propertyDefinition.getName(), errorLocale, e.getMessage()));
			} catch (RepositoryException ex) {
				logger.debug("Repository exception", ex);
			}
		} catch (RepositoryException e) {
			logger.debug("Repository exception", e);
		}

		return cive;
	}

	/**
	 * Verify if the nodeType is inheriting mix:title mixin
	 *
	 * @param nodeType nodetype to verify
	 * @return true if the nodetype is inheriting mix:title, false otherwise
	 */
	private boolean hasMixTitle(ExtendedNodeType nodeType) {
		if ("mix:title".equals(nodeType.getName())) {
			return true;
		}

		ExtendedNodeType[] supertypes = nodeType.getSupertypes();
		for (ExtendedNodeType extendedNodeType : supertypes) {
			if (hasMixTitle(extendedNodeType)) {
				return true;
			}
		}

		return false;
	}
}
