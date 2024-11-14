package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_VIRTUALSITE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=jmix:hasTemplateNode," + JAHIANT_VIRTUALSITE
})
public class PagesSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(PagesSanityCheck.class);

    private static final String TEMPLATE_NAME = "j:templateName";
    private static final String SITE_DEFAULT_TEMPLATE_NAME = "j:defaultTemplateName";
    private static final String TEMPLATE_TYPE_HTML = "html";

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

        if (!isTemplateValid(templateName, node)) {
            return createSingleError(createError(node, MISSING_TEMPLATE)
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
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        clearCaches();
    }

    @Override
    protected ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        clearCaches();
        return null;
    }

    private boolean isTemplateValid(String templateName, JCRNodeWrapper node) {
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
            logger.error("", e);
        }
        missingTemplates.add(templateName);
        return false;
    }

    private void clearCaches() {
        templates.clear();
        missingTemplates.clear();
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
