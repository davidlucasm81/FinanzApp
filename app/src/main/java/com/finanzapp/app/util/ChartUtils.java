package com.finanzapp.app.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class ChartUtils {

    public static void setupBasicChart(Chart<?> chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setWordWrapEnabled(true);
        
        // Adapt legend color to dark mode
        int textColor = isDarkMode(chart.getContext()) ? Color.WHITE : Color.BLACK;
        chart.getLegend().setTextColor(textColor);
        
        chart.setTouchEnabled(true);
        chart.setNoDataText("No hay datos para este periodo");
        chart.setNoDataTextColor(textColor);

        // Animation for better UX
        chart.animateY(800);
    }

    public static void setupAxes(BarLineChartBase<?> chart) {
        int textColor = isDarkMode(chart.getContext()) ? Color.WHITE : Color.BLACK;
        int gridColor = isDarkMode(chart.getContext()) ? Color.DKGRAY : Color.LTGRAY;

        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);
        
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(gridColor);
        chart.getAxisLeft().setTextColor(textColor);
        chart.getAxisLeft().setAxisMinimum(0f);
    }

    public static void setupPieChart(PieChart chart) {
        setupBasicChart(chart);
        int textColor = isDarkMode(chart.getContext()) ? Color.WHITE : Color.BLACK;
        chart.setEntryLabelColor(textColor);
        chart.setCenterTextColor(textColor);
        chart.setHoleColor(Color.TRANSPARENT);
    }

    private static boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static class CurrencyFormatter extends ValueFormatter {
        private final String currencySymbol;

        public CurrencyFormatter(String currencySymbol) {
            this.currencySymbol = currencySymbol;
        }

        @Override
        public String getFormattedValue(float value) {
            return String.format("%.2f %s", value, currencySymbol);
        }
    }
}
