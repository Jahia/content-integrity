package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.api.FixValuesDefinition;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializerService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_VIRTUALSITE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=jmix:hasTemplateNode," + JAHIANT_VIRTUALSITE
})
public class PagesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues {

    private static final Logger logger = LoggerFactory.getLogger(PagesSanityCheck.class);

    private static final String TEMPLATE_NAME = "j:templateName";
    private static final String SITE_DEFAULT_TEMPLATE_NAME = "j:defaultTemplateName";
    private static final String TEMPLATE_TYPE_HTML = "html";
    // The choice list of j:templateName, which the editors use to choose the template of a page
    private static final String TEMPLATES_INITIALIZER = "templatesNode";
    private static final String PAGE_TEMPLATE = "pageTemplate";

    public static final ContentIntegrityErrorType MISSING_TEMPLATE = createErrorType("MISSING_TEMPLATE", "Missing template", true);

    private final Set<String> templates = new HashSet<>();
    private final Set<String> missingTemplates = new HashSet<>();

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final String templateName;
        if (JCRUtils.runJcrCallBack(JAHIANT_VIRTUALSITE, node::isNodeType)) {
            clearCaches();
            templateName = getTemplateName(node, SITE_DEFAULT_TEMPLATE_NAME);
            if (StringUtils.isBlank(templateName))
                return null;
        } else {
            templateName = getTemplateName(node, TEMPLATE_NAME);
        }

        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        if (!isTemplateValid(templateName, node, errors)) {
            return errors.addError(createError(node, MISSING_TEMPLATE)
                    .addExtraInfo("template-name", templateName));
        }

        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node) {
        if (JCRUtils.runJcrCallBack(JAHIANT_VIRTUALSITE, node::isNodeType)) {
            clearCaches();
        }
        return null;
    }

    @Override
    protected void reset() {
        clearCaches();
    }

    private boolean isTemplateValid(String templateName, JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        if (templates.contains(templateName)) return true;
        if (missingTemplates.contains(templateName)) return false;

        final Resource resource = new Resource(node, TEMPLATE_TYPE_HTML, null, null);
        final RenderContext renderContext = new RenderContext(null, null, null);
        try {
            if (RenderService.getInstance().resolveTemplate(resource, renderContext) != null) {
                templates.add(templateName);
                return true;
            }
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, e));
        }
        missingTemplates.add(templateName);
        return false;
    }

    private void clearCaches() {
        templates.clear();
        missingTemplates.clear();
    }

    /*
     * The right template can't be guessed: an administrator chooses it among the templates available to the page.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        return false;
    }

    @Override
    public boolean isFixWithValues(ContentIntegrityError error) {
        return MISSING_TEMPLATE.equals(error.getErrorType());
    }

    @Override
    public FixValuesDefinition getFixValuesDefinition(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!isFixWithValues(error)) return null;
        final String propertyName = getTemplateProperty(node);
        final Map<String, String> templates = getAvailableTemplates(node, propertyName);
        final List<String> names = new ArrayList<>(templates.keySet());
        final List<String> labels = names.stream()
                .map(name -> StringUtils.equals(templates.get(name), name) ? name : String.format("%s (%s)", templates.get(name), name))
                .collect(Collectors.toList());
        final String description = String.format("The template %s does not exist. Choose one of the templates available to %s, in the template set " +
                "of the site and in its modules.", error.getExtraInfo("template-name"), JCRUtils.runJcrCallBack(JAHIANT_VIRTUALSITE, node::isNodeType) ? "the site" : "the page");
        return new FixValuesDefinition(propertyName, PropertyType.nameFromValue(PropertyType.STRING), false, names, Collections.emptyList(),
                Collections.emptyList(), labels, description);
    }

    /*
     * The template has to be one of the templates available to the page, as listed to the editors.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error, List<String> values) throws RepositoryException {
        if (!isFixWithValues(error)) return false;
        final List<String> typedValues = values == null ? Collections.emptyList() : values.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        if (typedValues.size() != 1) throw new ValueFormatException("Choose one template");
        final String template = typedValues.get(0);
        final String propertyName = getTemplateProperty(node);
        if (!getAvailableTemplates(node, propertyName).containsKey(template)) {
            throw new ConstraintViolationException(String.format("The template %s is not available to %s", template, node.getPath()));
        }

        try {
            node.setProperty(propertyName, template);
            node.saveSession();
        } catch (RepositoryException e) {
            // The system session is shared: a rejected value must not stay pending in it
            node.getSession().refresh(false);
            throw e;
        }
        return true;
    }

    private String getTemplateProperty(JCRNodeWrapper node) {
        return JCRUtils.runJcrCallBack(JAHIANT_VIRTUALSITE, node::isNodeType) ? SITE_DEFAULT_TEMPLATE_NAME : TEMPLATE_NAME;
    }

    /**
     * @return the names of the templates available to the node, with their titles, in the order of the choice list of the editors:
     * the page templates of the template set of its site and of its modules, which apply on its type, are not hidden, and are
     * allowed on its site
     */
    private Map<String, String> getAvailableTemplates(JCRNodeWrapper node, String propertyName) throws RepositoryException {
        final ChoiceListInitializer initializer = ChoiceListInitializerService.getInstance().getInitializers().get(TEMPLATES_INITIALIZER);
        if (initializer == null) {
            logger.warn("The choice list initializer {} is not available: no template can be proposed", TEMPLATES_INITIALIZER);
            return Collections.emptyMap();
        }
        final ExtendedPropertyDefinition definition = node.getApplicablePropertyDefinition(propertyName);
        final Map<String, Object> context = new HashMap<>();
        context.put("contextNode", node);
        final String param = SITE_DEFAULT_TEMPLATE_NAME.equals(propertyName) ? PAGE_TEMPLATE : null;
        final Map<String, String> templates = new LinkedHashMap<>();
        for (ChoiceListValue value : initializer.getChoiceListValues(definition, param, new ArrayList<>(), Locale.ENGLISH, context)) {
            if (value.getValue() == null) continue;
            final String name = value.getValue().getString();
            if (StringUtils.isNotBlank(name)) templates.put(name, StringUtils.defaultIfBlank(value.getDisplayName(), name));
        }
        return templates;
    }

    private String getTemplateName(JCRNodeWrapper node, String propertyName) {
        if (!JCRUtils.runJcrCallBack(TEMPLATE_NAME, node::hasProperty)) {
            // For the default template of a site, the property is not mandatory
            // For a page, the property is mandatory, and the error will be reported by PropertyDefinitionsSanityCheck
            return null;
        }
        return JCRUtils.runJcrSupplierCallBack(() -> node.getProperty(propertyName).getString());
    }
}
