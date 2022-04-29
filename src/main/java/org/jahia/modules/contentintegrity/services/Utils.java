package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.impl.ExternalLogger;
import org.jahia.osgi.BundleUtils;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final String JCR_REPORTS_FOLDER_NAME = "content-integrity-reports";
    private static final String DEFAULT_CSV_HEADER = "Check ID,Fixed,Error type,Workspace,Node identifier,Node path,Node primary type,Node mixins,Locale,Error message,Extra information";

    public static ContentIntegrityService getContentIntegrityService() {
        return BundleUtils.getOsgiService(ContentIntegrityService.class, null);
    }

    public static void log(String message, Logger log, ExternalLogger externalLogger) {
        if (log != null) log.info(message);
        if (externalLogger != null) externalLogger.logLine(message);
    }

    public static List<String> getChecksToExecute(ContentIntegrityService service, List<String> whiteList, List<String> blackList, ExternalLogger externalLogger) {
        if (CollectionUtils.isEmpty(whiteList) && CollectionUtils.isEmpty(blackList)) return null;

        if (CollectionUtils.isNotEmpty(whiteList)) {

            return CollectionUtils.isEmpty(blackList) ? whiteList : CollectionUtils.removeAll(whiteList, blackList)
                    .stream()
                    .map(id -> {
                        if (service.getContentIntegrityCheck(id) != null) return id;
                        externalLogger.logLine("Skipping invalid ID: " + id);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            return service.getContentIntegrityChecksIdentifiers(true).stream().filter(id -> !blackList.contains(id)).collect(Collectors.toList());
        }
    }

    private static String generateReportFilename(ContentIntegrityResults results, boolean excludeFixedErrors) {
        return String.format("%s-%s.csv", results.getID(), excludeFixedErrors ? "remainingErrors" : "full");
    }

    private static List<String> toFileContent(ContentIntegrityResults results, boolean noCsvHeader) {
        final List<String> lines = results.getErrors().stream()
                .map(ContentIntegrityError::toCSV)
                .collect(Collectors.toList());
        if (!noCsvHeader) {
            final String header = SettingsBean.getInstance().getString("modules.contentIntegrity.csv.header", DEFAULT_CSV_HEADER);
            lines.add(0, header);
        }

        return lines;
    }

    public static boolean writeDumpInTheJCR(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader, ExternalLogger externalLogger) {
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
                    final String filename = generateReportFilename(results, excludeFixedErrors);

                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try {
                        IOUtils.writeLines(toFileContent(results, noCsvHeader), null, out, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        logger.error("", e);
                        return false;
                    }
                    final byte[] bytes = out.toByteArray();

                    final JCRNodeWrapper reportNode = outputDir.uploadFile(filename, new ByteArrayInputStream(bytes), "text/csv");
                    session.save();
                    externalLogger.logLine("Written the report in " + reportNode.getPath());

                    return true;
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    public static boolean writeDumpOnTheFilesystem(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();

        if (folderCreated && outputDir.canWrite()) {
            final File csvFile = new File(outputDir, Utils.generateReportFilename(results, excludeFixedErrors));
            final boolean exists = csvFile.exists();
            if (!exists) {
                try {
                    csvFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Impossible to create the file", e);
                    return false;
                }
            }
            final List<String> lines = Utils.toFileContent(results, noCsvHeader);
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

    public static ContentIntegrityResults mergeResults(Collection<ContentIntegrityResults> results) {
        if (CollectionUtils.isEmpty(results)) return null;

        final Long testDate = results.stream()
                .map(ContentIntegrityResults::getTestDate)
                .sorted().findFirst().orElse(0L);
        final Long duration = results.stream().map(ContentIntegrityResults::getTestDuration).reduce(0L, Long::sum);
        final Set<String> workspaces = results.stream().map(ContentIntegrityResults::getWorkspace).collect(Collectors.toSet());
        final String workspace = workspaces.size() == 1 ? workspaces.stream().findAny().get() : "all-workspaces";
        final List<ContentIntegrityError> errors = results.stream()
                .map(ContentIntegrityResults::getErrors)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return new ContentIntegrityResults(testDate, duration, workspace, errors);
    }
}
