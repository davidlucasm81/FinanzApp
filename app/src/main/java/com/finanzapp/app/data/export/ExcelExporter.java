package com.finanzapp.app.data.export;

import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.util.FirebaseLogger;
import com.google.firebase.perf.metrics.Trace;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExcelExporter {

    public static void exportTransactions(List<Transaction> transactions,
                                          Map<String, String> categoryNames,
                                          Map<String, String> accountNames,
                                          Map<String, String> memberNames,
                                          Map<String, String> paymentMethodLabels,
                                          OutputStream outputStream) throws IOException {
        Trace trace = FirebaseLogger.startTrace("export_excel");
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Movimientos");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Fecha", "Concepto", "Categoría", "Importe", "Tipo", "Método", "Cuenta", "Registrado por"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            // Data rows
            int rowIdx = 1;
            for (Transaction t : transactions) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "");
                row.createCell(1).setCellValue(t.getDescription());
                row.createCell(2).setCellValue(categoryNames.getOrDefault(t.getCategoryId(), t.getCategoryId()));
                
                Cell amountCell = row.createCell(3);
                amountCell.setCellValue(t.getAmount());
                
                row.createCell(4).setCellValue("income".equals(t.getType()) ? "Ingreso" : "Gasto");
                row.createCell(5).setCellValue(paymentMethodLabels.getOrDefault(t.getPaymentMethod(), t.getPaymentMethod()));
                row.createCell(6).setCellValue(accountNames.getOrDefault(t.getAccountId(), t.getAccountId()));
                row.createCell(7).setCellValue(memberNames.getOrDefault(t.getCreatedBy(), t.getCreatedBy()));
            }

            // Auto-size columns is not supported on Android because it depends on AWT
            workbook.write(outputStream);
            workbook.close();
        } finally {
            FirebaseLogger.stopTrace(trace);
        }
    }
}
