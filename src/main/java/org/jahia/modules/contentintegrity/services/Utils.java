package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.api.ExternalLogger;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final String JCR_REPORTS_FOLDER_NAME = "content-integrity-reports";
    private static final String CSV_SEPARATOR = ";";
    private static final String CSV_VALUE_WRAPPER = "\"";
    private static final String ESCAPED_CSV_VALUE_WRAPPER = CSV_VALUE_WRAPPER + CSV_VALUE_WRAPPER;
    private static final List<String> DEFAULT_CSV_HEADER_ITEMS = Arrays.asList("Check ID", "Fixed", "Error type", "Workspace", "Node identifier", "Node path", "Site", "Node primary type", "Node mixins", "Locale", "Error message", "Extra information", "Specific extra information");
    private static final String ALL_WORKSPACES = "all-workspaces";
    private static final String NODE_UNDER_SITE_PATH_PREFIX = "/sites/";
    private static final String NODE_UNDER_MODULES_PATH_PREFIX = "/modules/";
    private static final char NODE_PATH_SEPARATOR_CHAR = '/';

    public enum LOG_LEVEL {
        TRACE, INFO, WARN, ERROR, DEBUG
    }

    public static ContentIntegrityService getContentIntegrityService() {
        return BundleUtils.getOsgiService(ContentIntegrityService.class, null);
    }

    public static void log(String message, Logger log, ExternalLogger... externalLoggers) {
        log(message, LOG_LEVEL.INFO, log, externalLoggers);
    }

    public static void log(String message, LOG_LEVEL logLevel, Logger log, ExternalLogger... externalLoggers) {
        log(message, logLevel, log, null, externalLoggers);
    }

    /**
     * Log a message on the main logger as well as on external loggers.
     * The message and the exception are both logged on the main logger (if defined).
     * Only the message is logged on the external loggers, unless this message is blank. In such case, the message extracted from the exception is logged on the external loggers instead.
     *
     * If the specified log level is not enabled on the main logger, then nothing is logged, neither on the main logger nor on the external loggers.
     *
     * @param message the message
     * @param logLevel the log level
     * @param log the main logger
     * @param t the exception
     * @param externalLoggers the external loggers
     */
    public static void log(String message, LOG_LEVEL logLevel, Logger log, Throwable t, ExternalLogger... externalLoggers) {
        if (log != null) {
            switch (logLevel) {
                case TRACE:
                    if (!log.isTraceEnabled()) return;
                    if (t == null) {
                        log.trace(message);
                    } else {
                        log.trace(message, t);
                    }
                    break;
                case INFO:
                    if (!log.isInfoEnabled()) return;
                    if (t == null) {
                        log.info(message);
                    } else {
                        log.info(message, t);
                    }
                    break;
                case WARN:
                    if (!log.isWarnEnabled()) return;
                    if (t == null) {
                        log.warn(message);
                    } else {
                        log.warn(message, t);
                    }
                    break;
                case ERROR:
                    if (!log.isErrorEnabled()) return;
                    if (t == null) {
                        log.error(message);
                    } else {
                        log.error(message, t);
                    }
                    break;
                case DEBUG:
                    if (!log.isDebugEnabled()) return;
                    if (t == null) {
                        log.debug(message);
                    } else {
                        log.debug(message, t);
                    }
            }
        }
        if (externalLoggers.length > 0) {
            if (StringUtils.isNotBlank(message)) {
                for (ExternalLogger l : externalLoggers) {
                    l.logLine(message);
                }
            } else if (t != null) {
                final String msg = String.format("%s: %s", t.getClass().getName(), t.getMessage());
                for (ExternalLogger l : externalLoggers) {
                    l.logLine(msg);
                }
            }
        }
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
            String header = SettingsBean.getInstance().getString("modules.contentIntegrity.csv.header", null);
            if (StringUtils.isBlank(header)) {
                final StringBuilder sb = new StringBuilder();
                for (String item : DEFAULT_CSV_HEADER_ITEMS) {
                    appendToCSVLine(sb, item);
                }
                header = sb.toString();
            }
            lines.add(0, header);
        }

        return lines;
    }

    public static void appendToCSVLine(StringBuilder line, Object value) {
        if (line.length() > 0) line.append(CSV_SEPARATOR);
        line.append(CSV_VALUE_WRAPPER);
        if (value != null) {
            line.append(StringUtils.replace(String.valueOf(value), CSV_VALUE_WRAPPER, ESCAPED_CSV_VALUE_WRAPPER));
        }
        line.append(CSV_VALUE_WRAPPER);
    }

    public static String writeDumpInTheJCR(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader, ExternalLogger externalLogger) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<String>() {
                @Override
                public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    final JCRNodeWrapper filesFolder = session.getNode("/sites/systemsite/files");
                    final JCRNodeWrapper outputDir = filesFolder.hasNode(JCR_REPORTS_FOLDER_NAME) ?
                            filesFolder.getNode(JCR_REPORTS_FOLDER_NAME) :
                            filesFolder.addNode(JCR_REPORTS_FOLDER_NAME, Constants.JAHIANT_FOLDER);
                    if (!outputDir.isNodeType(Constants.JAHIANT_FOLDER)) {
                        logger.error(String.format("Impossible to write the folder %s of type %s", outputDir.getPath(), outputDir.getPrimaryNodeTypeName()));
                        return null;
                    }
                    final String filename = generateReportFilename(results, excludeFixedErrors);

                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try {
                        IOUtils.writeLines(toFileContent(results, noCsvHeader), null, out, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        logger.error("", e);
                        return null;
                    }
                    final byte[] bytes = out.toByteArray();

                    final JCRNodeWrapper reportNode = outputDir.uploadFile(filename, new ByteArrayInputStream(bytes), "text/csv");
                    reportNode.addMixin("jmix:nolive");
                    writeReportMetadata(reportNode, results);
                    session.save();
                    final String reportPath = reportNode.getPath();
                    externalLogger.logLine("Written the report in " + reportPath);

                    final Map<String, String> errorsMetadata = new HashMap<>();
                    errorsMetadata.put("report-filename", filename);
                    errorsMetadata.put("report-path", reportPath);
                    results.addMetadata(errorsMetadata);

                    return reportPath;
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private static void writeReportMetadata(JCRNodeWrapper reportNode, ContentIntegrityResults results) throws RepositoryException {
        reportNode.addMixin("integrity:scanReport");
        reportNode.setProperty("integrity:errorsCount", results.getErrors().size());
        final String workspace = results.getWorkspace();
        final boolean multipleWorkspacesScanned = StringUtils.equals(workspace, ALL_WORKSPACES);
        reportNode.setProperty("integrity:scannedWorkspace", workspace);
        reportNode.setProperty("integrity:duration", results.getFormattedTestDuration());
        final GregorianCalendar testDate = new GregorianCalendar();
        testDate.setTimeInMillis(results.getTestDate());
        reportNode.setProperty("integrity:executionDate", testDate);
        reportNode.setProperty("integrity:executionLog", StringUtils.join(results.getExecutionLog(), "\n"));
        final Map<String, List<ContentIntegrityError>> errorsByType = results.getErrors().stream()
                .collect(Collectors.groupingBy(ContentIntegrityError::getIntegrityCheckID));
        final String countByErrorType = errorsByType.entrySet().stream()
                .map(e -> {
                    final List<String> lines = new ArrayList<>();
                    lines.add(String.format("%s : %d errors", e.getKey(), e.getValue().size()));
                    e.getValue().stream()
                            .collect(Collectors.groupingBy(ContentIntegrityError::getConstraintMessage))
                            .forEach((key, value) -> {
                                lines.add(String.format("%s%s : %d", StringUtils.repeat(" ", 4), key, value.size()));
                                if (multipleWorkspacesScanned) {
                                    value.stream()
                                            .collect(Collectors.groupingBy(ContentIntegrityError::getWorkspace))
                                            .forEach((msg, errors) -> lines.add(String.format("%s%s : %d", StringUtils.repeat(" ", 8), msg, errors.size())));
                                }
                            });
                    return lines;
                })
                .flatMap(Collection::stream)
                .collect(Collectors.joining("\n"));
        reportNode.setProperty("integrity:countByErrorType", countByErrorType);
    }

    public static String writeDumpOnTheFilesystem(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();
        final String reportPath;

        if (folderCreated && outputDir.canWrite()) {
            final File csvFile = new File(outputDir, Utils.generateReportFilename(results, excludeFixedErrors));
            final boolean exists = csvFile.exists();
            if (!exists) {
                try {
                    csvFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Impossible to create the file", e);
                    return null;
                }
            }
            final List<String> lines = Utils.toFileContent(results, noCsvHeader);
            try {
                FileUtils.writeLines(csvFile, "UTF-8", lines);
            } catch (IOException e) {
                logger.error("Impossible to write the file content", e);
                return null;
            }
            reportPath = csvFile.getPath();
            System.out.println(String.format("%s %s", exists ? "Overwritten" : "Dumped into", reportPath));
        } else {
            logger.error("Impossible to write the folder " + outputDir.getPath());
            return null;
        }

        return reportPath;
    }

    public static ContentIntegrityResults mergeResults(Collection<ContentIntegrityResults> results) {
        if (CollectionUtils.isEmpty(results)) return null;

        final ContentIntegrityService contentIntegrityService = Utils.getContentIntegrityService();
        final Long testDate = results.stream()
                .map(ContentIntegrityResults::getTestDate)
                .sorted().findFirst().orElse(0L);
        final Long duration = results.stream().map(ContentIntegrityResults::getTestDuration).reduce(0L, Long::sum);
        final Set<String> workspaces = results.stream().map(ContentIntegrityResults::getWorkspace).collect(Collectors.toSet());
        final String workspace = workspaces.size() == 1 ? workspaces.stream().findAny().get() : ALL_WORKSPACES;
        final List<ContentIntegrityError> errors = results.stream()
                .peek(contentIntegrityService::removeErrorsFromCache)
                .map(ContentIntegrityResults::getErrors)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        final List<String> executionLog = results.stream().map(ContentIntegrityResults::getExecutionLog).reduce(new ArrayList<>(), (strings, strings2) -> {
            strings.addAll(strings2);
            return strings;
        });

        final ContentIntegrityResults mergedResults = new ContentIntegrityResults(testDate, duration, workspace, errors, executionLog);
        contentIntegrityService.storeErrorsInCache(mergedResults);
        return mergedResults;
    }

    public static String getSiteKey(String path) {
        return getSiteKey(path, false);
    }

    public static String getSiteKey(String path, boolean considerModulesAsSites) {
        if (considerModulesAsSites && StringUtils.length(path) > NODE_UNDER_MODULES_PATH_PREFIX.length() && StringUtils.startsWith(path, NODE_UNDER_MODULES_PATH_PREFIX))
            return StringUtils.split(path, NODE_PATH_SEPARATOR_CHAR)[1];

        return StringUtils.length(path) > NODE_UNDER_SITE_PATH_PREFIX.length() && StringUtils.startsWith(path, NODE_UNDER_SITE_PATH_PREFIX) ?
                StringUtils.split(path, NODE_PATH_SEPARATOR_CHAR)[1] : null;
    }
}
