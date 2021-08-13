package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jahia.modules.contentintegrity.jcrcommands.completers.ErrorIdCompleter;
import org.jahia.modules.contentintegrity.jcrcommands.completers.TestDateCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Command(scope = "jcr", name = "integrity-printError", description = "Prints out an error identified by an integrity check with full details")
@Service
public class PrintErrorCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintErrorCommand.class);

    @Reference
    Session session;

    @Option(name = "-t", aliases = "--test", description = "ID of the test from which to load the error. Latest test used if not defined.")
    @Completion(TestDateCompleter.class)
    private String testID;

    @Argument(description = "ID of the error to fix. * for all", required = true, multiValued = true)
    @Completion(ErrorIdCompleter.class)
    private List<String> errorIDs;

    @Override
    public Object execute() throws Exception {
        final ContentIntegrityResults results;
        final String errorMsg;

        if (StringUtils.isBlank(testID)) {
            results = Utils.getContentIntegrityService().getLatestTestResults();
            errorMsg = "No previous test results found";
        } else {
            results = Utils.getContentIntegrityService().getTestResults(testID);
            errorMsg = "No previous test results found for this test ID";
        }

        if (results == null) {
            System.out.println(errorMsg);
            return null;
        }

        final List<ContentIntegrityError> errors = results.getErrors();
        for (String errorID : errorIDs) {
            final int errorIdx = NumberUtils.toInt(errorID, -1);
            if (errorIdx < 0 || errorIdx >= errors.size()) {
                System.out.println(String.format("The specified error (id=%s) couldn't be found", errorID));
                continue;
            }
            final ContentIntegrityError error = errors.get(errorIdx);
            final ShellTable table = new ShellTable();
            table.column("1");
            table.column("2");
            table.noHeaders();

            table.addRow().addContent("ID", errorID);
            table.addRow().addContent("Check name", error.getIntegrityCheckName());
            table.addRow().addContent("Check ID", error.getIntegrityCheckID());
            table.addRow().addContent("Fixed", error.isFixed());
            table.addRow().addContent("Workspace", error.getWorkspace());
            table.addRow().addContent("Locale", error.getLocale());
            table.addRow().addContent("Path", error.getPath());
            table.addRow().addContent("UUID", error.getUuid());
            table.addRow().addContent("Node type", error.getPrimaryType());
            table.addRow().addContent("Mixin types", error.getMixins());
            table.addRow().addContent("Message", error.getConstraintMessage());

            final Object extraInfos = error.getExtraInfos();
            if (extraInfos instanceof String)
                table.addRow().addContent("Extra infos", extraInfos);
            else if (extraInfos instanceof Collection)
                ((Collection) extraInfos).forEach(o -> table.addRow().addContent("", o));
            else if (extraInfos instanceof Map)
                ((Map) extraInfos).forEach((k, v) -> table.addRow().addContent(k, v));
            table.print(System.out, true);
            System.out.println("");
            System.out.println("");
        }
        return null;
    }
}
