package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.jcrcommands.completers.JCRNodeCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Command(scope = "jcr", name = "integrity-check", description = "Runs an integrity scan")
@Service
public class CheckIntegrityCommand extends JCRCommandSupport implements Action {

    protected static final char SKIP_MARKER = ':';
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

    @Option(name = "-c", aliases = "--checks", multiValued = true, description = "Checks to execute, specified by their ID. Only the specified checks will be executed during the current scan, no matter the global configuration. The check IDs can also be prefixed with '" + SKIP_MARKER + "' to specify a check to be skipped. In such case, the scan will execute all the currently active checks but those specified to be skipped during the current scan")
    private List<String> checks;

    @Override
    public Object execute() throws Exception {
        final String currentPath = StringUtils.defaultString(getCurrentPath(session), "/");
        final ContentIntegrityService service = Utils.getContentIntegrityService();
        final ContentIntegrityResults integrityResults = service.validateIntegrity(currentPath, excludedPaths, getCurrentWorkspace(session), getChecksToExecute(service));
        printContentIntegrityErrors(integrityResults, limit);
        return null;
    }

    private List<Long> getChecksToExecute(ContentIntegrityService service) {
        if (CollectionUtils.isEmpty(checks)) return null;

        final Map<Boolean, List<String>> checkIDs = checks.stream().collect(Collectors.partitioningBy(id -> id.trim().charAt(0) != SKIP_MARKER));
        final List<Long> whiteList = checkIDs.get(true).stream()
                .map(id -> parseID(id.trim())).filter(Objects::nonNull).collect(Collectors.toList());
        final List<Long> blackList = checkIDs.get(false).stream()
                .map(id -> parseID(id.trim().substring(1))).filter(Objects::nonNull).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(whiteList)) {
            whiteList.removeAll(blackList);
            return whiteList.stream().map(id -> {
                if (service.getContentIntegrityCheck(id) != null) return id;
                System.out.println("Skipping invalid ID: " + id);
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            return service.getContentIntegrityChecksIdentifiers(true).stream().filter(id -> !blackList.contains(id)).collect(Collectors.toList());
        }
    }

    private Long parseID(String str) {
        if (str == null) {
            return null;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException nfe) {
            System.out.println("Skipping invalid ID: " + str);
            return null;
        }
    }
}
