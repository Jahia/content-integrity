package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
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


@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=jnt:template",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/modules"
})
public class TemplatesIndexationCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(TemplatesIndexationCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper template) {
        final String templatePath = template.getPath();
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            idx = templatePath.indexOf('/', idx + 1);
            if (idx == -1) {
                logger.error(String.format("Unexpected template path: %s", templatePath));
                return null;
            }
        }
        final String modulePath = templatePath.substring(0, idx);

        final String query = String.format("select * from [jnt:template] as w where isdescendantnode(w, ['%s']) and name(w)='%s'",
                JCRContentUtils.sqlEncode(modulePath), JCRContentUtils.sqlEncode(template.getName()));
        QueryWrapper q = null;
        try {
            q = template.getSession().getWorkspace().getQueryManager().createQuery(query, Query.JCR_SQL2);
            final QueryResultWrapper result = q.execute();
            for (JCRNodeWrapper resultNode : result.getNodes()) {
                if (StringUtils.equals(template.getIdentifier(), resultNode.getIdentifier()))
                    return null; // The template is correctly indexed
            }

            return createSingleError(template, "The template is not correctly indexed");
        } catch (RepositoryException e) {
            logger.error("Error when running the query " + query, e);
        }
        return null;
    }
}
