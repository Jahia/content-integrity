package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections4.CollectionUtils;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.osgi.BundleUtils;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_VIRTUALSITE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/sites"
})
public class UndeployedModulesReferencesCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(UndeployedModulesReferencesCheck.class);

    private final Collection<String> availableModules = new ArrayList<>();

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        availableModules.clear();
        final JahiaTemplateManagerService jahiaTemplateManagerService = ServicesRegistry.getInstance().getJahiaTemplateManagerService();
        jahiaTemplateManagerService.getAvailableTemplatePackages().stream()
                .map(JahiaTemplatesPackage::getId)
                .collect(Collectors.toCollection(() -> availableModules));
    }

    @Override
    protected ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        availableModules.clear();
        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        final JCRSiteNode site = (JCRSiteNode) node;
        site.getInstalledModules().stream()
                .filter(m -> !availableModules.contains(m))
                .forEach(undeployedModule ->
                        errors.addError(createError(node, "Undeployed module still activated on a site")
                                .addExtraInfo("module", undeployedModule)));

        return errors;
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError integrityError) throws RepositoryException {
        final JCRSiteNode site = (JCRSiteNode) node;
        final String missingModule = (String) integrityError.getExtraInfo("module");
        final JahiaTemplateManagerService jahiaTemplateManagerService = ServicesRegistry.getInstance().getJahiaTemplateManagerService();
        final Set<Bundle> bundles = jahiaTemplateManagerService.getModuleStates().keySet();
        final Collection<String> installedModuleIDs = CollectionUtils.collect(bundles, BundleUtils::getModuleId);
        if (installedModuleIDs.contains(missingModule)) {
            logger.info(String.format("The module %s has not been unreferenced from the site %s since it is now installed on the server",
                    missingModule, site.getSiteKey()));
            return true;
        }
        final List<String> siteModules = site.getInstalledModules();
        final boolean remove = siteModules.remove(missingModule);
        if (!remove) {
            logger.info(String.format("The module %s is already unreferenced from the site %s",
                    missingModule, site.getSiteKey()));
            return true;
        }
        site.setInstalledModules(siteModules);
        site.getSession().save();
        logger.info(String.format("Unreferenced the module %s from the site %s", missingModule, site.getSiteKey()));
        return true;
    }
}
