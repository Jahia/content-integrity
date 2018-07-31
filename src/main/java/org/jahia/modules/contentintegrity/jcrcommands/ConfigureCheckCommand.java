package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.jcrcommands.completers.BooleanCompleter;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "jcr", name = "integrity-configureCheck", description = "Configures a registered check")
@Service
public class ConfigureCheckCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(ConfigureCheckCommand.class);

    @Reference
    Session session;

    @Option(name = "-id", description = "ID of the check to configure")
    private long checkID;

    @Option(name = "-e", aliases = "--enabled", description = "Specifies if the check has to be enabled/disabled")
    @Completion(BooleanCompleter.class)
    private String enabled;

    @Override
    public Object execute() throws Exception {
        final ContentIntegrityCheck integrityCheck = Utils.getContentIntegrityService().getContentIntegrityCheck(checkID);
        if (integrityCheck == null) {
            System.out.println(String.format("No check found for ID %s", checkID));
            return null;
        }
        if (enabled != null)
            integrityCheck.setEnabled(Boolean.parseBoolean(enabled));
        return null;
    }
}
