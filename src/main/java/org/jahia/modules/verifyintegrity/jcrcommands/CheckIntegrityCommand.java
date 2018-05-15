package org.jahia.modules.verifyintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityResults;
import org.jahia.modules.verifyintegrity.services.Utils;

@Command(scope = "jcr", name = "integrity-check")
@Service
public class CheckIntegrityCommand extends JCRCommandSupport implements Action {

    @Reference
    Session session;

    @Option(name = "-l", aliases = "--limit", description = "Maximum number of lines to print.")
    private String limit;

    @Option(name = "-lc", aliases = "--listChecks", description = "List the registered checks.")
    @Completion(OutputLevelCompleter.class)
    private String listChecks;

    @Override
    public Object execute() throws Exception {
        if (listChecks != null) {
            final boolean fullOutput = "full".equalsIgnoreCase(listChecks);
            for (String s : Utils.getContentIntegrityService().printIntegrityChecksList(!fullOutput)) {
                System.out.println(s);
            }
            return null;
        }
        final String currentPath = StringUtils.defaultString(getCurrentPath(session), "/");
        final ContentIntegrityResults integrityResults = Utils.getContentIntegrityService().validateIntegrity(currentPath, getCurrentWorkspace(session));
        printContentIntegrityErrors(integrityResults, limit);
        return null;
    }

}
