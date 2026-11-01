package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.jcrcommands.completers.ErrorIdCompleter;
import org.jahia.modules.contentintegrity.jcrcommands.completers.TestDateCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;

import java.util.List;

@Command(scope = "jcr", name = "integrity-fix", description = "Allows to fix an error identified by an integrity check")
@Service
public class FixIntegrityErrorsCommand extends JCRCommandSupport implements Action {

    @Reference
    private ContentIntegrityService integrityService;

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
        if (CollectionUtils.isEmpty(errorIDs)) {
            System.out.println("No error specified");
            return null;
        }

        final ContentIntegrityService contentIntegrityService = integrityService;
        final ContentIntegrityResults results = loadTestResults(contentIntegrityService, testID);
        if (results == null) return null;

        final List<ContentIntegrityError> errors = results.getErrors();
        if (errorIDs.contains("*")) {
            for (int i = 0; i < errors.size(); i++) {
                final ContentIntegrityError error = errors.get(i);
                if (!error.isFixed())
                    fixSingleError(error, i, contentIntegrityService);
            }
        } else {
            for (String errorID : errorIDs) {
                final ContentIntegrityError error = getError(errors, errorID);
                if (error == null) continue;
                if (error.isFixed()) {
                    System.out.println(String.format("The error (id=%s) is already fixed", errorID));
                    continue;
                }
                fixSingleError(error, NumberUtils.toInt(errorID), contentIntegrityService);
            }
        }
        // The fixed status is stored with the results, in the JCR
        contentIntegrityService.saveFixedErrors(results);

        return null;
    }

    private void fixSingleError(ContentIntegrityError error, int errorID, ContentIntegrityService contentIntegrityService) {
        contentIntegrityService.fixError(error);
        if (error.isFixed()) System.out.println(String.format("Fixed the error id=%s", errorID));
        else System.out.println(String.format("Impossible to fix the error id=%s", errorID));

    }
}
