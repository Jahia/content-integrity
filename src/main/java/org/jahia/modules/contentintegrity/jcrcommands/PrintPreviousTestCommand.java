package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.jcrcommands.completers.TestDateCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "jcr", name = "integrity-printTestResults", description = "Reprints the result of some previous test")
@Service
public class PrintPreviousTestCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintPreviousTestCommand.class);

    public static final String LAST_PRINTED_TEST = "lastPrintedTest";

    @Reference
    Session session;

    @Argument(description = "Test date")
    @Completion(TestDateCompleter.class)
    private String testDate;

    @Option(name = "-l", aliases = "--limit", description = "Maximum number of lines to print.")
    private String limit;

    @Option(name = "-ef", aliases = "--excludeFixedErrors", description = "Specifies if the already fixed errors have to be excluded.")
    private boolean excludeFixedErrors;

    @Option(name = "-d", aliases = {"--dump", "--dumpToCSV"}, description = "Dumps the errors into a CSV file. By default, " +
            "the file is written on the filesystem in temp/content-integrity/ . The limit option is ignored when dumping.")
    private boolean dumpToCSV;

    @Option(name = "-u", aliases = {"--upload"}, description = "Uploads the dump in the JCR instead of writing in on the filesystem. " +
            "This option has no effect if not combined with -d")
    private boolean uploadDump;

    @Option(name = "-nh", aliases = {"--noCSVHeader"}, description = "Generates a CSV file without header. " +
            "This option is ignored when not generating a CSV file")
    private boolean noCsvHeader;

    @Override
    public Object execute() throws Exception {
        final ContentIntegrityResults results;
        final String errorMsg;

        if (StringUtils.isBlank(testDate)) {
            results = Utils.getContentIntegrityService().getLatestTestResults();
            errorMsg = "No previous test results found";
        } else {
            results = Utils.getContentIntegrityService().getTestResults(testDate);
            errorMsg = "No previous test results found for this date";
        }

        if (results == null) {
            System.out.println(errorMsg);
            return null;
        }

        session.put(LAST_PRINTED_TEST, testDate);
        if (dumpToCSV) {
            if (uploadDump) {
                if (!Utils.writeDumpInTheJCR(results, excludeFixedErrors, !noCsvHeader, CONSOLE)) {
                    System.out.println("Failed to write the report in the JCR");
                }
            } else if (!Utils.writeDumpOnTheFilesystem(results, excludeFixedErrors, !noCsvHeader)) {
                System.out.println("Failed to write the report on the filesystem");
            }
        } else {
            printContentIntegrityErrors(results, limit, !excludeFixedErrors, session);
        }
        return null;
    }
}
