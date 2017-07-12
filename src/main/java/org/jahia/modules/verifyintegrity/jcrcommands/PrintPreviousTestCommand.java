package org.jahia.modules.verifyintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Command(scope = "jcr", name = "printIntegrityTest")
@Service
public class PrintPreviousTestCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintPreviousTestCommand.class);
    public static final String LAST_PRINTED_TEST = "lastPrintedTest";

    @Reference
    Session session;

    @Argument(description = "Test date")
    @Completion(TestDateCompleter.class)
    private String testDate;

    @Override
    public Object execute() throws Exception {
        final List<ContentIntegrityError> errors;
        final String errorMsg;

        if (StringUtils.isBlank(testDate)) {
            errors = ContentIntegrityService.getInstance().getLatestTestResults();
            errorMsg = "No previous test results found";
        } else {
            errors = ContentIntegrityService.getInstance().getTestResults(testDate);
            errorMsg = "No previous test results found for this date";
        }

        if (errors == null) System.out.println(errorMsg);
        else {
            printContentIntegrityErrors(errors);
            session.put(LAST_PRINTED_TEST, testDate);
        }
        return null;
    }

}
