# PLAN_DESARROLLO.md — FinanzApp

> Plan de implementación paso a paso. Cada fase debe dejar el proyecto compilando y, si aplica, ejecutándose en un emulador/dispositivo antes de pasar a la siguiente. Marca cada tarea con `[x]` al completarla. Consulta `AGENTS.md` para contexto de arquitectura y modelo de datos antes de cada fase.

## Fase 0 — Preparación del entorno
- [x] Crear el proyecto en Android Studio: plantilla "Empty Views Activity", lenguaje Java, `applicationId` = `com.finanzapp.app` (o el que se decida finalmente).
- [x] Configurar `minSdk` 26 y `targetSdk` al valor por defecto sugerido por Android Studio.
- [x] Inicializar git (`git init`) y crear `.gitignore` (ver sección 6 de `AGENTS.md`).
- [x] Primer commit: proyecto vacío compilando.
- [x] (Acción manual del humano) Crear proyecto en Firebase Console, registrar la app Android con el `applicationId` elegido y el SHA-1 de depuración.
- [x] (Acción manual del humano) Descargar `google-services.json` y colocarlo en `app/`; verificar que **no** aparece en `git status`.
- [x] Crear `google-services.json.example` con estructura ficticia y documentar en `README.md` cómo obtener el propio.
- [x] Añadir el plugin `com.google.gms.google-services` y el Firebase BoM más reciente al `build.gradle` (proyecto y módulo `app`).
- [x] (Acción manual del humano) En Firebase Console, habilitar Authentication → proveedor Google.
- [x] (Acción manual del humano) Crear la base de datos Cloud Firestore (modo producción, elegir región cercana).

## Fase 1 — Autenticación (Firebase Auth + Google vía Credential Manager)
- [x] Añadir dependencias: `firebase-auth`, `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`.
- [x] Implementar `LoginActivity` con botón "Continuar con Google".
- [x] Configurar `GetGoogleIdOption` usando el **Web Client ID** del proyecto de Firebase (no el Android client ID).
- [x] Implementar el flujo: `CredentialManager.getCredential()` → extraer `idToken` → `GoogleAuthProvider.getCredential(idToken, null)` → `FirebaseAuth.signInWithCredential()`.
- [x] Manejar la cancelación del usuario (sin mostrar error) y los errores reales (mostrar mensaje).
- [x] En el primer login, crear/actualizar `users/{uid}` en Firestore (`displayName`, `email`, `photoUrl`, `familyId: null`, `createdAt`).
- [x] Añadir pantalla splash/loading que decida a dónde navegar: login → onboarding (si `familyId == null`) → home (si ya tiene familia).
- [x] Implementar cierre de sesión (`signOut`, limpiar Credential Manager, volver a `LoginActivity`).

## Fase 2 — Onboarding: crear o unirse a una unidad familiar
- [x] Pantalla de bienvenida: "Crear una familia" / "Unirme a una familia".
- [x] **Crear familia**: formulario con nombre + selector de moneda (lista corta de ISO 4217 comunes: EUR, USD, GBP, etc.). Al confirmar: crear `families/{familyId}` con `inviteCode` generado y único, crear `families/{familyId}/members/{uid}` con `role: admin`, actualizar `users/{uid}.familyId`.
- [x] Al crear la familia, sembrar las categorías por defecto (Fase 5) en `families/{familyId}/categories`.
- [x] **Cuentas iniciales (posición neta inicial)**: tras el formulario de nombre + moneda, añadir un paso más al asistente de creación de familia — "Añade tus cuentas" — que permita dar de alta N cuentas (nombre, saldo inicial) reutilizando el mismo formulario de la Fase 4 (`AddEditAccountFragment`/`AccountViewModel`), o pulsar "Continuar sin cuentas" para añadirlas más tarde desde Ajustes. La suma de los `initialBalance` de las cuentas dadas de alta aquí es la posición neta inicial de la familia.
  - ⚠️ Dependencia con la Fase 4: para implementar esta tarea hace falta adelantar el modelo `Account` + `AccountRepository` y una versión mínima del formulario de alta de cuenta (nombre, saldo inicial). Constrúyelos aquí aunque el resto de la Fase 4 (listado completo, archivado, etc.) se termine después.
  - **2026-07-13**: se elimina el campo "tipo de cuenta" del modelo y del formulario — una cuenta se define únicamente por su nombre y su saldo inicial. Verificado el flujo completo: `CreateFamilyFragment` crea familia+member admin, navega con `familyId` a `AddInitialAccountsFragment`, que usa `AccountViewModel`/`AccountRepository` para dar de alta cuentas ya como miembro existente de la familia.
  - **2026-07-13**: se elimina el campo "tipo de cuenta" del modelo y del formulario — una cuenta se define únicamente por su nombre y su saldo inicial.
