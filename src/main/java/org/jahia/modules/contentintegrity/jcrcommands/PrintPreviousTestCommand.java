package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.io.FileUtils;
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
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Command(scope = "jcr", name = "integrity-printTestResults", description = "Reprints the result of some previous test")
@Service
public class PrintPreviousTestCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintPreviousTestCommand.class);
    public static final String LAST_PRINTED_TEST = "lastPrintedTest";
    private static final String DEFAULT_CSV_HEADER = "Check ID,Fixed,Error type,Workspace,Node identifier,Node path,Node primary type,Node mixins,Locale,Error message,Extra information";

    @Reference
    Session session;

    @Argument(description = "Test date")
    @Completion(TestDateCompleter.class)
    private String testDate;

    @Option(name = "-l", aliases = "--limit", description = "Maximum number of lines to print.")
    private String limit;

    @Option(name = "-ef", aliases = "--excludeFixedErrors", description = "Specifies if the already fixed errors have to be excluded.")
    private boolean excludeFixedErrors;

    @Option(name = "-d", aliases = {"--dump", "--dumpToCSV"}, description = "Dumps the errors into a CSV file in " +
            "temp/content-integrity/. The limit option is ignored when dumping.")
    private boolean dumpToCSV;

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
            if (!writeDumpOnTheFilesystem(results)) {
                System.out.println("Failed to write the report on the filesystem");
            }
        } else {
            printContentIntegrityErrors(results, limit, !excludeFixedErrors);
        }
        return null;
    }

    private boolean writeDumpOnTheFilesystem(ContentIntegrityResults results) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();

        if (folderCreated && outputDir.canWrite()) {
            final File csvFile = new File(outputDir, String.format("%s-%s.csv",
                    results.getID(), excludeFixedErrors ? "remainingErrors" : "full"));
            final boolean exists = csvFile.exists();
            if (!exists) {
                try {
                    csvFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Impossible to create the file", e);
                    return false;
                }
            }
            final List<String> lines = (List<String>) CollectionUtils.collect(results.getErrors(), new Transformer() {
                @Override
                public Object transform(Object input) {
                    return ((ContentIntegrityError) input).toCSV();
                }
            });
            if (!noCsvHeader) {
                final String header = SettingsBean.getInstance().getString("modules.contentIntegrity.csv.header", DEFAULT_CSV_HEADER);
                lines.add(0, header);
            }
            try {
                FileUtils.writeLines(csvFile, "UTF-8", lines);
            } catch (IOException e) {
                logger.error("Impossible to write the file content", e);
                return false;
            }
            System.out.println(String.format("%s %s", exists ? "Overwritten" : "Dumped into", csvFile.getPath()));
        } else {
            logger.error("Impossible to write the folder " + outputDir.getPath());
            return false;
        }

        return true;
    }
}
