package org.jahia.modules.contentintegrity.services;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Stores the results of the scans in the JCR, under /sites/systemsite/files/content-integrity-reports, split by year and
 * month. A scan has one report node, created when the scan starts and updated until its end:
 * - its properties describe the scan, so that the reports are listed without reading any file;
 * - its errors are written once the scan is over, as a gzipped JSON file next to the CSV and XLSX reports;
 * - the errors fixed afterwards are listed in a property, so that a fix does not rewrite the file.
 * A report node without the JSON file, such as one written before this format, is ignored.
 */
public final class ResultsStore {

    private static final Logger logger = LoggerFactory.getLogger(ResultsStore.class);

    static final String REPORTS_FOLDER_PATH = Utils.REPORTS_FOLDER_PARENT_PATH + Constants.JCR_PATH_SEPARATOR + Utils.JCR_REPORTS_FOLDER_NAME;
    static final String REPORT_MIXIN = "integrity:scanReport";
    private static final String PROP_RESULTS_ID = "integrity:resultsId";
    private static final String PROP_STATUS = "integrity:status";
    private static final String PROP_SERVER_ID = "integrity:serverId";
    private static final String PROP_EXECUTION_DATE = "integrity:executionDate";
    private static final String PROP_WORKSPACE = "integrity:scannedWorkspace";
    private static final String PROP_ERRORS_COUNT = "integrity:errorsCount";
    private static final String PROP_IMPORT_ERRORS_COUNT = "integrity:importErrorsCount";
    private static final String PROP_EXECUTION_LOG = "integrity:executionLog";
    private static final String PROP_FIXED_ERRORS = "integrity:fixedErrors";
    private static final String ERRORS_FILE_SUFFIX = "-errors.json.gz";
    private static final String ERRORS_FILE_CONTENT_TYPE = "application/gzip";
    private static final int FORMAT_VERSION = 1;
    // The format of the date in the identifier of results, as ContentIntegrityResults writes it
    private static final String ID_DATE_PATTERN = "yyyy_MM_dd-HH_mm_ss_SSS";
    private static final FastDateFormat SPLIT_FORMAT = FastDateFormat.getInstance("yyyy'/'MM");
    private static final FastDateFormat QUERY_DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZZ", TimeZone.getTimeZone("UTC"));
    private static final JsonFactory JSON = new JsonFactory();
    // The stored log of a scan keeps its first lines and its last ones, as the logs returned to the clients
    private static final int LOG_HEAD_SIZE = 100;
    private static final int LOG_TAIL_SIZE = 500;
    // The error types declared by each check class, to restore the very instances its fix compares with
    private static final Map<Class<?>, Map<String, ContentIntegrityErrorType>> ERROR_TYPES = new ConcurrentHashMap<>();

    private ResultsStore() {
    }

    public static String getServerId() {
        return System.getProperty("cluster.node.serverId", "jahiaServer1");
    }

