package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final String JCR_REPORTS_FOLDER_NAME = "content-integrity-reports";
    private static final String DEFAULT_CSV_HEADER = "Check ID,Fixed,Error type,Workspace,Node identifier,Node path,Node primary type,Node mixins,Locale,Error message,Extra information";

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

    public static void log(String message, LOG_LEVEL logLevel, Logger log, Throwable t, ExternalLogger... externalLoggers) {
        if (log != null) {
            switch (logLevel) {
                case TRACE:
                    if (t == null) {
                        log.trace(message);
                    } else {
                        log.trace(message, t);
                    }
                    break;
                case INFO:
                    if (t == null) {
                        log.info(message);
                    } else {
                        log.info(message, t);
                    }
                    break;
                case WARN:
                    if (t == null) {
                        log.warn(message);
                    } else {
                        log.warn(message, t);
                    }
                    break;
                case ERROR:
                    if (t == null) {
                        log.error(message);
                    } else {
                        log.error(message, t);
                    }
                    break;
                case DEBUG:
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
        if (CollectionUtils.isEmpty(whiteList) && CollectionUtils.isEmpty(blackList)) {
            return null;
        }

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
        }
        return service.getContentIntegrityChecksIdentifiers(true).stream().filter(id -> !blackList.contains(id)).collect(Collectors.toList());
    }

    public static String writeDumpInTheJCR(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader, ExternalLogger externalLogger, boolean uploadDump) {
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

                    File report = generateReport(results, excludeFixedErrors, noCsvHeader);
                    if (report != null && uploadDump) {
                        try {
                            final JCRNodeWrapper reportNode = outputDir.uploadFile(report.getName(), new FileInputStream(report), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                            session.save();
                            final String reportPath = reportNode.getPath();
                            externalLogger.logLine("Written the report in " + reportPath);
                            FileUtils.deleteQuietly(report);
                            return reportPath;
                        } catch (FileNotFoundException e) {
                            logger.error("", e);
                        }
                    }
                    return null;
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
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

    private static File generateReport(ContentIntegrityResults results, boolean excludeFixedErrors, boolean noCsvHeader) {
        Workbook wb = new XSSFWorkbook();
        XSSFSheet sheet = (XSSFSheet) wb.createSheet(WorkbookUtil.createSafeSheetName("Data"));

        String firstColName = null;
        int rowNum = 0;
        Row row;
        // Add header
        if (!noCsvHeader) {
            row = sheet.createRow((short) rowNum++);
            String[] colNames = SettingsBean.getInstance().getString("modules.contentIntegrity.csv.header", DEFAULT_CSV_HEADER).split(",");
            int nbColumns = colNames.length;
            if (nbColumns > 0) {
                firstColName = colNames[0];
            }
            for (int i = 0; i < nbColumns; i++) {
                row.createCell(i).setCellValue(colNames[i]);
            }
        }

        List<String> content = results.getErrors().stream()
                .map(ContentIntegrityError::toCSV)
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(content)) {
            int nbFields;
            for (String data : content) {
                String[] fields = data.split(",");
                nbFields = fields.length;
                row = sheet.createRow((short) rowNum++);
                for (int i = 0; i < nbFields; i++) {
                    row.createCell(i).setCellValue(fields[i]);
                }
            }
        }

        int firstRow = sheet.getFirstRowNum();
        int lastCol = sheet.getRow(0).getLastCellNum();

        for (int i = firstRow; i < lastCol; i++) {
            sheet.autoSizeColumn(i);
        }

        XSSFPivotTable pivotTable = ((XSSFSheet) wb.createSheet(WorkbookUtil.createSafeSheetName("Analysis"))).createPivotTable(
                new AreaReference(new CellReference(firstRow, sheet.getRow(0).getFirstCellNum()), new CellReference(sheet.getLastRowNum(), lastCol - 1), SpreadsheetVersion.EXCEL2007),
                new CellReference(1, 1), sheet);
        // Check ID
        pivotTable.addRowLabel(0);
        // Workspace
        pivotTable.addRowLabel(3);
        // Error message
        pivotTable.addRowLabel(9);
        // Node primary type
        pivotTable.addRowLabel(6);
        // Extra information
        pivotTable.addRowLabel(10);
        // Node path
        pivotTable.addRowLabel(5);
        // Count data
        if (StringUtils.isNotEmpty(firstColName)) {
            pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 2, "Count of " + firstColName);
        } else {
            pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 2, "Count");
        }

        try {
            final File outputDir = new File(System.getProperty("java.io.tmpdir"), "content-integrity");
            if ((outputDir.exists() || outputDir.mkdirs()) && outputDir.canWrite()) {
                File report = new File(outputDir, String.format("%s-%s.xlsx", results.getID(), excludeFixedErrors ? "remainingErrors" : "full"));
                FileOutputStream fileOut = new FileOutputStream(report);
                wb.write(fileOut);
                IOUtils.closeQuietly(fileOut);
                return report;
            }
        } catch (IOException e) {
            logger.error("", e);
        }
        return null;
    }
}