- [x] Ampliar las reglas de seguridad de Firestore para que el creador de la familia pueda, durante el propio alta, escribir también en `families/{familyId}/accounts`; volver a probar con el Firebase Emulator Suite.
- [x] **Unirse por código**: input del código → buscar la familia por `inviteCode` → crear entrada en `invitations` (`type: code_request`, `status: pending`) → navegar a "Esperando aprobación".
- [x] **Unirse por invitación de correo**: tras el login, comprobar si existe alguna `invitation` con `type: email_invite`, `targetEmail == email del usuario`, `status: pending`. Si existe, mostrar "Te han invitado a la familia X" con aceptar/rechazar. Al aceptar: crear `member` con `status: approved` directamente, marcar `invitation.status = accepted`, actualizar `users/{uid}.familyId`.
- [x] Escribir las reglas de seguridad de Firestore de esta fase (creación de familia, members, invitations) y probarlas con el Firebase Emulator Suite antes de continuar.

## Fase 3 — Gestión de la Familia y Ajustes
 - [x] Pantalla de perfil/ajustes: ver datos del usuario, cerrar sesión.
 - [x] **Gestión de miembros**:
    - [x] Listado de miembros de la unidad familiar (nombre, email, rol).
    - [x] Botón "Invitar miembro" (abre diálogo para introducir email) — `InviteMemberFragment` implementado.
    - [x] Listado de solicitudes de unión por código (`code_request`) pendientes (solo para admins) — Integrado en `MemberListFragment`.
    - [x] Pantalla de administración (solo admins): aprobar/rechazar invitaciones tipo `code_request` desde la lista de miembros.
    - [x] **Modificar rol de miembros**: permitir a un admin cambiar el rol de otro miembro (de "member" a "admin" y viceversa).
    - [x] **Rol de Dueño (Owner)**: implementar el rol `owner`. El creador de la familia es el `owner`. Solo el `owner` puede cambiar roles de otros `admin`. El `owner` no puede ser degradado por un `admin`.
    - [x] **Traspaso de Propiedad**: al abandonar la familia, si el que sale es el `owner`, la propiedad se traspasa al admin más antiguo (o miembro si no hay admins). Si es el único miembro, la familia se borra.
 - [x] Configuración de la familia (solo admins): editar nombre de la familia, cambiar moneda (con aviso de que no recalcula históricos) — `FamilySettingsFragment`.
 - [x] **Abandonar familia**:
    - [x] Acción de "Salirse de la familia".
    - [x] Lógica: si es el único miembro, borrar la familia y todas sus subcolecciones (limpieza total) — repository realiza borrado básico; se recomienda borrado recursivo adicional en el futuro.
    - [x] Lógica: si hay más miembros y es el único admin, pedir promover a otro antes de salir o promover automáticamente (implementación automática de promoción en `FamilyRepository`).
    - [x] Actualizar `users/{uid}.familyId = null` y navegar a Onboarding (la navegación al salir queda hecha hacia el inicio; el flujo de onboarding depende del guard routing en la app).
 - [x] Borrado de cuenta (opcional): eliminar `users/{uid}` y forzar salida de la familia.
 - [x] **Mejoras de UX y Estabilidad (Fase 3 bis)**:
    - [x] Rediseño del Dashboard: prioridad al nombre de la familia y foto de perfil (Glide).
    - [x] Navegación de Ajustes: acceso a perfil vía foto y a familia vía barra inferior.
    - [x] Seguridad en Borrado: verificación mediante frase escrita ("BORRAR CUENTA").
    - [x] Gestión de Invitaciones: visualización y cancelación de invitaciones pendientes en la lista de miembros.
    - [x] Onboarding Inteligente: detección automática de invitaciones pendientes en la pantalla de Bienvenida para acceso directo.
    - [x] Código de Familia: visualización y opción de copiar el código de invitación desde los ajustes de familia.
    - [x] Limpieza de Recursos: eliminación de escuchas (listeners) de Firestore al cerrar sesión para evitar errores de permisos.

## Fase 4 — Cuentas bancarias
> Nota: la parte mínima de esta fase (modelo `Account` + formulario de alta con nombre/saldo inicial) puede haberse adelantado ya en la Fase 2, porque el asistente de creación de familia permite dar de alta cuentas y fijar la posición neta inicial en el propio onboarding. Aquí se completa/consolida el resto: listado, edición posterior del saldo inicial, archivado, borrado y reglas de seguridad definitivas.
- [x] Modelo `Account` + `AccountRepository` (listener en tiempo real sobre `families/{familyId}/accounts`) — si ya existe desde la Fase 2, revisar que cubra también listado y archivado/borrado.
- [x] Pantalla listado de cuentas de la familia (nombre, saldo actual).
- [x] Formulario añadir/editar cuenta (nombre, saldo inicial) — reutilizable tanto desde el asistente de onboarding como desde esta pantalla de gestión de cuentas.
- [x] **Editar la posición neta inicial de una cuenta ya existente**: permitir modificar `initialBalance` en cualquier momento después de la creación de la familia (no solo durante el alta). Al guardar, recalcular `currentBalance` dentro de una única Firestore transaction: `currentBalance_nuevo = currentBalance_actual + (initialBalance_nuevo − initialBalance_anterior)`, para conservar el efecto de los movimientos ya registrados.
- [x] **Eliminar cuenta**: si la cuenta no tiene ningún movimiento (`transaction`) asociado, permitir el borrado físico del documento. Si ya tiene movimientos, no permitir el borrado (para no perder histórico): deshabilitar la opción "Eliminar" y ofrecer en su lugar "Archivar/Desactivar", con un mensaje explicativo al usuario.
- [x] Acción de desactivar/archivar cuenta (no borrar físicamente si ya tiene movimientos, para no perder histórico).
- [x] Reglas de seguridad para `accounts`: cualquier miembro aprobado puede leer y crear cuentas; solo un miembro con `role: admin` puede editar `initialBalance`, archivar o eliminar una cuenta (igual que cambiar `currencyCode`, ver AGENTS.md sección 5). Probar la creación, la edición del saldo inicial y el borrado/archivado con el Firebase Emulator Suite (ver también la nota sobre `currentBalance` en la Fase 6).

