package com.finanzapp.app.data.importer;

import androidx.annotation.NonNull;

import com.finanzapp.app.data.model.ImportResult;
import com.finanzapp.app.data.model.ImportedRow;
import com.finanzapp.app.util.FirebaseLogger;
import com.google.firebase.Timestamp;
import com.google.firebase.perf.metrics.Trace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CsvTransactionParser {
    private static final String EXPECTED_HEADER = "Fecha Concepto Categoría Valor Tipo Método Cuenta";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public List<ImportedRow> parse(InputStream inputStream, ImportResult result) throws IOException {
        Trace trace = FirebaseLogger.startTrace("csv_parse");
        List<ImportedRow> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String headerLine = reader.readLine();
        if (headerLine == null) {
            result.addError("El fichero está vacío.");
            FirebaseLogger.stopTrace(trace);
            return rows;
        }

        String delimiter = detectDelimiter(headerLine);
        if (delimiter == null) {
            result.addError("No se ha podido detectar el delimitador (esperado: tabulador, coma o punto y coma).");
            FirebaseLogger.stopTrace(trace);
            return rows;
        }

        String line;
        int lineNumber = 2; // Line 1 was the header
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                lineNumber++;
                continue;
            }

            try {
                ImportedRow row = parseLine(line, delimiter, lineNumber);
                rows.add(row);
            } catch (ParseException e) {
                FirebaseLogger.logException(e);
                result.addError("Fila " + lineNumber + ": " + e.getMessage());
            }
            lineNumber++;
        }
        FirebaseLogger.stopTrace(trace);
        return rows;
    }

    private String detectDelimiter(String header) {
        if (header.contains("\t")) return "\t";
        if (header.contains(",")) return ",";
        if (header.contains(";")) return ";";
        return null;
    }

    private ImportedRow parseLine(String line, String delimiter, int lineNumber) throws ParseException {
        List<String> tokens = splitRespectingQuotes(line, delimiter.charAt(0));
        if (tokens.size() < 7) {
            throw new ParseException("Número de columnas insuficiente (esperadas 7).", 0);
        }

        String dateStr = tokens.get(0).trim();
        String description = tokens.get(1).trim();
        String categoryName = tokens.get(2).trim();
        String valueStr = tokens.get(3).trim();
        String typeStr = tokens.get(4).trim();
        String methodStr = tokens.get(5).trim();
        String accountName = tokens.get(6).trim();

        Date date;
        try {
            date = DATE_FORMAT.parse(dateStr);
        } catch (ParseException e) {
            throw new ParseException("Formato de fecha inválido (esperado dd/MM/yyyy).", 0);
        }

        double amount;
        try {
            // Clean currency symbols, quotes, thousands separators and handle decimal comma
            String cleanedValue = getString(valueStr);

            amount = Math.abs(Double.parseDouble(cleanedValue));
        } catch (NumberFormatException e) {
            throw new ParseException("Valor numérico inválido: " + valueStr, 0);
        }

        String type;
        if (typeStr.equalsIgnoreCase("Ingreso")) {
            type = "income";
        } else if (typeStr.equalsIgnoreCase("Gasto")) {
            type = "expense";
        } else {
            throw new ParseException("Tipo desconocido (esperado: Ingreso o Gasto).", 0);
        }

        String paymentMethod = mapPaymentMethod(methodStr);
        if (paymentMethod == null) {
            throw new ParseException("Método de pago no reconocido: " + methodStr, 0);
        }

        if (description.isEmpty()) {
            description = "Importado sin descripción";
        }

        return new ImportedRow(new Timestamp(Objects.requireNonNull(date)), description, categoryName, amount, type, paymentMethod, accountName, lineNumber);
    }

    @NonNull
    private static String getString(String valueStr) {
        String cleanedValue = valueStr.replace("\"", "")
                .replace("€", "")
                .replace("$", "")
                .replace(".", "") // Assume dot is thousands separator if comma is present later
                .replace(",", ".") // Replace decimal comma with dot
                .trim();

        // Re-check: if there were no commas but dots, the previous replace(".") might have broken it
        // if it was "289.06". Let's use a more defensive approach.
        if (!valueStr.contains(",") && valueStr.contains(".")) {
            cleanedValue = valueStr.replace("\"", "").replace("€", "").replace("$", "").trim();
        }
        return cleanedValue;
    }

    private List<String> splitRespectingQuotes(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().replace("\"", ""));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().replace("\"", ""));
        return tokens;
    }

    private String mapPaymentMethod(String method) {
        // Normalización básica: quitar acentos y pasar a minúsculas
        String normalized = method.toLowerCase().replaceAll("[áàäâ]", "a").replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i").replaceAll("[óòöô]", "o").replaceAll("[úùüû]", "u");

        if (normalized.contains("tarjeta")) return "tarjeta";
        if (normalized.contains("efectivo")) return "efectivo";
        if (normalized.contains("transferencia")) return "transferencia";
        if (normalized.contains("bizum")) return "bizum";
        if (normalized.contains("restaurante")) return "tarjeta_restaurante";
        if (normalized.contains("transporte")) return "tarjeta_transporte";
        if (normalized.contains("domiciliacion")) return "domiciliacion_bancaria";

        return null;
    }
}
