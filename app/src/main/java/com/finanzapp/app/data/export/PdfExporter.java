package com.finanzapp.app.data.export;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import com.finanzapp.app.data.model.Transaction;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PdfExporter {

    public static void exportTransactions(List<Transaction> transactions,
                                          String familyName,
                                          Map<String, String> categoryNames,
                                          Map<String, String> accountNames,
                                          Map<String, String> memberNames,
                                          Map<String, String> paymentMethodLabels,
                                          OutputStream outputStream) throws IOException {
        
        PdfDocument document = new PdfDocument();
        
        // A4 Size: 595 x 842 points
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 40;
        
        Paint titlePaint = new Paint();
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(18);
        titlePaint.setColor(Color.BLACK);
        
        Paint headerPaint = new Paint();
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setTextSize(10);
        headerPaint.setColor(Color.BLACK);
        
        Paint textPaint = new Paint();
        textPaint.setTextSize(9);
        textPaint.setColor(Color.BLACK);

        Paint headerBgPaint = new Paint();
        headerBgPaint.setColor(Color.LTGRAY);
        headerBgPaint.setStyle(Paint.Style.FILL);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        
        int itemsPerPage = 45;
        int totalItems = transactions.size();
        int pageCount = (int) Math.ceil((double) totalItems / itemsPerPage);
        if (pageCount == 0) pageCount = 1;
        
        for (int p = 0; p < pageCount; p++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            
            int yPosition = margin;
            
            // Title
            canvas.drawText("Listado de Movimientos - " + (familyName != null ? familyName : ""), margin, yPosition, titlePaint);
            yPosition += 30;
            
            // Headers
            int[] columnWidths = {60, 120, 80, 60, 40, 70, 70};
            String[] headers = {"Fecha", "Concepto", "Categoría", "Importe", "Tipo", "Método", "Cuenta"};
            int xPosition = margin;
            
            canvas.drawRect(margin, yPosition - 15, pageWidth - margin, yPosition + 5, headerBgPaint);
            
            for (int i = 0; i < headers.length; i++) {
                canvas.drawText(headers[i], xPosition, yPosition, headerPaint);
                xPosition += columnWidths[i];
            }
            yPosition += 20;
            
            int startIdx = p * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, totalItems);
            
            for (int i = startIdx; i < endIdx; i++) {
                Transaction t = transactions.get(i);
                xPosition = margin;
                
                // Fecha
                canvas.drawText(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "", xPosition, yPosition, textPaint);
                xPosition += columnWidths[0];
                
                // Concepto
                String desc = t.getDescription();
                if (desc != null && desc.length() > 22) desc = desc.substring(0, 19) + "...";
                canvas.drawText(desc != null ? desc : "", xPosition, yPosition, textPaint);
                xPosition += columnWidths[1];
                
                // Categoría
                String cat = categoryNames.getOrDefault(t.getCategoryId(), "");
                if (cat != null && cat.length() > 15) cat = cat.substring(0, 12) + "...";
                canvas.drawText(cat != null ? cat : "", xPosition, yPosition, textPaint);
                xPosition += columnWidths[2];
                
                // Importe
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", t.getAmount()), xPosition, yPosition, textPaint);
                xPosition += columnWidths[3];
                
                // Tipo
                canvas.drawText("income".equals(t.getType()) ? "Ing" : "Gas", xPosition, yPosition, textPaint);
                xPosition += columnWidths[4];
                
                // Método
                String method = paymentMethodLabels.getOrDefault(t.getPaymentMethod(), "");
                if (method != null && method.length() > 12) method = method.substring(0, 9) + "...";
                canvas.drawText(method != null ? method : "", xPosition, yPosition, textPaint);
                xPosition += columnWidths[5];
                
                // Cuenta
                String acc = accountNames.getOrDefault(t.getAccountId(), "");
                if (acc != null && acc.length() > 12) acc = acc.substring(0, 9) + "...";
                canvas.drawText(acc != null ? acc : "", xPosition, yPosition, textPaint);
                
                yPosition += 15;
            }
            
            canvas.drawText("Página " + (p + 1) + " de " + pageCount, pageWidth / 2f - 20, pageHeight - 20, textPaint);
            
            document.finishPage(page);
        }
        
        document.writeTo(outputStream);
        document.close();
    }
}
