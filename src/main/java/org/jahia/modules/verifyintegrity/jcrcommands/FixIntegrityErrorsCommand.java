package org.jahia.modules.verifyintegrity.jcrcommands;

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
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Command(scope = "jcr", name = "fixIntegrity")
@Service
public class FixIntegrityErrorsCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(FixIntegrityErrorsCommand.class);

    @Reference
    Session session;

    @Option(name = "-t", aliases = "--test", description = "ID of the previous test. Latest test used if not set.")
    @Completion(TestDateCompleter.class)
    private String testID;

    @Argument(description = "ID of the error to fix.")
    @Completion(ErrorIdCompleter.class)
    private String errorID;

    @Override
    public Object execute() throws Exception {

        final ContentIntegrityService contentIntegrityService = ContentIntegrityService.getInstance();
        final List<ContentIntegrityError> testResults = contentIntegrityService.getTestResults(testID);
        if (testResults == null) {
            if (StringUtils.isBlank(testID)) System.out.println("No test results found");
            else System.out.println("The specified test results couldn't be found");
            return null;
        }
        final int errorIdx = NumberUtils.toInt(errorID, -1);
        if (errorIdx < 0 || errorIdx >= testResults.size()) {
            System.out.println("The specified error couldn't be found");
            return null;
        }
        final ContentIntegrityError error = testResults.get(errorIdx);
        if (error.isFixed()) {
            System.out.println("The error is already fixed");
            return null;
        }
        contentIntegrityService.fixError(error);
        if (error.isFixed()) System.out.println("Error fixed");
        else System.out.println("Impossible to fix the error");
        return null;
    }

}
