package com.finanzapp.app.util;

import java.util.HashMap;
import java.util.Map;

public class CategoryColorPalette {

    // Paleta fija de 33 colores (28 de gasto + 5 de ingreso)
    public static final String[] COLORS = {
        "#2E7D32", // Nómina
        "#558B2F", // Otros ingresos
        "#00897B", // Ingresos extra / Freelance
        "#00695C", // Alquileres (ingreso)
        "#43A047", // Devoluciones / Reembolsos
        "#6D4C41", // Hipoteca
        "#8D6E63", // Reformas
        "#455A64", // Servicios
        "#1E88E5", // Internet
        "#3949AB", // Seguros
        "#F4511E", // Supermercado
        "#FB8C00", // Restaurantes
        "#6A1B9A", // Alcohol
        "#039BE5", // Transporte
        "#E53935", // Salud
        "#D81B60", // Ropa
        "#5E35B1", // Educación
        "#F9A825", // Ocio
        "#00ACC1", // Viajes
        "#7CB342", // Ahorros
        "#546E7A", // Informática
        "#8E24AA", // Libros
        "#C2185B", // Streaming
        "#26A69A", // Deporte
        "#FFB300", // Bebidas
        "#EC407A", // Peluquería
        "#AB47BC", // Regalos
        "#A1887F", // Hogar
        "#78909C", // Misceláneo
        "#C62828", // Impuestos
        "#4527A0", // Comunidad
        "#FF7043", // Mascotas
        "#66BB6A"  // Donaciones
    };

    private static final Map<String, String> DEFAULT_CATEGORY_COLORS = new HashMap<>();

    static {
        // Ingresos
        DEFAULT_CATEGORY_COLORS.put("Nómina", "#2E7D32");
        DEFAULT_CATEGORY_COLORS.put("Otros ingresos", "#558B2F");
        DEFAULT_CATEGORY_COLORS.put("Ingresos extra / Freelance", "#00897B");
        DEFAULT_CATEGORY_COLORS.put("Alquileres (ingreso)", "#00695C");
        DEFAULT_CATEGORY_COLORS.put("Devoluciones / Reembolsos", "#43A047");

        // Gastos
        DEFAULT_CATEGORY_COLORS.put("Hipoteca", "#6D4C41");
        DEFAULT_CATEGORY_COLORS.put("Reformas", "#8D6E63");
        DEFAULT_CATEGORY_COLORS.put("Servicios", "#455A64");
        DEFAULT_CATEGORY_COLORS.put("Internet", "#1E88E5");
        DEFAULT_CATEGORY_COLORS.put("Seguros", "#3949AB");
        DEFAULT_CATEGORY_COLORS.put("Supermercado", "#F4511E");
        DEFAULT_CATEGORY_COLORS.put("Restaurantes", "#FB8C00");
        DEFAULT_CATEGORY_COLORS.put("Alcohol", "#6A1B9A");
        DEFAULT_CATEGORY_COLORS.put("Transporte", "#039BE5");
        DEFAULT_CATEGORY_COLORS.put("Salud", "#E53935");
        DEFAULT_CATEGORY_COLORS.put("Ropa", "#D81B60");
        DEFAULT_CATEGORY_COLORS.put("Educación", "#5E35B1");
        DEFAULT_CATEGORY_COLORS.put("Ocio", "#F9A825");
        DEFAULT_CATEGORY_COLORS.put("Viajes", "#00ACC1");
        DEFAULT_CATEGORY_COLORS.put("Ahorros", "#7CB342");
        DEFAULT_CATEGORY_COLORS.put("Informática", "#546E7A");
        DEFAULT_CATEGORY_COLORS.put("Libros", "#8E24AA");
        DEFAULT_CATEGORY_COLORS.put("Streaming", "#C2185B");
        DEFAULT_CATEGORY_COLORS.put("Deporte", "#26A69A");
        DEFAULT_CATEGORY_COLORS.put("Bebidas", "#FFB300");
        DEFAULT_CATEGORY_COLORS.put("Peluquería", "#EC407A");
        DEFAULT_CATEGORY_COLORS.put("Regalos", "#AB47BC");
        DEFAULT_CATEGORY_COLORS.put("Hogar", "#A1887F");
        DEFAULT_CATEGORY_COLORS.put("Misceláneo", "#78909C");
        DEFAULT_CATEGORY_COLORS.put("Impuestos", "#C62828");
        DEFAULT_CATEGORY_COLORS.put("Comunidad", "#4527A0");
        DEFAULT_CATEGORY_COLORS.put("Mascotas", "#FF7043");
        DEFAULT_CATEGORY_COLORS.put("Donaciones", "#66BB6A");
    }

    public static String getColorForCategory(String categoryName) {
        return DEFAULT_CATEGORY_COLORS.getOrDefault(categoryName, COLORS[Math.abs(categoryName.hashCode()) % COLORS.length]);
    }
}
