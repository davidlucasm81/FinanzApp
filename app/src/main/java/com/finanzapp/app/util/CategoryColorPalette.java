package com.finanzapp.app.util;

import java.util.HashMap;
import java.util.Map;

public class CategoryColorPalette {

    // Nueva paleta de colores para las 25 categorías solicitadas (2026-07-21)
    public static final String[] COLORS = {
        "#2E7D32", // Nomina
        "#4527A0", // Comunidad
        "#26A69A", // Deporte
        "#FFB300", // Desayunos / Bar
        "#5E35B1", // Educacion
        "#455A64", // Gas
        "#6D4C41", // Hipoteca
        "#C62828", // Impuestos
        "#546E7A", // Informática / Telefonía
        "#1E88E5", // Internet
        "#FBC02D", // Luz
        "#78909C", // Misceláneo
        "#A1887F", // Mobiliario
        "#F9A825", // Ocio
        "#EC407A", // Peluqueria
        "#8D6E63", // Reformas
        "#AB47BC", // Regalos
        "#FB8C00", // Restaurantes
        "#D81B60", // Ropa
        "#E53935", // Salud
        "#3949AB", // Seguros
        "#C2185B", // Streaming
        "#F4511E", // Supermercado / Tiendas
        "#039BE5", // Transporte
        "#00ACC1"  // Viajes
    };

    private static final Map<String, String> DEFAULT_CATEGORY_COLORS = new HashMap<>();

    static {
        DEFAULT_CATEGORY_COLORS.put("Nomina", "#2E7D32");
        DEFAULT_CATEGORY_COLORS.put("Comunidad", "#4527A0");
        DEFAULT_CATEGORY_COLORS.put("Deporte", "#26A69A");
        DEFAULT_CATEGORY_COLORS.put("Desayunos / Bar", "#FFB300");
        DEFAULT_CATEGORY_COLORS.put("Educacion", "#5E35B1");
        DEFAULT_CATEGORY_COLORS.put("Gas", "#455A64");
        DEFAULT_CATEGORY_COLORS.put("Hipoteca", "#6D4C41");
        DEFAULT_CATEGORY_COLORS.put("Impuestos", "#C62828");
        DEFAULT_CATEGORY_COLORS.put("Informática / Telefonía", "#546E7A");
        DEFAULT_CATEGORY_COLORS.put("Internet", "#1E88E5");
        DEFAULT_CATEGORY_COLORS.put("Luz", "#FBC02D");
        DEFAULT_CATEGORY_COLORS.put("Misceláneo", "#78909C");
        DEFAULT_CATEGORY_COLORS.put("Mobiliario", "#A1887F");
        DEFAULT_CATEGORY_COLORS.put("Ocio", "#F9A825");
        DEFAULT_CATEGORY_COLORS.put("Peluqueria", "#EC407A");
        DEFAULT_CATEGORY_COLORS.put("Reformas", "#8D6E63");
        DEFAULT_CATEGORY_COLORS.put("Regalos", "#AB47BC");
        DEFAULT_CATEGORY_COLORS.put("Restaurantes", "#FB8C00");
        DEFAULT_CATEGORY_COLORS.put("Ropa", "#D81B60");
        DEFAULT_CATEGORY_COLORS.put("Salud", "#E53935");
        DEFAULT_CATEGORY_COLORS.put("Seguros", "#3949AB");
        DEFAULT_CATEGORY_COLORS.put("Streaming", "#C2185B");
        DEFAULT_CATEGORY_COLORS.put("Supermercado / Tiendas", "#F4511E");
        DEFAULT_CATEGORY_COLORS.put("Transporte", "#039BE5");
        DEFAULT_CATEGORY_COLORS.put("Viajes", "#00ACC1");
    }

    public static String getColorForCategory(String categoryName) {
        return DEFAULT_CATEGORY_COLORS.getOrDefault(categoryName, COLORS[Math.abs(categoryName.hashCode()) % COLORS.length]);
    }
}
