package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
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
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.jcrcommands.completers.ErrorIdCompleter;
import org.jahia.modules.contentintegrity.jcrcommands.completers.TestDateCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Command(scope = "jcr", name = "integrity-fix", description = "Allows to fix an error identified by an integrity check")
@Service
public class FixIntegrityErrorsCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(FixIntegrityErrorsCommand.class);

    private static final boolean devMode = Boolean.parseBoolean(getProperty("modules.contentIntegrity.devMode"));

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
        if (!devMode) {
            System.out.println("Not yet available, coming soon!");
            return null;
        }

        if (CollectionUtils.isEmpty(errorIDs)) {
            System.out.println("No error specified");
            return null;
        }

        final ContentIntegrityService contentIntegrityService = Utils.getContentIntegrityService();
        final ContentIntegrityResults results = contentIntegrityService.getTestResults(testID);
        if (results == null) {
            if (StringUtils.isBlank(testID)) System.out.println("No test results found");
            else System.out.println("The specified test results couldn't be found");
            return null;
        }

        final List<ContentIntegrityError> errors = results.getErrors();
        if (errorIDs.contains("*")) {
            for (int i = 0; i < errors.size(); i++) {
                final ContentIntegrityError error = errors.get(i);
                if (!error.isFixed())
                    fixSingleError(error, i, contentIntegrityService);
            }
        } else {
            for (String errorID : errorIDs) {
                final int errorIdx = NumberUtils.toInt(errorID, -1);
                if (errorIdx < 0 || errorIdx >= errors.size()) {
                    System.out.println(String.format("The specified error (id=%s) couldn't be found", errorID));
                    continue;
                }
                final ContentIntegrityError error = errors.get(errorIdx);
                if (error.isFixed()) {
                    System.out.println(String.format("The error (id=%s) is already fixed", errorID));
                    continue;
                }
                fixSingleError(error, errorIdx, contentIntegrityService);
            }
        }

        return null;
    }

    private void fixSingleError(ContentIntegrityError error, int errorID, ContentIntegrityService contentIntegrityService) {
        contentIntegrityService.fixError(error);
        if (error.isFixed()) System.out.println(String.format("Fixed the error id=%s", errorID));
        else System.out.println(String.format("Impossible to fix the error id=%s", errorID));

    }
}
