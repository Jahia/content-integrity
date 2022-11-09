package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.jahia.modules.contentintegrity.jcrcommands.PrintPreviousTestCommand.LAST_PRINTED_TEST;

@Service
public class ErrorIdCompleter implements Completer {

    private static final Logger logger = LoggerFactory.getLogger(ErrorIdCompleter.class);

    private static final String OPTION = "-t";
    private static final String ALIAS = "--test";

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final String testID = getTestID(session, commandLine);
        final ContentIntegrityResults testResults = Utils.getContentIntegrityService().getTestResults(testID);
        final List<ContentIntegrityError> errors;
        if (testResults == null || CollectionUtils.isEmpty(errors = testResults.getErrors())) return -1;

        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            for (int i = 0; i < errors.size(); i++) {
                final ContentIntegrityError error = errors.get(i);
                if (!error.isFixed()) candidates.add(Integer.toString(i));
            }
            candidates.add("*");
        } else {
            for (int i = 0; i < errors.size(); i++) {
                final ContentIntegrityError error = errors.get(i);
                if (!error.isFixed()) {
                    final String errorID = Integer.toString(i);
                    if (errorID.startsWith(argument)) candidates.add(errorID);
                }
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }

    private String getTestID(Session session, CommandLine commandLine) {
        if (commandLine.getArguments().length > 0) {
            final List<String> arguments = Arrays.asList(commandLine.getArguments());
            if (arguments.contains(OPTION)) {
                final int index = arguments.indexOf(OPTION);
                if (arguments.size() > index) {
                    return arguments.get(index + 1);
                }
            }

            if (arguments.contains(ALIAS)) {
                final int index = arguments.indexOf(ALIAS);
                if (arguments.size() > index) {
                    return arguments.get(index + 1);
                }
            }
        }
        return (String) session.get(LAST_PRINTED_TEST);
    }
}
