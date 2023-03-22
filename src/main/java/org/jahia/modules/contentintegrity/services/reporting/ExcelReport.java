package org.jahia.modules.contentintegrity.services.reporting;

import org.apache.commons.collections4.CollectionUtils;
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
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExcelReport extends Report {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReport.class);

    private static final String MAIN_SHEET_NAME = "Data";

    @Override
    public String getFileExtension() {
        return "xlsx";
    }

    @Override
    public String getFileContentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public void write(OutputStream stream, ContentIntegrityResults results, boolean withColumnHeaders, boolean excludeFixedErrors) throws IOException {
        final Workbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = (XSSFSheet) wb.createSheet(WorkbookUtil.createSafeSheetName(MAIN_SHEET_NAME));

        int rowNum = 0;
        Row row;
        // Add header
        if (withColumnHeaders) {
            row = sheet.createRow((short) rowNum++);
            final String[] colNames = getColumns().toArray(new String[0]);
            final int nbColumns = colNames.length;
            for (int i = 0; i < nbColumns; i++) {
                row.createCell(i).setCellValue(colNames[i]);
            }
        }

        final List<List<String>> content = getReportContent(results, excludeFixedErrors);
        if (CollectionUtils.isNotEmpty(content)) {
            for (List<String> data : content) {
                row = sheet.createRow((short) rowNum++);
                for (int i = 0; i < data.size(); i++) {
                    row.createCell(i).setCellValue(data.get(i));
                }
            }
        }

        final int firstRow = sheet.getFirstRowNum();
        final int lastCol = sheet.getRow(0).getLastCellNum();

        for (int i = firstRow; i < lastCol; i++) {
            sheet.autoSizeColumn(i);
        }

        final XSSFPivotTable pivotTable = ((XSSFSheet) wb.createSheet(WorkbookUtil.createSafeSheetName("Analysis"))).createPivotTable(
                new AreaReference(new CellReference(firstRow, sheet.getRow(0).getFirstCellNum()), new CellReference(sheet.getLastRowNum(), lastCol - 1), SpreadsheetVersion.EXCEL2007),
                new CellReference(1, 1), sheet);
        // Check ID
        pivotTable.addRowLabel(0);
        // Error message
        pivotTable.addRowLabel(10);
        // Workspace
        pivotTable.addRowLabel(3);
        // Site
        pivotTable.addRowLabel(6);
        // Count data
        pivotTable.addColumnLabel(DataConsolidateFunction.COUNT, 2, "Count");

        wb.setSheetOrder(sheet.getSheetName(), wb.getNumberOfSheets() - 1);

        try {
            wb.write(stream);
        } catch (IOException e) {
            logger.error("", e);
        }
    }

    private List<List<String>> getReportContent(ContentIntegrityResults results, boolean excludeFixedErrors) {
        return results.getErrors().stream()
                .filter(error -> !excludeFixedErrors || !error.isFixed())
                .map(Report::toTextElementsList)
                .collect(Collectors.toList());
    }
}
