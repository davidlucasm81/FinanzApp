# PLAN_DESARROLLO.md — FinanzApp

> Plan de implementación paso a paso. Cada fase debe dejar el proyecto compilando y, si aplica, ejecutándose en un emulador/dispositivo antes de pasar a la siguiente. Marca cada tarea con `[x]` al completarla. Consulta `AGENTS.md` para contexto de arquitectura y modelo de datos antes de cada fase.

## Fase 0 — Preparación del entorno
- [x] Crear el proyecto en Android Studio: plantilla "Empty Views Activity", lenguaje Java, `applicationId` = `com.finanzapp.app` (o el que se decida finalmente).
- [x] Configurar `minSdk` 26 y `targetSdk` al valor por defecto sugerido por Android Studio.
- [x] Inicializar git (`git init`) y crear `.gitignore` (ver sección 6 de `AGENTS.md`).
- [x] Primer commit: proyecto vacío compilando.
- [ ] (Acción manual del humano) Crear proyecto en Firebase Console, registrar la app Android con el `applicationId` elegido y el SHA-1 de depuración.
- [ ] (Acción manual del humano) Descargar `google-services.json` y colocarlo en `app/`; verificar que **no** aparece en `git status`.
- [x] Crear `google-services.json.example` con estructura ficticia y documentar en `README.md` cómo obtener el propio.
- [x] Añadir el plugin `com.google.gms.google-services` y el Firebase BoM más reciente al `build.gradle` (proyecto y módulo `app`).
- [ ] (Acción manual del humano) En Firebase Console, habilitar Authentication → proveedor Google.
- [ ] (Acción manual del humano) Crear la base de datos Cloud Firestore (modo producción, elegir región cercana).

## Fase 1 — Autenticación (Firebase Auth + Google vía Credential Manager)
- [ ] Añadir dependencias: `firebase-auth`, `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`.
- [ ] Implementar `LoginActivity` con botón "Continuar con Google".
- [ ] Configurar `GetGoogleIdOption` usando el **Web Client ID** del proyecto de Firebase (no el Android client ID).
- [ ] Implementar el flujo: `CredentialManager.getCredential()` → extraer `idToken` → `GoogleAuthProvider.getCredential(idToken, null)` → `FirebaseAuth.signInWithCredential()`.
- [ ] Manejar la cancelación del usuario (sin mostrar error) y los errores reales (mostrar mensaje).
- [ ] En el primer login, crear/actualizar `users/{uid}` en Firestore (`displayName`, `email`, `photoUrl`, `familyId: null`, `createdAt`).
- [ ] Añadir pantalla splash/loading que decida a dónde navegar: login → onboarding (si `familyId == null`) → home (si ya tiene familia).
- [ ] Implementar cierre de sesión (`signOut`, limpiar Credential Manager, volver a `LoginActivity`).

## Fase 2 — Onboarding: crear o unirse a una unidad familiar
- [ ] Pantalla de bienvenida: "Crear una familia" / "Unirme a una familia".
- [ ] **Crear familia**: formulario con nombre + selector de moneda (lista corta de ISO 4217 comunes: EUR, USD, GBP, etc.). Al confirmar: crear `families/{familyId}` con `inviteCode` generado y único, crear `families/{familyId}/members/{uid}` con `role: admin`, actualizar `users/{uid}.familyId`.
- [ ] Al crear la familia, sembrar las categorías por defecto (Fase 4) en `families/{familyId}/categories`.
- [ ] **Unirse por código**: input del código → buscar la familia por `inviteCode` → crear entrada en `invitations` (`type: code_request`, `status: pending`) → navegar a "Esperando aprobación".
- [ ] **Unirse por invitación de correo**: tras el login, comprobar si existe alguna `invitation` con `type: email_invite`, `targetEmail == email del usuario`, `status: pending`. Si existe, mostrar "Te han invitado a la familia X" con aceptar/rechazar. Al aceptar: crear `member` con `status: approved` directamente, marcar `invitation.status = accepted`, actualizar `users/{uid}.familyId`.
- [ ] Pantalla de administración (solo admins): lista de `invitations` tipo `code_request` en estado `pending`; aprobar/rechazar crea el `member` y actualiza el estado.
- [ ] Pantalla para que el admin invite por correo: input de email → crea `invitation` tipo `email_invite`.
- [ ] Escribir las reglas de seguridad de Firestore de esta fase (creación de familia, members, invitations) y probarlas con el Firebase Emulator Suite antes de continuar.

