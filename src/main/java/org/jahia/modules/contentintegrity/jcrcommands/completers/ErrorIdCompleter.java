package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;

import java.util.ArrayList;
import java.util.List;

import static org.jahia.modules.contentintegrity.jcrcommands.PrintPreviousTestCommand.LAST_PRINTED_TEST;

@Service
public class ErrorIdCompleter extends SimpleCompleter {

    @Reference
    private ContentIntegrityService integrityService;

    private static final String OPTION = "-t";
    private static final String ALIAS = "--test";

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        final String testID = StringUtils.defaultIfEmpty(getOptionValue(commandLine, OPTION, ALIAS), (String) session.get(LAST_PRINTED_TEST));
        final ContentIntegrityResults testResults = integrityService.getTestResults(testID);
        final List<ContentIntegrityError> errors;
        if (testResults == null || CollectionUtils.isEmpty(errors = testResults.getErrors())) return null;

        // The IDs of the errors which are not fixed yet, and * for all of them
        final List<String> errorIDs = new ArrayList<>();
        for (int i = 0; i < errors.size(); i++) {
            if (!errors.get(i).isFixed()) errorIDs.add(Integer.toString(i));
        }
        errorIDs.add("*");
        return errorIDs;
    }
}
