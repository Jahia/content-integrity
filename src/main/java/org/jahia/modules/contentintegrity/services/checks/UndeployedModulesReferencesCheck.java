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
            final ContentIntegrityError error = createError(node, String.format("The modules %s are activated on the site %s, but not available", undeployedModules, site.getTitle()));
            error.setExtraInfos(undeployedModules);
            return createSingleError(error);
        }

        return null;
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError integrityError) throws RepositoryException {
        final JCRSiteNode site = (JCRSiteNode) node;
        final List<String> missingModules = (List<String>) integrityError.getExtraInfos();
        if (CollectionUtils.isEmpty(missingModules)) return true;
        final JahiaTemplateManagerService jahiaTemplateManagerService = ServicesRegistry.getInstance().getJahiaTemplateManagerService();
        final Set<Bundle> bundles = jahiaTemplateManagerService.getModuleStates().keySet();
        final Set<String> installedModuleIDs = new HashSet<>();
        installedModuleIDs.addAll(CollectionUtils.collect(bundles, new Transformer<Bundle, String>() {
            @Override
            public String transform(Bundle input) {
                return BundleUtils.getModuleId(input);
            }
        }));
        boolean success = true;
        for (String undeployedModule : missingModules) {
            if (installedModuleIDs.contains(undeployedModule)) {
                success = false;
                continue;
            }
            final List<String> siteModules = site.getInstalledModules();
            siteModules.remove(undeployedModule);
            site.setInstalledModules(siteModules);
            site.getSession().save();
            logger.info(String.format("Unreferenced the module %s from the site %s", undeployedModule, site.getTitle()));
        }

        return success;
    }
}
