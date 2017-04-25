package org.jahia.modules.verifyintegrity.services;

import org.jahia.registries.ServicesRegistry;
import org.jahia.services.JahiaService;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public abstract class ContentIntegrityCheck implements InitializingBean, DisposableBean, Comparable<ContentIntegrityCheck> {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityCheck.class);

    private float priority = 100f;

    @Override
    public void afterPropertiesSet() throws Exception {
        final JahiaService service = ServicesRegistry.getInstance().getService("contentIntegrity");
        if (service == null) {
            logger.error("Impossible to register in the content integrity service, as this one is missing");
            return;
        }

        ((ContentIntegrityService) service).registerIntegrityCheck(this);
    }

    @Override
    public void destroy() throws Exception {
        final JahiaService service = ServicesRegistry.getInstance().getService("contentIntegrity");
        if (service == null) {
            logger.error("Impossible to unregister in the content integrity service, as this one is missing");
            return;
        }

        ((ContentIntegrityService) service).unregisterIntegrityCheck(this);
    }

    public void setPriority(float priority) {
        this.priority = priority;
    }

    public float getPriority() {
        return priority;
    }

    @Override
    public int compareTo(ContentIntegrityCheck o) {
        return (int) (priority - o.getPriority());
    }

    @Override
    public String toString() {
        return String.format("%s (priority: %s)", this.getClass().getName(), priority);
    }

    abstract void checkIntegrity(JCRNodeWrapper node);
}
