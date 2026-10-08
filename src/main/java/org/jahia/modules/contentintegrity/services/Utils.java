package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.bin.Jahia;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.api.ExternalLogger;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.reporting.CsvReport;
import org.jahia.modules.contentintegrity.services.reporting.ExcelReport;
import org.jahia.modules.contentintegrity.services.reporting.Report;
import org.jahia.modules.contentintegrity.services.reporting.ReportWriter;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.services.impl.Constants.ACE_TYPE_DENY;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PATH_SEPARATOR_CHAR;
import static org.jahia.modules.contentintegrity.services.impl.Constants.NODE_UNDER_MODULES_PATH_PREFIX;
import static org.jahia.modules.contentintegrity.services.impl.Constants.NODE_UNDER_SITE_PATH_PREFIX;
import static org.jahia.modules.contentintegrity.services.impl.Constants.TAB_LVL_1;
import static org.jahia.modules.contentintegrity.services.impl.Constants.TAB_LVL_2;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    static final String JCR_REPORTS_FOLDER_NAME = "content-integrity-reports";
    static final String REPORTS_FOLDER_PARENT_PATH = "/sites/systemsite/files";
    private static final String PRIVILEGED_GROUP_PRINCIPAL = "g:" + JahiaGroupManagerService.PRIVILEGED_GROUPNAME;
    private static final String PRIVILEGED_ROLE = "privileged";
    public static final String ALL_WORKSPACES = "all-workspaces";
    private static final long APPROXIMATE_COUNT_FACTOR = 10L;
    private static final List<Report> reportGenerators = Arrays.asList(new CsvReport(), new ExcelReport());
    public static final String JAVA_ERROR_PREFIX = "[java error]";
    public static final String FIX_PERMISSION = "adminContentIntegrityFix";

    public enum LOG_LEVEL {
        TRACE, INFO, WARN, ERROR, DEBUG
    }

    public static ContentIntegrityService getContentIntegrityService() {
        return BundleUtils.getOsgiService(ContentIntegrityService.class, null);
    }

    /**
     * Tells if the current user may fix the errors. A fix writes to the repository with a system session, so the access
     * to the module, which allows to scan and read the results, is not enough: the permission is checked on the root node.
     */
    public static boolean canFixErrors() {
        try {
            return JCRSessionFactory.getInstance().getCurrentUserSession().getNode("/").hasPermission(FIX_PERMISSION);
        } catch (RepositoryException e) {
            logger.error("Impossible to check the permission " + FIX_PERMISSION, e);
            return false;
        }
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
     * <p>
     * If the specified log level is not enabled on the main logger, then nothing is logged, neither on the main logger nor on the external loggers.
     *
     * @param message         the message
     * @param logLevel        the log level
     * @param log             the main logger
     * @param t               the exception
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
            final String msg;
            if (StringUtils.isNotBlank(message)) {
                msg = t == null ? message : String.format("%s %s", JAVA_ERROR_PREFIX, message);
            } else if (t != null) {
                msg = String.format("%s %s: %s", JAVA_ERROR_PREFIX, t.getClass().getName(), t.getMessage());
            } else {
                return;
            }
            for (ExternalLogger l : externalLoggers) {
                l.logLine(msg);
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

    /**
     * Restricts the reports folder, created before this protection existed, to the server administrators. Run when the
     * module starts, so that the reports written by a previous version are protected without waiting for a new scan.
     */
    public static void restrictReportsFolderAccess() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final String path = REPORTS_FOLDER_PARENT_PATH + JCR_PATH_SEPARATOR_CHAR + JCR_REPORTS_FOLDER_NAME;
                if (session.nodeExists(path) && restrictReportsFolderAccess(session.getNode(path))) {
                    session.save();
                    logger.info("Restricted the access to the reports folder {}", path);
                }
                return null;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to restrict the access to the reports folder", e);
        }
    }

    /**
     * A report describes the whole scanned repository, while the API of the module is restricted to the server
     * administrators. In every site but the system site, the core denies the role privileged, granted at the root of the
     * repository to the group privileged, which every editor of every site belongs to (JahiaSitesService). The reports
     * folder is under the system site: the role is denied on it the same way, so that only the server administrators,
     * through their own role, read the reports.
     *
     * @return true if the ACL has been changed, and the session has to be saved
     */
    static boolean restrictReportsFolderAccess(JCRNodeWrapper reportsFolder) throws RepositoryException {
        final String folderPath = reportsFolder.getPath();
        final boolean alreadyDenied = Optional.ofNullable(reportsFolder.getAclEntries().get(PRIVILEGED_GROUP_PRINCIPAL))
                .map(entries -> entries.stream().anyMatch(entry -> StringUtils.equals(entry[0], folderPath)
                        && StringUtils.equals(entry[1], ACE_TYPE_DENY)
                        && StringUtils.equals(entry[2], PRIVILEGED_ROLE)))
                .orElse(false);
        if (alreadyDenied) return false;
        reportsFolder.denyRoles(PRIVILEGED_GROUP_PRINCIPAL, Collections.singleton(PRIVILEGED_ROLE));
        return true;
    }

    /**
     * True if the node of the error is virtual: served by an external provider, such as a mount point, and not stored in
     * the JCR. Its errors are not fixed, as a fix would change the external source, for example delete a file.
     */
    public static boolean isOnVirtualNode(ContentIntegrityError error) {
        final String path = error.getPath();
        if (StringUtils.isBlank(path)) return false;
        final JCRStoreProvider provider = JCRSessionFactory.getInstance().getProvider(path, false);
        return provider != null && !provider.isDefault();
    }

    public static boolean writeDumpInTheJCR(ContentIntegrityResults results, boolean excludeFixedErrors, ExternalLogger externalLogger) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final String resultsSignature = results.getSignature(excludeFixedErrors);
                final JCRNodeWrapper outputDir;
                try {
                    // The full reports go to the report node of the scan, stored since its start
                    outputDir = ResultsStore.getOrCreateReportNode(session, results, resultsSignature);
                    writeReportMetadata(outputDir, results);
                } catch (RepositoryException re) {
                    logger.error("Impossible to retrieve the reports folder", re);
                    return false;
                }

                return writeReports(results, excludeFixedErrors, (name, extension, contentType, data) -> {
                    final JCRNodeWrapper reportNode;
                    try {
                        reportNode = outputDir.uploadFile(name, new ByteArrayInputStream(data), contentType);
                        reportNode.addMixin(Constants.JAHIAMIX_NOLIVE);
                        session.save();
                    } catch (RepositoryException e) {
                        logger.error("Impossible to upload the report", e);
                        return false;
                    }
                    final String reportPath = reportNode.getPath();
                    results.addJcrReport(reportNode.getName(), reportPath, extension);
                    externalLogger.logLine("Written the report in " + reportPath);

                    return true;
                });
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    private static boolean writeReports(ContentIntegrityResults results, boolean excludeFixedErrors, ReportWriter reportWriter) {
        final String resultsSignature = results.getSignature(excludeFixedErrors);
        final Integer chunksSize = reportGenerators.stream()
                .map(Report::getMaxNumberOfLines)
                .min(Integer::compareTo)
                .get();
        final long nbErrors = results.getErrors().stream()
                .filter(error -> !excludeFixedErrors || !error.isFixed())
                .count();
        if (nbErrors < chunksSize) {
            final List<ContentIntegrityError> errors = results.getErrors().stream()
                    .filter(error -> !excludeFixedErrors || !error.isFixed())
                    .collect(Collectors.toList());
            return writeReports(errors, resultsSignature, reportWriter);
        } else {
            long dumpedErrors = 0L;
            int idx = 0;
            boolean success = true;
            while (dumpedErrors < nbErrors) {
                final List<ContentIntegrityError> errors = results.getErrors().stream()
                        .filter(error -> !excludeFixedErrors || !error.isFixed())
                        .skip(dumpedErrors)
                        .limit(chunksSize)
                        .collect(Collectors.toList());
                idx++;
                dumpedErrors += chunksSize;
                success &= writeReports(errors, String.format("%s--%d", resultsSignature, idx), reportWriter);
            }
            return success;
        }
    }

    private static boolean writeReports(List<ContentIntegrityError> errors, String fileNameSignature, ReportWriter reportWriter) {
        final AtomicInteger reportsCount = new AtomicInteger();
        reportGenerators.forEach(reportGenerator -> {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                reportGenerator.write(out, errors);
            } catch (Throwable t) {
                logger.error("Impossible to generate the report content", t);
                return;
            }
            final byte[] bytes = out.toByteArray();
            if (reportWriter.saveFile(reportGenerator.getFileName(fileNameSignature),
                    reportGenerator.getFileExtension(),
                    reportGenerator.getFileContentType(),
                    bytes)) {
                reportsCount.incrementAndGet();
            }
        });

        return reportsCount.get() == reportGenerators.size();
    }

    static void writeReportMetadata(JCRNodeWrapper reportNode, ContentIntegrityResults results) throws RepositoryException {
        reportNode.addMixin("integrity:scanReport");
        reportNode.setProperty("integrity:errorsCount", results.getErrors().size());
        final String workspace = results.getWorkspace();
        final boolean multipleWorkspacesScanned = StringUtils.equals(workspace, ALL_WORKSPACES);
        reportNode.setProperty("integrity:scannedWorkspace", workspace);
        reportNode.setProperty("integrity:duration", results.getFormattedTestDuration());
        final GregorianCalendar testDate = new GregorianCalendar();
        testDate.setTimeInMillis(results.getTestDate());
        reportNode.setProperty("integrity:executionDate", testDate);
        reportNode.setProperty("integrity:moduleVersion", getContentIntegrityVersion());
        reportNode.setProperty("integrity:jahiaVersion", Jahia.VERSION);
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
                                lines.add(String.format("%s%s : %d", TAB_LVL_1, key, value.size()));
                                if (multipleWorkspacesScanned) {
                                    value.stream()
                                            .collect(Collectors.groupingBy(ContentIntegrityError::getWorkspace))
                                            .forEach((msg, errors) -> lines.add(String.format("%s%s : %d", TAB_LVL_2, msg, errors.size())));
                                }
                            });
                    return lines;
                })
                .flatMap(Collection::stream)
                .collect(Collectors.joining("\n"));
        reportNode.setProperty("integrity:countByErrorType", countByErrorType);
    }

    public static boolean writeDumpOnTheFilesystem(ContentIntegrityResults results, boolean excludeFixedErrors) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();

        if (folderCreated && outputDir.canWrite()) {
            return writeReports(results, excludeFixedErrors, (name, extension, contentType, data) -> {
                final File file = new File(outputDir, name);
                final boolean exists = file.exists();
                if (!exists) {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        logger.error("Impossible to create the file", e);
                        return false;
                    }
                }
                try {
                    FileUtils.writeByteArrayToFile(file, data);
                } catch (IOException e) {
                    logger.error("Impossible to write the file content", e);
                    return false;
                }
                final String reportPath = file.getPath();
                results.addFilesystemReport(file.getName(), reportPath, extension);
                System.out.println(String.format("%s %s", exists ? "Overwritten" : "Dumped into", reportPath));

                return true;
            });
        } else {
            logger.error("Impossible to write the folder " + outputDir.getPath());
        }

        return false;
    }

    /**
     * Merges the results of the workspaces scanned by one scan into the results of the scan, which keep the date of its
     * start, so that they keep the identifier of its report. The results of the scan are interrupted if those of one
     * workspace are.
     *
     * @param testDate  the date of the start of the scan
     * @param workspace the scanned workspace, or ALL_WORKSPACES when the scan covered several
     */
    public static ContentIntegrityResults mergeResults(Collection<ContentIntegrityResults> results, long testDate, String workspace) {
        final Long duration = results.stream().map(ContentIntegrityResults::getTestDuration).reduce(0L, Long::sum);
        final List<ContentIntegrityError> errors = results.stream()
                .map(ContentIntegrityResults::getErrors)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        final List<String> executionLog = results.stream().map(ContentIntegrityResults::getExecutionLog).flatMap(List::stream).collect(Collectors.toList());
        final boolean interrupted = results.stream().anyMatch(ContentIntegrityResults::isInterrupted);
        return new ContentIntegrityResults(testDate, duration, workspace, errors, executionLog).setInterrupted(interrupted);
    }

    public static ContentIntegrityErrorList mergeErrorLists(ContentIntegrityErrorList... errorLists) {
        if (errorLists.length == 0) return null;

        final List<ContentIntegrityErrorList> notEmptyLists = Arrays.stream(errorLists)
                .filter(Objects::nonNull)
                .filter(ContentIntegrityErrorList::hasErrors)
                .collect(Collectors.toList());
        if (notEmptyLists.isEmpty()) return null;

        return notEmptyLists.stream()
                .flatMap(list -> list.getNestedErrors().stream())
                .collect(ContentIntegrityErrorListImpl::createEmptyList, ContentIntegrityErrorList::addError, Utils::mergeErrorLists);
    }

    public static String getSiteKey(String path) {
        return getSiteKey(path, false);
    }

    public static String getSiteKey(String path, boolean considerModulesAsSites) {
        return getSiteKey(path, considerModulesAsSites, null);
    }

    public static String getSiteKey(String path, boolean considerModulesAsSites, Function<String,String> moduleKeyRewriter) {
        if (considerModulesAsSites && StringUtils.startsWith(path, NODE_UNDER_MODULES_PATH_PREFIX))
            return Optional.ofNullable(moduleKeyRewriter).orElseGet(Function::identity).apply(StringUtils.split(path, JCR_PATH_SEPARATOR_CHAR)[1]);

        return StringUtils.startsWith(path, NODE_UNDER_SITE_PATH_PREFIX) ?
                StringUtils.split(path, JCR_PATH_SEPARATOR_CHAR)[1] : null;
    }

    public static String getApproximateCount(long count, long threshold) {
        if (count < 0) throw new IllegalArgumentException(String.format("The count can't be negative: %d", count));
        if (threshold <= 0) throw new IllegalArgumentException(String.format("The threshold can't be negative or equal to zero: %d", threshold));

        long rangeBottom = 0;
        long rangeTop = threshold;
        while (count > rangeTop) {
            rangeBottom = rangeTop;
            rangeTop *= APPROXIMATE_COUNT_FACTOR;
        }
        return String.format("%d - %d", rangeBottom, rangeTop);
    }

    public static String getContentIntegrityVersion() {
        final Bundle bundle = FrameworkUtil.getBundle(Utils.class);
        return String.format("%s %s", bundle.getSymbolicName(), bundle.getHeaders().get(Constants.MANIFEST_HEADER_CONTENT_INTEGRITY_VERSION));
    }

    public static void validateImportCompatibility(List<ContentIntegrityError> errors, Logger logger, ExternalLogger... externalLoggers) {
        final boolean isImportCompatible = errors.stream()
                .filter(e -> !e.isFixed())
                .map(ContentIntegrityError::getErrorType)
                .noneMatch(ContentIntegrityErrorType::isBlockingImport);
        if (isImportCompatible) {
            log("The scanned tree is compatible with the XML import", logger, externalLoggers);
            return;
        }
        log("The scanned tree is incompatible with the XML import", LOG_LEVEL.WARN, logger, externalLoggers);
        log(TAB_LVL_1 + "The following sites contain incompatibilities:", LOG_LEVEL.WARN, logger, externalLoggers);
        errors.stream()
                .filter(e -> !e.isFixed())
                .filter(e -> e.getErrorType().isBlockingImport())
                .map(ContentIntegrityError::getSite)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()))
                .forEach((site, count) -> log(String.format("%s%s: %s errors", TAB_LVL_2, site, count), LOG_LEVEL.WARN, logger, externalLoggers));
    }

    @Deprecated
    public static void detectLegacyErrorTypes(List<ContentIntegrityError> errors, Logger logger, ExternalLogger... externalLoggers) {
        final List<String> checksUsingLegacyAPI = errors.stream()
                .filter(e -> e.getErrorType() instanceof ContentIntegrityErrorTypeImplLegacy)
                .map(ContentIntegrityError::getIntegrityCheckName)
                .distinct()
                .collect(Collectors.toList());
        if (checksUsingLegacyAPI.isEmpty()) return;
        log("Some checks are using a deprecated API for the error types. Please refactor them before this API is dropped", LOG_LEVEL.WARN, logger, externalLoggers);
        checksUsingLegacyAPI.stream()
                .map(TAB_LVL_1::concat)
                .forEach(msg -> log(msg, LOG_LEVEL.WARN, logger, externalLoggers));
    }
}
