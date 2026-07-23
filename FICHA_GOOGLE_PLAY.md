# Ficha de Google Play — FinanzApp

> Borrador de los textos que pide la Play Console en "Presencia en la
> tienda" → "Ficha principal de la tienda". Ajusta longitudes exactas si
> Google las cambia; los límites actuales son: título 30 caracteres,
> descripción breve 80 caracteres, descripción completa 4000 caracteres.

## ✅ Checklist — qué queda listo aquí y qué debe rellenar el humano

Textos (título, descripción breve, descripción completa, categoría, chuleta
de "Seguridad de los datos") y assets gráficos de icono ya están cerrados en
este documento y en `icon_assets/` (ver Fase 10 de `PLAN_DESARROLLO.md`).
Queda pendiente, y **no se puede completar sin intervención humana**:

- [ ] **Email de contacto** de la ficha (sección "Datos de contacto" más
  abajo). Debe ser un email que el propietario revise, no uno genérico. davidlucasmora81@gmail.com
- [ ] **URL pública de la Política de Privacidad**. Antes hace falta:
  1. Completar los huecos `[COMPLETAR]` de `POLITICA_PRIVACIDAD.md` (razón
     social/NIF/dirección, región de Firestore, fecha de publicación) —
     David (desarrollador individual), David (desarrollador individual), David (desarrollador individual), Europa (España), 2024-07-23.
  2. Publicar ese Markdown como página web (GitHub Pages, Firebase Hosting
     capa gratuita, o Google Sites) y pegar aquí la URL resultante.
- [ ] **Capturas de pantalla reales** (mínimo 2, recomendado 4-8): Dashboard,
  Movimientos, Estadísticas, Selector de familias. Deben salir de la app
  ejecutándose en un emulador/dispositivo, no se pueden generar aquí.
- [ ] **Cuestionario de clasificación de contenido (IARC)** en Play Console:
  hay que rellenarlo dentro de la propia consola; la clasificación de esta
  ficha ("Para todos los públicos") es la expectativa, no el resultado
  garantizado.
- [ ] **Cuenta de desarrollador de Google Play** (pago único de registro) y
  aceptar los acuerdos de distribución vigentes en la consola.
- [x] **Icono de alta resolución 512×512** y **feature graphic 1024×500**:
  generados en `icon_assets/play_store_icon_512.png` y
  `icon_assets/feature_graphic_1024x500.png` (ver Fase 10). El feature
  graphic es un borrador funcional; sustitúyelo si un diseñador prepara
  algo más elaborado más adelante.

## Título (máx. 30 caracteres)
FinanzApp — Finanzas familiares
(30 caracteres exactos; alternativa más corta: "FinanzApp Familiar", 19)

## Descripción breve (máx. 80 caracteres)
Gestiona los ingresos y gastos de tu familia, todos a la vez y en tiempo real.
(79 caracteres)

## Descripción completa (máx. 4000 caracteres)
FinanzApp es la forma más sencilla de llevar las finanzas de tu familia en
equipo, sin hojas de cálculo ni apps que solo sirven para una persona.

CREA TU UNIDAD FAMILIAR
Invita a los tuyos por email o compartiendo un código, y ved todos las
mismas cuentas y movimientos en tiempo real, se actualice quien se actualice.

CUENTAS Y POSICIÓN NETA
Da de alta tantas cuentas como necesites y consulta de un vistazo cuánto
tiene la familia en conjunto.

MOVIMIENTOS ORGANIZADOS
Registra ingresos y gastos con categorías personalizables (color y nombre a
tu gusto), método de pago y, si lo necesitas, importa movimientos completos
desde un fichero CSV de tu banco.

ESTADÍSTICAS CLARAS
Evolución mensual de ingresos y gastos, variación respecto al mes anterior
y distribución del gasto por categorías, con gráficos interactivos.

VARIAS FAMILIAS, UN SOLO USUARIO
¿Compartes gastos con tu pareja y también llevas las cuentas de otra unidad
familiar? Cambia entre familias sin salir de la app.

PRIVACIDAD POR DISEÑO
Tus datos solo son visibles para los miembros de tu propia familia. Puedes
descargar en cualquier momento un archivo con tus datos personales, y
eliminar tu cuenta cuando quieras.

SIN ANUNCIOS, SIN LETRA PEQUEÑA
FinanzApp no vende tus datos ni los usa con fines publicitarios.

## Categoría
Finanzas

## Datos de contacto
- Email: davidlucasmora81@gmail.com
- Sitio web / política de privacidad: [COMPLETAR URL pública una vez publicada, necesaria para el formulario de Play Console]

## Clasificación de contenido
Deberás rellenar el cuestionario de IARC en Play Console. Al no incluir
contenido violento, sexual ni de terceros, debería resultar en "Para todos los públicos" (PEGI 3), pero la clasificación final la decide el cuestionario.

## Sección "Seguridad de los datos" (Data safety) de Play Console
Resumen para rellenar el formulario (no es el texto final, es la chuleta
de qué marcar):
- Datos recogidos: nombre, email, foto de perfil (identidad); datos
  financieros que el propio usuario introduce (importes, categorías).
- ¿Se comparten con terceros?: no, salvo el proveedor de infraestructura
  (Firebase/Google Cloud) como encargado del tratamiento.
- ¿Cifrado en tránsito?: sí.
- ¿El usuario puede pedir borrado de datos?: sí, desde la propia app.

## Capturas de pantalla requeridas
- Al menos 2 capturas de teléfono (recomendado 4-8): Dashboard, Movimientos,
  Estadísticas, Selector de familias.
- Icono de alta resolución 512×512 px (32 bits PNG, con canal alfa).
- Gráfico de la ficha (feature graphic) 1024×500 px.

## Textos legales que Google exige enlazar
- Política de Privacidad pública (URL, no puede ser solo un fichero dentro
  del repo): puedes publicarla gratis con GitHub Pages, Firebase Hosting
  (capa gratuita) o Google Sites.