## Fase 3 — Cuentas bancarias
- [ ] Modelo `Account` + `AccountRepository` (listener en tiempo real sobre `families/{familyId}/accounts`).
- [ ] Pantalla listado de cuentas de la familia (nombre, tipo, saldo actual).
- [ ] Formulario añadir/editar cuenta (nombre, tipo, saldo inicial).
- [ ] Acción de desactivar/archivar cuenta (no borrar físicamente si ya tiene movimientos, para no perder histórico).
- [ ] Reglas de seguridad para `accounts` (solo miembros aprobados leen/escriben; ver nota sobre `currentBalance` en la Fase 5).

## Fase 4 — Categorías
- [ ] Modelo `Category` + `CategoryRepository`.
- [ ] Categorías por defecto a sembrar al crear la familia (ejemplo: Nómina, Otros ingresos, Alimentación, Vivienda, Transporte, Ocio, Salud, Educación, Otros gastos).
- [ ] Pantalla de gestión de categorías: listar, añadir personalizada (nombre, tipo, icono/color), editar, eliminar (solo si no está en uso, o marcarla inactiva).

## Fase 5 — Registro de movimientos (gasto/ingreso)
- [ ] Modelo `Transaction` + `TransactionRepository`.
- [ ] Formulario "Añadir movimiento": selector de fecha, descripción, importe (formateado según `currencyCode` de la familia), tipo (gasto/ingreso), categoría (filtrada por tipo), método de pago (tarjeta/transferencia/efectivo/bizum), cuenta asociada.
- [ ] Guardar el movimiento **y** actualizar `currentBalance` de la cuenta en una única Firestore transaction (atómico), para que nunca queden desincronizados.
- [ ] Igual para edición y borrado: recalcular el saldo de la cuenta afectada dentro de la misma transacción atómica.
- [ ] Listado de movimientos con filtros: por cuenta, por categoría, por rango de fechas, por tipo.
- [ ] Reglas de seguridad para `transactions` (documentar el riesgo de escritura directa de `currentBalance` si no se puede restringir del todo desde las reglas).

## Fase 6 — Posición neta / Dashboard
- [ ] Pantalla principal (home tras login): saldo total de la familia (suma de `currentBalance` de cuentas activas).
- [ ] Desglose por cuenta (lista/tarjetas).
- [ ] Desglose ingresos vs gastos del periodo seleccionado (selector: mes actual / últimos 3 meses / año / rango personalizado).
- [ ] Desglose por categoría del periodo seleccionado (lista ordenada de mayor a menor importe).

## Fase 7 — Estadísticas avanzadas
- [ ] Añadir dependencia MPAndroidChart.
- [ ] Gráfico de evolución mensual de ingresos vs gastos (líneas o barras).
- [ ] Gráfico de gastos por categoría (donut/pie), con interacción para ver el detalle al tocar una categoría.
- [ ] Comparativa por miembro de la familia (quién ha registrado qué volumen de gasto/ingreso).
- [ ] Comparativa entre cuentas (evolución de saldo por cuenta a lo largo del tiempo).

## Fase 8 — Calidad, seguridad y pulido
- [ ] Revisión completa de las reglas de seguridad de Firestore con casos de prueba en el Firebase Emulator Suite (usuario no autenticado, usuario de otra familia, member vs admin).
- [ ] Manejo de estados vacíos (sin cuentas, sin movimientos, sin familia) y de errores de red.
- [ ] Verificar que la persistencia offline de Firestore funciona razonablemente bien para lectura y para crear movimientos sin conexión.
- [ ] Tests unitarios de `Repository` (con Firestore emulado o mocks) y de `ViewModel`.
- [ ] Revisión de accesibilidad básica (`contentDescription`, tamaños de texto, contraste).
- [ ] Preparar la firma de release: generar keystore fuera del repo, configurar `signingConfigs` leyendo credenciales desde `local.properties`.

## Fase 9 — Publicación (opcional, futura)
- [ ] Icono de la app, splash screen.
- [ ] Ficha de Google Play, política de privacidad (obligatoria por tratar datos personales vía Firebase).
- [ ] Subida al canal de pruebas internas de Google Play.
