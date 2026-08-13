package com.finanzapp.app.util;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class CurrencyFormatter {
    public static String format(double amount, String currencyCode) {
        Locale locale;
        String code = currencyCode != null ? currencyCode : "EUR";
        
        switch (code) {
            case "USD": locale = Locale.US; break;
            case "GBP": locale = Locale.UK; break;
            default: locale = new Locale("es", "ES"); break;
        }
        
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        try {
            format.setCurrency(Currency.getInstance(code));
        } catch (Exception ignored) {}
        
        return format.format(amount);
    }
}