    /**
     * Creates or updates the report node of the results: their status, their counts and the log of the scan. The errors
     * are written when withErrors is true, once the scan is over.
     */
    public static boolean save(ContentIntegrityResults results, List<String> executionLog, boolean withErrors) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final JCRNodeWrapper reportNode = getOrCreateReportNode(session, results, results.getSignature(false));
                if (withErrors) {
                    Utils.writeReportMetadata(reportNode, results);
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (OutputStream gzip = new GZIPOutputStream(out)) {
                        writeErrors(gzip, results.getErrors());
                    } catch (IOException e) {
                        logger.error("Impossible to write the errors of the results {}", results.getID(), e);
                        return false;
                    }
                    final JCRNodeWrapper errorsFile = reportNode.uploadFile(getErrorsFileName(results), new ByteArrayInputStream(out.toByteArray()), ERRORS_FILE_CONTENT_TYPE);
                    errorsFile.addMixin(Constants.JAHIAMIX_NOLIVE);
                    // The file carries the fixed status of each error
                    if (reportNode.hasProperty(PROP_FIXED_ERRORS)) reportNode.getProperty(PROP_FIXED_ERRORS).remove();
                    reportNode.setProperty(PROP_ERRORS_COUNT, results.getErrors().size());
                    reportNode.setProperty(PROP_IMPORT_ERRORS_COUNT, results.getErrors().stream().filter(ResultsStore::isBlockingImport).count());
                }
                reportNode.setProperty(PROP_RESULTS_ID, results.getID());
                reportNode.setProperty(PROP_STATUS, results.getStatus().getValue());
                reportNode.setProperty(PROP_WORKSPACE, results.getWorkspace());
                reportNode.setProperty(PROP_EXECUTION_DATE, toCalendar(results.getTestDate()));
                reportNode.setProperty(PROP_SERVER_ID, getServerId());
                reportNode.setProperty(PROP_EXECUTION_LOG, StringUtils.join(limitLog(executionLog), "\n"));
                session.save();
                return true;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to store the results {}", results.getID(), e);
            return false;
        }
    }

    /**
     * Records the errors of the results fixed since they were stored.
     */
    public static void saveFixedErrors(ContentIntegrityResults results) {
        final Set<String> fixedErrors = results.getErrors().stream()
                .filter(ContentIntegrityError::isFixed)
                .map(ContentIntegrityError::getErrorID)
                .collect(Collectors.toCollection(HashSet::new));
        if (fixedErrors.isEmpty()) return;
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final JCRNodeWrapper reportNode = findReportNode(session, results.getID());
                if (reportNode == null || !reportNode.hasNode(getErrorsFileName(results))) return null;
                fixedErrors.addAll(readFixedErrors(reportNode));
                reportNode.setProperty(PROP_FIXED_ERRORS, fixedErrors.toArray(new String[0]));
                session.save();
                return null;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to store the fixed errors of the results {}", results.getID(), e);
        }
    }

    /**
     * The stored results, from the oldest scan to the latest, read from the properties of their report nodes only.
     */
    public static List<ContentIntegrityResultsSummary> list() {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                if (!session.nodeExists(REPORTS_FOLDER_PATH)) return Collections.<ContentIntegrityResultsSummary>emptyList();
                final String statement = String.format("SELECT * FROM [%s] AS report WHERE ISDESCENDANTNODE(report, '%s') AND report.[%s] IS NOT NULL ORDER BY report.[%s]",
                        REPORT_MIXIN, REPORTS_FOLDER_PATH, PROP_STATUS, PROP_EXECUTION_DATE);
                final JCRNodeIteratorWrapper nodes = session.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getNodes();
                final List<ContentIntegrityResultsSummary> summaries = new ArrayList<>();
                while (nodes.hasNext()) {
                    final JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
                    final ContentIntegrityResults.Status status = ContentIntegrityResults.Status.fromValue(node.getPropertyAsString(PROP_STATUS));
                    // The results of a scan which is over are listed only with their errors
                    if (status == null || (isOver(status) && status != ContentIntegrityResults.Status.FAILED && !hasErrorsFile(node))) continue;
                    summaries.add(new ContentIntegrityResultsSummary(
                            node.getPropertyAsString(PROP_RESULTS_ID),
                            node.getProperty(PROP_EXECUTION_DATE).getDate().getTimeInMillis(),
                            node.getPropertyAsString(PROP_WORKSPACE),
                            status,
                            node.hasProperty(PROP_ERRORS_COUNT) ? node.getProperty(PROP_ERRORS_COUNT).getLong() : 0L,
                            node.hasProperty(PROP_IMPORT_ERRORS_COUNT) ? node.getProperty(PROP_IMPORT_ERRORS_COUNT).getLong() : 0L));
                }
                return summaries;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to list the stored results", e);
            return Collections.emptyList();
        }
    }

    /**
     * Reads the stored results. Those of a scan which is running, or which failed, have no errors.
     *
     * @param checks resolves an integrity check from its identifier, to restore the types of its errors
     * @return the results, or null if none are stored with this identifier
     */
    public static ContentIntegrityResults load(String resultsID, Function<String, ContentIntegrityCheck> checks) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final JCRNodeWrapper reportNode = findReportNode(session, resultsID);
                if (reportNode == null) return null;
                final ContentIntegrityResults.Status status = ContentIntegrityResults.Status.fromValue(reportNode.getPropertyAsString(PROP_STATUS));
                if (status == null) return null;
                final long testDate = reportNode.getProperty(PROP_EXECUTION_DATE).getDate().getTimeInMillis();
                final String workspace = reportNode.getPropertyAsString(PROP_WORKSPACE);
                final String errorsFileName = getErrorsFileName(reportNode.getName());
                final List<ContentIntegrityError> errors;
                if (reportNode.hasNode(errorsFileName)) {
                    final Set<String> fixedErrors = readFixedErrors(reportNode);
                    try (InputStream in = new GZIPInputStream(reportNode.getNode(errorsFileName).getFileContent().downloadFile())) {
                        errors = readErrors(in, checks, fixedErrors);
                    } catch (IOException e) {
                        logger.error("Impossible to read the errors of the results {}", resultsID, e);
                        return null;
                    }
                } else if (isOver(status) && status != ContentIntegrityResults.Status.FAILED) {
                    return null;
                } else {
                    errors = new ArrayList<>();
                }
                final List<String> log = Arrays.asList(StringUtils.split(StringUtils.defaultString(reportNode.getPropertyAsString(PROP_EXECUTION_LOG)), '\n'));
                final ContentIntegrityResults results = new ContentIntegrityResults(testDate, 0L, workspace, errors, new ArrayList<>(log)).setStatus(status);
                final JCRNodeIteratorWrapper files = reportNode.getNodes();
                while (files.hasNext()) {
                    final JCRNodeWrapper file = (JCRNodeWrapper) files.nextNode();
                    final String extension = StringUtils.substringAfterLast(file.getName(), ".");
                    if (file.isNodeType(Constants.JAHIANT_FILE) && !file.getName().equals(errorsFileName) && StringUtils.isNotBlank(extension)) {
                        results.addJcrReport(file.getName(), file.getPath(), extension);
                    }
                }
                return results;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to read the results {}", resultsID, e);
            return null;
        }
    }

    /**
     * The log of the scan of the stored results, read from their report node only: it is stored from the start of the
     * scan, so a scan which runs has the log of its last update.
     *
     * @return the lines of the log, or null if no results are stored with this identifier
     */
    public static List<String> loadExecutionLog(String resultsID) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final JCRNodeWrapper reportNode = findReportNode(session, resultsID);
                if (reportNode == null || !reportNode.hasProperty(PROP_STATUS)) return null;
                final String log = reportNode.hasProperty(PROP_EXECUTION_LOG) ? reportNode.getProperty(PROP_EXECUTION_LOG).getString() : StringUtils.EMPTY;
                return StringUtils.isEmpty(log) ? Collections.<String>emptyList() : Arrays.asList(StringUtils.splitPreserveAllTokens(log, '\n'));
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to read the log of the results {}", resultsID, e);
            return null;
        }
    }

    /**
     * The identifiers of the errors of the stored results fixed since they were stored.
     */
    public static Set<String> loadFixedErrors(String resultsID) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                final JCRNodeWrapper reportNode = findReportNode(session, resultsID);
                return reportNode == null ? Collections.<String>emptySet() : readFixedErrors(reportNode);
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to read the fixed errors of the results {}", resultsID, e);
            return Collections.emptySet();
        }
    }

    /**
     * Marks as interrupted the reports of the scans this server was running when it stopped.
     *
     * @return the number of reports marked as interrupted
     */
    public static int interruptAbandonedScans() {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                if (!session.nodeExists(REPORTS_FOLDER_PATH)) return 0;
                final String statement = String.format("SELECT * FROM [%s] AS report WHERE ISDESCENDANTNODE(report, '%s') AND report.[%s] = '%s' AND report.[%s] = '%s'",
                        REPORT_MIXIN, REPORTS_FOLDER_PATH, PROP_STATUS, ContentIntegrityResults.Status.RUNNING.getValue(), PROP_SERVER_ID, getServerId().replace("'", "''"));
                final JCRNodeIteratorWrapper nodes = session.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getNodes();
                int count = 0;
                while (nodes.hasNext()) {
                    ((JCRNodeWrapper) nodes.nextNode()).setProperty(PROP_STATUS, ContentIntegrityResults.Status.INTERRUPTED.getValue());
                    count++;
                }
                if (count > 0) session.save();
                return count;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to mark the abandoned scans as interrupted", e);
            return 0;
        }
    }

    /**
     * Deletes the reports of the scans started before the given date, whatever their format, except those still running.
     * The year and month folders left empty are deleted too.
     *
     * @return the number of deleted reports
     */
    public static int deleteReportsOlderThan(long date) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                if (!session.nodeExists(REPORTS_FOLDER_PATH)) return 0;
                final String statement = String.format("SELECT * FROM [%s] AS report WHERE ISDESCENDANTNODE(report, '%s') AND report.[%s] < CAST('%s' AS DATE)",
                        REPORT_MIXIN, REPORTS_FOLDER_PATH, PROP_EXECUTION_DATE, QUERY_DATE_FORMAT.format(date));
                final JCRNodeIteratorWrapper nodes = session.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2).execute().getNodes();
                final Set<JCRNodeWrapper> parents = new HashSet<>();
                int count = 0;
                while (nodes.hasNext()) {
                    final JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
                    if (ContentIntegrityResults.Status.RUNNING.getValue().equals(node.getPropertyAsString(PROP_STATUS))) continue;
                    parents.add(node.getParent());
                    node.remove();
                    count++;
                }
                for (JCRNodeWrapper monthFolder : parents) {
                    final JCRNodeWrapper yearFolder = monthFolder.getParent();
                    if (!monthFolder.getNodes().hasNext()) monthFolder.remove();
                    if (!yearFolder.getPath().equals(REPORTS_FOLDER_PATH) && !yearFolder.getNodes().hasNext()) yearFolder.remove();
                }
                if (count > 0) session.save();
                return count;
            });
        } catch (RepositoryException e) {
            logger.error("Impossible to delete the old reports", e);
            return 0;
        }
    }

    /**
     * The report node of the results, created with the folders of their year and month if missing.
     */
    static JCRNodeWrapper getOrCreateReportNode(JCRSessionWrapper session, ContentIntegrityResults results, String name) throws RepositoryException {
        final String path = String.format("%s/%s/%s", REPORTS_FOLDER_PATH, SPLIT_FORMAT.format(results.getTestDate()), name);
        if (session.nodeExists(path)) return session.getNode(path);

        final JCRNodeWrapper reportsFolder = JCRUtils.getOrCreateNode(session.getNode(Utils.REPORTS_FOLDER_PARENT_PATH), Utils.JCR_REPORTS_FOLDER_NAME, Constants.JAHIANT_FOLDER);
        Utils.restrictReportsFolderAccess(reportsFolder);
        final String splitConfig = FastDateFormat.getInstance("'constant,'yyyy';constant,'MM").format(results.getTestDate());
        final JCRNodeWrapper reportNode = JCRAutoSplitUtils.addNodeWithAutoSplitting(reportsFolder, name, Constants.JAHIANT_FOLDER, splitConfig, Constants.JAHIANT_FOLDER, null);
        reportNode.addMixin(Constants.JAHIAMIX_NOLIVE);
        reportNode.getParent().addMixin(Constants.JAHIAMIX_NOLIVE);
        reportNode.getParent().getParent().addMixin(Constants.JAHIAMIX_NOLIVE);
        reportNode.addMixin(REPORT_MIXIN);
        return reportNode;
    }

    private static JCRNodeWrapper findReportNode(JCRSessionWrapper session, String resultsID) throws RepositoryException {
        if (StringUtils.isBlank(resultsID)) return null;
        // The identifier ends with the date of the scan, which gives the folders of its year and month
        final String date = StringUtils.substringAfter(resultsID, "_");
        try {
            final String path = String.format("%s/%s/%s-full", REPORTS_FOLDER_PATH, SPLIT_FORMAT.format(new SimpleDateFormat(ID_DATE_PATTERN).parse(date)), resultsID);
            return session.nodeExists(path) ? session.getNode(path) : null;
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean hasErrorsFile(JCRNodeWrapper reportNode) throws RepositoryException {
        return reportNode.hasNode(getErrorsFileName(reportNode.getName()));
    }

    private static String getErrorsFileName(ContentIntegrityResults results) {
        return getErrorsFileName(results.getSignature(false));
    }

    private static String getErrorsFileName(String reportNodeName) {
        return reportNodeName + ERRORS_FILE_SUFFIX;
    }

    private static boolean isOver(ContentIntegrityResults.Status status) {
        return status != ContentIntegrityResults.Status.RUNNING;
    }

    private static boolean isBlockingImport(ContentIntegrityError error) {
        return error.getErrorType() != null && error.getErrorType().isBlockingImport();
    }

    private static Set<String> readFixedErrors(JCRNodeWrapper reportNode) throws RepositoryException {
        if (!reportNode.hasProperty(PROP_FIXED_ERRORS)) return new HashSet<>();
        final Set<String> fixedErrors = new HashSet<>();
        for (Value value : reportNode.getProperty(PROP_FIXED_ERRORS).getValues()) {
            fixedErrors.add(value.getString());
        }
        return fixedErrors;
    }

    private static Calendar toCalendar(long date) {
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(date);
        return calendar;
    }

    private static List<String> limitLog(List<String> log) {
        if (log.size() <= LOG_HEAD_SIZE + LOG_TAIL_SIZE + 1) return log;
        return Stream.of(log.subList(0, LOG_HEAD_SIZE), Collections.singletonList("[...]"), log.subList(log.size() - LOG_TAIL_SIZE, log.size()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    static void writeErrors(OutputStream out, List<ContentIntegrityError> errors) throws IOException {
        try (JsonGenerator json = JSON.createGenerator(out)) {
            json.writeStartObject();
            json.writeNumberField("formatVersion", FORMAT_VERSION);
            json.writeArrayFieldStart("errors");
            for (ContentIntegrityError error : errors) {
                json.writeStartObject();
                json.writeStringField("id", error.getErrorID());
                json.writeStringField("checkId", error.getIntegrityCheckID());
                json.writeStringField("checkName", error.getIntegrityCheckName());
                json.writeStringField("errorType", error.getErrorType() == null ? null : error.getErrorType().getKey());
                json.writeBooleanField("blockingImport", isBlockingImport(error));
                json.writeStringField("path", error.getPath());
                json.writeStringField("uuid", error.getUuid());
                json.writeStringField("primaryType", error.getPrimaryType());
                json.writeStringField("mixins", error.getMixins());
                json.writeStringField("locale", error.getLocale());
                json.writeStringField("workspace", error.getWorkspace());
                json.writeStringField("message", error.getConstraintMessage());
                json.writeBooleanField("fixed", error.isFixed());
                writeExtraInfos(json, error);
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }

    /**
     * The extra infos in the order the check added them, which is the order they are displayed in. Their values are
     * written as strings, numbers, booleans or arrays of strings: the checks read strings only.
     */
    private static void writeExtraInfos(JsonGenerator json, ContentIntegrityError error) throws IOException {
        final Map<String, Object> extraInfos = error.getAllExtraInfos();
        if (extraInfos == null || extraInfos.isEmpty()) return;
        final Set<String> specificKeys = error.getSpecificExtraInfos().keySet();
        json.writeArrayFieldStart("extraInfos");
        for (Map.Entry<String, Object> entry : extraInfos.entrySet()) {
            final Object value = entry.getValue();
            json.writeStartObject();
            json.writeStringField("key", entry.getKey());
            json.writeBooleanField("specific", specificKeys.contains(entry.getKey()));
            json.writeFieldName("value");
            if (value == null) json.writeNull();
            else if (value instanceof Boolean) json.writeBoolean((Boolean) value);
            else if (value instanceof Integer || value instanceof Long || value instanceof Short) json.writeNumber(((Number) value).longValue());
            else if (value instanceof Number) json.writeNumber(((Number) value).doubleValue());
            else if (value instanceof Collection || value instanceof Object[]) {
                json.writeStartArray();
                for (Object item : value instanceof Collection ? (Collection<?>) value : Arrays.asList((Object[]) value)) {
                    json.writeString(String.valueOf(item));
                }
                json.writeEndArray();
            } else json.writeString(value.toString());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    static List<ContentIntegrityError> readErrors(InputStream in, Function<String, ContentIntegrityCheck> checks, Set<String> fixedErrors) throws IOException {
        final List<ContentIntegrityError> errors = new ArrayList<>();
        try (JsonParser json = JSON.createParser(in)) {
            if (json.nextToken() != JsonToken.START_OBJECT) throw new IOException("Unexpected format of the stored errors");
            while (json.nextToken() == JsonToken.FIELD_NAME) {
                final String field = json.getCurrentName();
                json.nextToken();
                if ("errors".equals(field)) {
                    while (json.nextToken() == JsonToken.START_OBJECT) {
                        errors.add(readError(json, checks, fixedErrors));
                    }
                } else {
                    json.skipChildren();
                }
            }
        }
        return errors;
    }

    private static ContentIntegrityError readError(JsonParser json, Function<String, ContentIntegrityCheck> checks, Set<String> fixedErrors) throws IOException {
        final Map<String, String> fields = new HashMap<>();
        final Map<String, Object> extraInfos = new LinkedHashMap<>();
        final Set<String> specificKeys = new HashSet<>();
        boolean blockingImport = false;
        boolean fixed = false;
        while (json.nextToken() == JsonToken.FIELD_NAME) {
            final String field = json.getCurrentName();
            final JsonToken token = json.nextToken();
            switch (field) {
                case "extraInfos":
                    if (token == JsonToken.START_ARRAY) readExtraInfos(json, extraInfos, specificKeys);
                    else if (token.isStructStart()) json.skipChildren();
                    break;
                case "blockingImport":
                    blockingImport = token == JsonToken.VALUE_TRUE;
                    break;
                case "fixed":
                    fixed = token == JsonToken.VALUE_TRUE;
                    break;
                default:
                    if (token.isStructStart()) json.skipChildren();
                    else fields.put(field, token == JsonToken.VALUE_NULL ? null : json.getText());
            }
        }
        final String id = fields.get("id");
        final String checkId = fields.get("checkId");
        final ContentIntegrityErrorType errorType = resolveErrorType(checkId == null ? null : checks.apply(checkId), fields.get("errorType"), blockingImport);
        return ContentIntegrityErrorImpl.restoreError(id, fields.get("path"), fields.get("uuid"), fields.get("primaryType"), fields.get("mixins"),
                fields.get("workspace"), fields.get("locale"), errorType, fields.get("message"), fields.get("checkName"), checkId,
                extraInfos, specificKeys, fixed || fixedErrors.contains(id));
    }

    private static void readExtraInfos(JsonParser json, Map<String, Object> extraInfos, Set<String> specificKeys) throws IOException {
        while (json.nextToken() == JsonToken.START_OBJECT) {
            String key = null;
            Object value = null;
            boolean specific = false;
            while (json.nextToken() == JsonToken.FIELD_NAME) {
                final String field = json.getCurrentName();
                final JsonToken token = json.nextToken();
                if ("key".equals(field)) key = json.getText();
                else if ("specific".equals(field)) specific = token == JsonToken.VALUE_TRUE;
                else if ("value".equals(field)) value = readValue(json, token);
                else if (token.isStructStart()) json.skipChildren();
            }
            if (key == null) continue;
            extraInfos.put(key, value);
            if (specific) specificKeys.add(key);
        }
    }

    private static Object readValue(JsonParser json, JsonToken token) throws IOException {
        switch (token) {
            case START_ARRAY:
                final List<String> values = new ArrayList<>();
                while (json.nextToken() != JsonToken.END_ARRAY) {
                    values.add(json.getText());
                }
                return values;
            case VALUE_NUMBER_INT:
                return json.getLongValue();
            case VALUE_NUMBER_FLOAT:
                return json.getDoubleValue();
            case VALUE_TRUE:
            case VALUE_FALSE:
                return json.getBooleanValue();
            case VALUE_NULL:
                return null;
            case START_OBJECT:
                json.skipChildren();
                return null;
            default:
                return json.getText();
        }
    }

    /**
     * The checks compare the type of an error with their own constants, so the type is resolved to the constant its
     * check declares with this key. A type the check does not declare, or the type of an error of the framework, is
     * rebuilt from its key.
     */
    private static ContentIntegrityErrorType resolveErrorType(ContentIntegrityCheck check, String key, boolean blockingImport) {
        final ContentIntegrityErrorType frameworkError = ContentIntegrityErrorImpl.getFrameworkErrorType();
        if (frameworkError.getKey().equals(key)) return frameworkError;
        if (check != null) {
            final ContentIntegrityErrorType declared = ERROR_TYPES.computeIfAbsent(check.getClass(), ResultsStore::getDeclaredErrorTypes).get(key);
            if (declared != null) return declared;
        }
        return new ContentIntegrityErrorTypeImpl(StringUtils.defaultString(key), blockingImport);
    }

    private static Map<String, ContentIntegrityErrorType> getDeclaredErrorTypes(Class<?> checkClass) {
        final Map<String, ContentIntegrityErrorType> types = new HashMap<>();
        for (Class<?> c = checkClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !ContentIntegrityErrorType.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    final ContentIntegrityErrorType type = (ContentIntegrityErrorType) field.get(null);
                    if (type != null) types.putIfAbsent(type.getKey(), type);
                } catch (IllegalAccessException | RuntimeException e) {
                    logger.debug("Impossible to read the error type {} of {}", field.getName(), checkClass.getName(), e);
                }
            }
        }
        return types;
    }
}
