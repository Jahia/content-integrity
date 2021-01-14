package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.commons.Version;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class DeprecatedNodeTypesCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(DeprecatedNodeTypesCheck.class);

    private Map<String, Version> deprecatedTypes;

    @Override
    protected void activate(ComponentContext context) {
        super.activate(context);
        init();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        ContentIntegrityErrorList errors = null;
        for (String nt : deprecatedTypes.keySet()) {
            try {
                // TODO : if the type has been dropped, this might raise an error
                if (node.isNodeType(nt)) {
                    final Version version = deprecatedTypes.get(nt);
                    final String msg;
                    if (version == null) {
                        msg = StringUtils.EMPTY;
                    } else {
                        final Version jahiaVersion = new Version(Jahia.VERSION);
                        final int compareTo = jahiaVersion.compareTo(version);
                        if (compareTo < 0) continue;
                        msg = String.format(" since Jahia %s", version);
                    }
                    final ContentIntegrityError error = createError(node, String.format("The node type %s is deprecated%s", nt, msg));
                    errors = appendError(errors, error);
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
        return errors;
    }

    private void init() {
        deprecatedTypes = new HashMap<>();
        deprecatedTypes.put("jnt:page", new Version("7.3.6.0"));
        deprecatedTypes.put("jmix:image", new Version("7.3.5.0"));
    }
}