## Fase 5 — Categorías
- [x] Modelo `Category` + `CategoryRepository`.
- [x] Categorías por defecto a sembrar al crear la familia (ejemplo: Nómina, Otros ingresos, Alimentación, Vivienda, Transporte, Ocio, Salud, Educación, Otros gastos).
- [x] Pantalla de gestión de categorías: listar, añadir personalizada (nombre, tipo, icono/color), editar, eliminar (solo si no está en uso, o marcarla inactiva).
  - [x] Listado y borrado de categorías.
  - [x] **Edición y colores**: Permitir editar categorías existentes y asociarles un color personalizado de una paleta (RGB wheel).
  - [x] **Borrado de categorías predeterminadas**: Permitir borrar categorías por defecto.
  - [x] **Control de integridad**: Impedir borrar categorías que ya hayan sido utilizadas en movimientos. El usuario debe borrar primero el movimiento.
  - [x] **Acceso restringido**: El botón de gestionar categorías solo debe ser visible para usuarios con rol `admin` o `owner`.

## Fase 6 — Registro de movimientos (gasto/ingreso)
- [x] Modelo `Transaction` + `TransactionRepository`.
- [x] Formulario "Añadir movimiento": selector de fecha, descripción, importe (formateado según `currencyCode` de la familia), tipo (gasto/ingreso), categoría (filtrada por tipo), método de pago (tarjeta/transferencia/efectivo/bizum), cuenta asociada.
- [x] Guardar el movimiento: **obligatorio** guardar el ID del usuario que lo creó (`createdBy`).
- [x] Actualizar `currentBalance` de la cuenta en una única Firestore transaction (atómico).
- [x] Igual para edición y borrado: recalcular el saldo de la cuenta afectada dentro de la misma transacción atómica.
- [x] Listado de movimientos con filtros: por cuenta, por categoría, por rango de fechas, por tipo.
- [x] Mostrar en el detalle del movimiento quién lo registró.
- [x] Reglas de seguridad para `transactions`.

## Fase 7 — Posición neta / Dashboard
- [ ] Pantalla principal (home tras login): saldo total de la familia (suma de `currentBalance` de cuentas activas).
- [ ] Desglose por cuenta (lista/tarjetas).
- [ ] Desglose ingresos vs gastos del periodo seleccionado (selector: mes actual / últimos 3 meses / año / rango personalizado).
- [ ] Desglose por categoría del periodo seleccionado (lista ordenada de mayor a menor importe).

## Fase 8 — Estadísticas avanzadas
- [ ] Añadir dependencia MPAndroidChart.
- [ ] Gráfico de evolución mensual de ingresos vs gastos (líneas o barras).
- [ ] Gráfico de gastos por categoría (donut/pie), con interacción para ver el detalle al tocar una categoría.
- [ ] Comparativa por miembro de la familia (quién ha registrado qué volumen de gasto/ingreso).
- [ ] Comparativa entre cuentas (evolución de saldo por cuenta a lo largo del tiempo).

## Fase 9 — Calidad, seguridad y pulido
- [ ] Revisión completa de las reglas de seguridad de Firestore con casos de prueba en el Firebase Emulator Suite.
- [ ] Manejo de estados vacíos (sin cuentas, sin movimientos, sin familia) y de errores de red.
- [ ] Verificar que la persistencia offline de Firestore funciona razonablemente bien.
- [ ] Tests unitarios de `Repository` y de `ViewModel`.
- [ ] Revisión de accesibilidad básica (`contentDescription`, tamaños de texto, contraste).
- [ ] Preparar la firma de release: generar keystore fuera del repo, configurar `signingConfigs`.

## Fase 10 — Publicación (opcional, futura)
- [ ] Icono de la app, splash screen.
- [ ] Ficha de Google Play, política de privacidad.
- [ ] Subida al canal de pruebas internas de Google Play.
