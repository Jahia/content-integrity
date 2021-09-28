package org.jahia.modules.aclcleanup;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LightContentIntegrityService {

    private static final Logger logger = LoggerFactory.getLogger(LightContentIntegrityService.class);

    public List<String> checkIntegrity(String rootNode, String workspace, Map<String, Boolean> config) throws RepositoryException {
        return checkIntegrity(rootNode, null, workspace, config);
    }

    public List<String> checkIntegrity(String rootNode, String defaultRootNode, String workspace, Map<String, Boolean> config) throws RepositoryException {
        final List<String> output = new ArrayList<>();
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
            final JCRNodeWrapper node = getRootNode(rootNode, defaultRootNode, session);
            if (node == null) {
                output.add("/!\\ Invalid root node");
                return null;
            }
            final AceSanityCheck aceSanityCheck = new AceSanityCheck();
            aceSanityCheck.setFixErrors(config.getOrDefault("fix-errors", false));
            aceSanityCheck.setCheckExternalAce(config.getOrDefault("check-external-ace", false));
            aceSanityCheck.setCheckRegularAce(config.getOrDefault("check-regular-ace", false));
            aceSanityCheck.setCheckMissingExternalAce(config.getOrDefault("check-missing-external-ace", false));
            aceSanityCheck.setCheckUselessExternalAce(config.getOrDefault("check-useless-external-ace", false));
            aceSanityCheck.setCheckPrincipalOnAce(config.getOrDefault("check-principal", false));
            aceSanityCheck.initializeIntegrityTestInternal();
            checkIntegrity(node, aceSanityCheck, output);
            aceSanityCheck.finalizeIntegrityTestInternal();
            return null;
        });
        return output;
    }

    public JCRNodeWrapper getRootNode(String rootNode, String workspace) {
        return getRootNode(rootNode, null, workspace);
    }

    public JCRNodeWrapper getRootNode(String rootNode, String defaultRootNode, String workspace) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> getRootNode(rootNode, defaultRootNode, session));
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private JCRNodeWrapper getRootNode(String rootNode, String defaultRootNode, JCRSessionWrapper session) throws RepositoryException {
        if (StringUtils.isBlank(rootNode)) return session.getNode(defaultRootNode == null ? "/" : defaultRootNode);
        if (rootNode.trim().startsWith("/")) return session.getNode(rootNode);
        return session.getNodeByUUID(rootNode.trim());
    }

    private void checkIntegrity(JCRNodeWrapper node, AceSanityCheck aceSanityCheck, List<String> output) throws RepositoryException {
        if (System.getProperty("interruptScript") != null) {
            final String msg = "Script interrupted";
            output.add(msg);
            logger.info(msg);
            System.clearProperty("interruptScript");
            return;
        }

        if (!node.getSession().nodeExists(node.getPath())) return;

        for (JCRNodeWrapper child : node.getNodes()) {
            if ("/jcr:system".equals(child.getPath())) continue;
            checkIntegrity(child, aceSanityCheck, output);
        }

        if (!node.getSession().nodeExists(node.getPath())) return;
        addAll(output, aceSanityCheck.checkIntegrityAfterChildren(node));
    }

    private void addAll(List<String> output, List<String> newLines) {
        if (CollectionUtils.isEmpty(newLines)) return;
        output.addAll(newLines);
    }

    public long countErrors(List<String> buffer) {
        return buffer.stream().filter(s -> StringUtils.isNotBlank(s) && !s.trim().startsWith(">") && !s.trim().startsWith("/!\\")).count();
    }
}
