package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.query.QueryWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PATH_SEPARATOR;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=jnt:template",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/modules"
})
public class TemplatesIndexationCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(TemplatesIndexationCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper template) {
        // Example: /modules/templates-system/9.0.0/templates/base/home
        final String templatePath = template.getPath();
        final String[] parts = StringUtils.split(templatePath, JCR_PATH_SEPARATOR, 4);
        if (parts.length != 4) {
            logger.error(String.format("Unexpected template path: %s", templatePath));
            return null;
        }
        final int modulePathLength = parts[0].length() + parts[1].length() + parts[2].length() + 3;
        final String modulePath = templatePath.substring(0, modulePathLength);
        
        final String query = String.format("select * from [jnt:template] where isdescendantnode('%s') and name()='%s'",
                JCRContentUtils.sqlEncode(modulePath), JCRContentUtils.sqlEncode(template.getName()));
        final QueryWrapper q;
        try {
            q = template.getSession().getWorkspace().getQueryManager().createQuery(query, Query.JCR_SQL2);
            final QueryResultWrapper result = q.execute();
            for (JCRNodeWrapper resultNode : result.getNodes()) {
                if (StringUtils.equals(template.getIdentifier(), resultNode.getIdentifier()))
                    return null; // The template is correctly indexed
            }

            return createSingleError(createError(template, "The template is not correctly indexed"));
        } catch (RepositoryException e) {
            logger.error("Error when running the query " + query, e);
        }
        return null;
    }
}

/*
Notes:

The check is done on every installed module/version, no matter if the current version is started or not.
If a not indexed template is identified for a stopped version, this will have no functional impact immediately,
but it would if the version was started, reason why such error is not ignored.
 */
