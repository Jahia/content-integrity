package org.jahia.modules.verifyintegrity.jcrcommands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.verifyintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "jcr", name = "integrity-printChecks", description = "Prints out the registered checks")
@Service
public class PrintRegisteredChecksCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintRegisteredChecksCommand.class);

    @Reference
    Session session;

    @Option(name = "-l", aliases = "--outputLevel", description = "Output level")
    @Completion(OutputLevelCompleter.class)
    private String listChecks;

    @Override
    public Object execute() throws Exception {
        final boolean fullOutput = "full".equalsIgnoreCase(listChecks);
        for (String s : Utils.getContentIntegrityService().printIntegrityChecksList(!fullOutput)) {
            System.out.println(s);
        }
        return null;
    }
}
