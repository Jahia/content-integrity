package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.jahia.api.Constants;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_VIRTUALSITE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/sites"
})
public class UndeployedModulesReferencesCheck extends AbstractContentIntegrityCheck implements AbstractContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(UndeployedModulesReferencesCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final JCRSiteNode site = (JCRSiteNode) node;
        final JahiaTemplateManagerService jahiaTemplateManagerService = ServicesRegistry.getInstance().getJahiaTemplateManagerService();
        final List<JahiaTemplatesPackage> availableTemplatePackages = jahiaTemplateManagerService.getAvailableTemplatePackages();
        final Collection<String> availableModules = CollectionUtils.collect(availableTemplatePackages, new Transformer<JahiaTemplatesPackage, String>() {
            @Override
            public String transform(JahiaTemplatesPackage input) {
                return input.getId();
            }
        });
        List<String> undeployedModules = null;
        for (String module : ((JCRSiteNode) node).getInstalledModules()) {
            if (!availableModules.contains(module)) {
                if (undeployedModules == null) undeployedModules = new ArrayList<>();
                undeployedModules.add(module);
            }
        }

        if (CollectionUtils.isNotEmpty(undeployedModules)) {
            final ContentIntegrityErrorList errors = createEmptyErrorsList();
            for (String undeployedModule : undeployedModules) {
                errors.addError(createErrorWithInfos(node, null,
                        String.format("The module %s is activated on the site %s, but not available", undeployedModule, site.getTitle()),
                        undeployedModule));
            }
            return errors;
        }

        return null;
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError integrityError) throws RepositoryException {
        final JCRSiteNode site = (JCRSiteNode) node;
        final String missingModule = (String) integrityError.getExtraInfos();
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
            logger.info(String.format("The module %s is arlready unreferenced from the site %s",
                    missingModule, site.getSiteKey()));
            return true;
        }
        site.setInstalledModules(siteModules);
        site.getSession().save();
        logger.info(String.format("Unreferenced the module %s from the site %s", missingModule, site.getSiteKey()));
        return true;
    }
}
