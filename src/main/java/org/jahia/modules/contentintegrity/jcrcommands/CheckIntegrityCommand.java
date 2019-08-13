package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.jcrcommands.completers.JCRNodeCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;

import java.util.List;

@Command(scope = "jcr", name = "integrity-check", description = "Runs an integrity scan")
@Service
public class CheckIntegrityCommand extends JCRCommandSupport implements Action {

    @Reference
    Session session;

    @Option(name = "-l", aliases = "--limit", description = "Maximum number of lines to print")
    private String limit;

    // If the root node of the scan is listed, then no node is scanned
    // if a node stored outside from the scanned subtree is listed, then excluding it has no effect
    // If a parent node of the root node of the scan is listed, then excluding it has no effect (e.g. exluding /sites when scanning from /sites/digitall will not prevent from scanning)
    @Option(name = "-x", aliases = "--exclude", multiValued = true, description = "Path(s) to exclude from the scan")
    @Completion(JCRNodeCompleter.class)
    private List<String> excludedPaths;

    @Override
    public Object execute() throws Exception {
        final String currentPath = StringUtils.defaultString(getCurrentPath(session), "/");
        final ContentIntegrityResults integrityResults = Utils.getContentIntegrityService().validateIntegrity(currentPath, excludedPaths, getCurrentWorkspace(session));
        printContentIntegrityErrors(integrityResults, limit);
        return null;
    }

}
