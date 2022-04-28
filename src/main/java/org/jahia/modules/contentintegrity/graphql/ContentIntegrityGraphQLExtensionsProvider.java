package org.jahia.modules.contentintegrity.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class ContentIntegrityGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityGraphQLExtensionsProvider.class);

    @Override
    public Collection<Class<?>> getExtensions() {
        return Collections.singletonList(QueryExtensions.class);
    }
}
