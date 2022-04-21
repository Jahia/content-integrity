package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.jcrcommands.completers.TestDateCompleter;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Command(scope = "jcr", name = "integrity-printTestResults", description = "Reprints the result of some previous test")
@Service
public class PrintPreviousTestCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(PrintPreviousTestCommand.class);
    public static final String LAST_PRINTED_TEST = "lastPrintedTest";
    private static final String DEFAULT_CSV_HEADER = "Check ID,Fixed,Error type,Workspace,Node identifier,Node path,Node primary type,Node mixins,Locale,Error message,Extra information";
    protected static final String JCR_REPORTS_FOLDER_NAME = "content-integrity-reports";

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
                if (!writeDumpInTheJCR(results)) {
                    System.out.println("Failed to write the report in the JCR");
                }
            } else if (!writeDumpOnTheFilesystem(results)) {
                System.out.println("Failed to write the report on the filesystem");
            }
        } else {
            printContentIntegrityErrors(results, limit, !excludeFixedErrors, session);
        }
        return null;
    }

    private boolean writeDumpOnTheFilesystem(ContentIntegrityResults results) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();

        if (folderCreated && outputDir.canWrite()) {
            final File csvFile = new File(outputDir, generateReportFilename(results));
            final boolean exists = csvFile.exists();
            if (!exists) {
                try {
                    csvFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Impossible to create the file", e);
                    return false;
                }
            }
            final List<String> lines = toFileContent(results);
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

    private boolean writeDumpInTheJCR(ContentIntegrityResults results) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    final JCRNodeWrapper filesFolder = session.getNode("/sites/systemsite/files");
                    final JCRNodeWrapper outputDir = filesFolder.hasNode(JCR_REPORTS_FOLDER_NAME) ?
                            filesFolder.getNode(JCR_REPORTS_FOLDER_NAME) :
                            filesFolder.addNode(JCR_REPORTS_FOLDER_NAME, Constants.JAHIANT_FOLDER);
                    if (!outputDir.isNodeType(Constants.JAHIANT_FOLDER)) {
                        logger.error(String.format("Impossible to write the folder %s of type %s", outputDir.getPath(), outputDir.getPrimaryNodeTypeName()));
                        return false;
                    }
                    final String filename = generateReportFilename(results);

                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try {
                        IOUtils.writeLines(toFileContent(results), null, out, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        logger.error("", e);
                        return false;
                    }
                    final byte[] bytes = out.toByteArray();

                    final JCRNodeWrapper reportNode = outputDir.uploadFile(filename, new ByteArrayInputStream(bytes), "text/csv");
                    session.save();
                    System.out.println("Written the report in " + reportNode.getPath());

                    return true;
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    private String generateReportFilename(ContentIntegrityResults results) {
        return String.format("%s-%s.csv", results.getID(), excludeFixedErrors ? "remainingErrors" : "full");
    }

    private List<String> toFileContent(ContentIntegrityResults results) {
        final List<String> lines = results.getErrors().stream()
                .map(ContentIntegrityError::toCSV)
                .collect(Collectors.toList());
        if (!noCsvHeader) {
            final String header = SettingsBean.getInstance().getString("modules.contentIntegrity.csv.header", DEFAULT_CSV_HEADER);
            lines.add(0, header);
        }

        return lines;
    }
}
