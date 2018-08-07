package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.PropertyDefinition;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + ":Boolean=false",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_CONTENT + "," + Constants.JAHIANT_PAGE
})
public class UndeclaredPropertiesSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UndeclaredPropertiesSanityCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper nodeWrapper) {
        try {
            final PropertyIterator propertyIterator = nodeWrapper.getRealNode().getProperties();
            //final PropertyIterator propertyIterator = nodeWrapper.getProperties();
            while (propertyIterator.hasNext()) {
                final Property property = propertyIterator.nextProperty();
                final JCRPropertyWrapper propertyDX = nodeWrapper.getProperty(property.getName());
                logger.info("Prop: " + property.getName());
                final PropertyDefinition definition = property.getDefinition();
                logger.info("Prop def: " + definition.getName());
                //logger.info("i18n: " + ((ExtendedPropertyDefinition)definition).isInternationalized());
                logger.info("NT: " + definition.getDeclaringNodeType().getName());
                logger.info("EPD1: " + nodeWrapper.getProperty(property.getName()).getDefinition());
                final ExtendedPropertyDefinition epd = nodeWrapper.getApplicablePropertyDefinition(property.getName());
                logger.info("EPD2: " + epd);
            }
        } catch (RepositoryException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }

        //TODO: review me, I'm generated
        return super.checkIntegrityBeforeChildren(nodeWrapper);
    }
}
