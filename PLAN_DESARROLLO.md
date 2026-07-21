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
  - [x] **Edge-to-edge / insets del sistema**: `MainActivity` aplica `WindowInsets` manualmente (`systemBars`) para que el `NavHostFragment` no quede debajo de la status bar y el `BottomNavigationView` respete la barra de gestos inferior (`android:clipToPadding="false"` en `activity_main.xml` + listener en `MainActivity.onCreate()`). Necesario porque el `targetSdk` reciente activa edge-to-edge por defecto.
  - [x] **Expulsar miembros**: un `owner` puede expulsar a cualquier miembro (excepto a sí mismo); un `admin` puede expulsar a `member` pero no a otro `admin` ni al `owner`. Opción "Expulsar de la familia" añadida al mismo `PopupMenu` de cambio de rol en `MemberListFragment`, con `AlertDialog` de confirmación. Nuevo `FamilyRepository.removeMember()` borra el documento en `members/` y limpia `users/{uid}.familyId`. Regla de Firestore nueva en `users/{uid}`: un admin/owner de la familia puede poner a `null` el `familyId` de un miembro que están expulsando (antes solo se permitía asignar un `familyId` string, nunca limpiarlo).
  - [x] **Robustez añadida en `AcceptInvitationFragment`**: la búsqueda de invitación pendiente (`checkPendingInvitation`, usada solo como fallback si no se navega con argumentos) no tenía manejo de errores; ahora los botones se deshabilitan hasta tener los datos, se añade `addOnFailureListener`/log + `Toast` de error, se normaliza el email a minúsculas para el `whereEqualTo`, y se añade `AcceptInvitationFragment.newInstance(invitationId, familyId)` para poder recibir la invitación ya localizada sin repetir la consulta.
  - [x] **Bug corregido — botón "No gracias" (rechazar invitación) no hacía nada**: causa raíz encontrada en `OnboardingViewModel`, no en `AcceptInvitationFragment`. Un único `MutableLiveData<String> pendingFamilyId` era compartido por dos flujos asíncronos independientes lanzados casi a la vez desde `WelcomeFragment.checkPendingInvitations()`: `fetchPendingInvitation()` (invitación por email) y `fetchPendingCodeRequest()` (solicitud por código). Cuando no había ninguna solicitud por código pendiente (el caso normal), esa segunda llamada terminaba después y sobreescribía con `null` el `familyId` correcto que acababa de guardar la invitación por email — dejando `getPendingFamilyIdValue()` en `null` justo cuando el usuario pulsaba "Aceptar"/"No gracias", y como no había rama `else`, no se registraba ningún log. Solucionado separando el campo en `pendingInvitationFamilyId` y `pendingCodeRequestFamilyId` (cada flujo escribe el suyo), con getters específicos, y añadiendo log + `Toast` de aviso en `WelcomeFragment` si algún día vuelven a faltar los datos al pulsar los botones.
  - [x] **Restricción de permisos admin/owner**: solo `admin`/`owner` pueden editar el nombre y la moneda de la familia (`FamilySettingsFragment`: campos deshabilitados y botón oculto para `member`), invitar nuevos miembros (`MemberListFragment`: botón "Invitar" oculto para `member`) y aprobar/rechazar solicitudes de unión o cancelar invitaciones (`MemberAdapter`: acciones ocultas para `member`). Reforzado también en `firestore.rules`: crear una invitación de tipo `email_invite` ahora exige ser admin/owner de la familia (antes cualquier usuario autenticado podía crearlas), y actualizar el `status` de una invitación a `approved`/`accepted` fuera de las transiciones permitidas (auto-aprobación por parte del propio solicitante) ya no está permitido.

## Fase 4 — Cuentas bancarias
> Nota: la parte mínima de esta fase (modelo `Account` + formulario de alta con nombre/saldo inicial) puede haberse adelantado ya en la Fase 2, porque el asistente de creación de familia permite dar de alta cuentas y fijar la posición neta inicial en el propio onboarding. Aquí se completa/consolida el resto: listado, edición posterior del saldo inicial, archivado, borrado y reglas de seguridad definitivas.
- [x] Modelo `Account` + `AccountRepository` (listener en tiempo real sobre `families/{familyId}/accounts`) — si ya existe desde la Fase 2, revisar que cubra también listado y archivado/borrado.
- [x] Pantalla listado de cuentas de la familia (nombre, saldo actual).
- [x] Formulario añadir/editar cuenta (nombre, saldo inicial) — reutilizable tanto desde el asistente de onboarding como desde esta pantalla de gestión de cuentas.
- [x] **Editar la posición neta inicial de una cuenta ya existente**: permitir modificar `initialBalance` en cualquier momento después de la creación de la familia (no solo durante el alta). Al guardar, recalcular `currentBalance` dentro de una única Firestore transaction: `currentBalance_nuevo = currentBalance_actual + (initialBalance_nuevo − initialBalance_anterior)`, para conservar el efecto de los movimientos ya registrados.
- [x] **Eliminar cuenta**: si la cuenta no tiene ningún movimiento (`transaction`) asociado, permitir el borrado físico del documento. Si ya tiene movimientos, no permitir el borrado (para no perder histórico): deshabilitar la opción "Eliminar" y ofrecer en su lugar "Archivar/Desactivar", con un mensaje explicativo al usuario.
- [x] Acción de desactivar/archivar cuenta (no borrar físicamente si ya tiene movimientos, para no perder histórico).
- [x] Reglas de seguridad para `accounts`: cualquier miembro aprobado puede leer y crear cuentas; solo un miembro con `role: admin` (u `owner`) puede editar la cuenta (nombre, `initialBalance`, `active`), archivar o eliminar. La única excepción es `currentBalance`, que cualquier member puede modificar, pero solo indirectamente a través del registro de un movimiento (`TransactionRepository`), nunca editando la cuenta directamente. Probar la creación, la edición del saldo inicial y el borrado/archivado con el Firebase Emulator Suite (ver también la nota sobre `currentBalance` en la Fase 6).
- [x] **Mejora de UX (2026-07-16): ocultar en el listado la acción no permitida en vez de mostrar un error tras pulsarla**. Hasta ahora `AccountListFragment` mostraba siempre los tres botones (Editar/Archivar/Eliminar) para cada cuenta, y solo al pulsar "Eliminar" sobre una cuenta con movimientos se descubría el error devuelto por `AccountRepository.deleteAccount`. Cambios:
  - `Account`: nuevo campo transitorio `hasTransactions` (no persistido en Firestore, anotado con `@Exclude` en el getter) que indica si la cuenta tiene al menos un movimiento asociado.
  - `AccountRepository.getAccounts(familyId)`: además de escuchar la colección `accounts`, escucha también `transactions` y, en cada cambio, cruza los `accountId` de los movimientos (consulta `whereIn("accountId", ids)`) para marcar `hasTransactions` en cada cuenta antes de emitir la lista.
  - `AccountListFragment` (`AccountsViewHolder.bind`): el botón "Editar" siempre se muestra; "Archivar" y "Eliminar" pasan a ser mutuamente excluyentes — se muestra solo "Archivar" si la cuenta tiene movimientos, y solo "Eliminar" si no tiene ninguno.
  - Se mantiene igualmente la comprobación server-side existente en `AccountRepository.deleteAccount` (defensa en profundidad), por si el flag del cliente estuviera desactualizado en el momento de la acción.
- [x] **Bugfix (2026-07-16): `PERMISSION_DENIED` al archivar/eliminar cuenta siendo `member`**. `AccountListFragment` mostraba los botones "Archivar"/"Eliminar" a cualquier miembro, pero las reglas de Firestore (sección 5 de `AGENTS.md`) solo permiten esas acciones a `admin`/`owner`; un `member` normal los pulsaba y Firestore rechazaba la escritura. Cambios:
  - `AccountListFragment`: nuevo método `checkAdminRole(familyId)` que escucha en tiempo real `families/{familyId}/members/{uid}` para saber si el usuario actual es `admin`/`owner`.
  - `AccountsAdapter`/`AccountsViewHolder`: reciben ese flag `isAdmin`; "Archivar" y "Eliminar" ahora solo se muestran si `isAdmin == true` (y, como ya estaba, son mutuamente excluyentes según `hasTransactions`). Un `member` normal solo ve "Editar".
  - Pendiente de revisar (fuera del alcance de este fix, requiere ver `AddEditAccountFragment`, no disponible en este cambio): confirmar que el formulario de edición tampoco permite a un `member` modificar `initialBalance` (solo `name`), ya que la regla de Firestore para `update` en `accounts` solo permite a un member cambiar el campo `name`.
- [x] **Bugfix (2026-07-17): al entrar en la pantalla de Cuentas salen varios Toast sin que el usuario haya hecho nada.** Localizada la causa raíz: el uso de `MutableLiveData` estándar en los `ViewModel` para notificar el resultado de acciones (crear, editar, archivar, borrar). Al navegar de vuelta a un fragmento, el `LiveData` entrega el último valor guardado al nuevo observador, disparando el `Toast` de éxito/error de una operación pasada.
  - Se implementa `SingleLiveEvent` en `com.finanzapp.app.util`, una variante de `LiveData` que solo notifica una vez a un único observador y no retiene el valor para nuevas suscripciones.
  - Se actualizan `AccountViewModel`, `TransactionViewModel`, `FamilyViewModel`, `CategoryViewModel` y `OnboardingViewModel` para usar `SingleLiveEvent` en todos los campos que representan eventos de un solo uso (resultados de operaciones).
  - Ver detalle técnico en `AGENTS.md`, sección "Decisiones tomadas durante el desarrollo" (entrada 2026-07-17).
- [x] **Decisión de negocio (2026-07-16): un `member` normal tampoco puede editar cuentas**. Se amplía el bugfix anterior: ahora "Editar" también se oculta para `member` en `AccountListFragment` (solo `admin`/`owner` ven Editar/Archivar/Eliminar; un member ve la cuenta en modo solo lectura). Se actualiza `firestore.rules` en consonancia: la regla `update` de `accounts` ya no permite a un `member` cambiar el campo `name` (antes sí podía). **Efecto colateral importante y deseado**: al revisar la regla se detectó que el campo `currentBalance` tampoco estaba en la lista de campos permitidos para `member`, lo cual habría bloqueado con `PERMISSION_DENIED` a **cualquier miembro normal que intentara registrar un ingreso/gasto** (`TransactionRepository` actualiza `currentBalance` de la cuenta dentro de la misma transacción atómica que crea/edita/borra el movimiento). Se corrige permitiendo explícitamente `currentBalance` como único campo que un member puede tocar en `accounts` (vía el flujo de movimientos, nunca editando la cuenta directamente).
- [x] **Bugfix (2026-07-17)**: Al editar la posicion inicial de una cuenta esta se archiva automaticamente haciendo que el usuario tenga que desarchivarla. Solucionado asegurando que se preserva el estado `active` original en `AccountRepository.updateAccount`.

## Fase 5 — Categorías
- [x] Modelo `Category` + `CategoryRepository`.
- [x] Categorías por defecto a sembrar al crear la familia (ejemplo: Nómina, Otros ingresos, Alimentación, Vivienda, Transporte, Ocio, Salud, Educación, Otros gastos).
- [x] Pantalla de gestión de categorías: listar, añadir personalizada (nombre, tipo, icono/color), editar, eliminar (solo si no está en uso, o marcarla inactiva).
  - [x] Listado y borrado de categorías.
  - [x] **Edición y colores**: Permitir editar categorías existentes y asociarles un color personalizado de una paleta (RGB wheel) o mediante entrada directa de código hexadecimal RGB.
  - [x] **Selector de categoría con búsqueda**: Implementar filtrado por texto (búsqueda) en el selector de categorías al añadir/editar un movimiento, para facilitar la selección en listas largas.
  - [x] **Borrado de categorías predeterminadas**: Permitir borrar categorías por defecto.
  - [x] **Control de integridad**: Impedir borrar categorías que ya hayan sido utilizadas en movimientos. El usuario debe borrar primero el movimiento.
  - [x] **Acceso restringido**: El botón de gestionar categorías solo debe ser visible para usuarios con rol `admin` o `owner`.
- [x] **Colores por defecto en la siembra (2026-07-21)**: al crear una familia, cada categoría semilla debe crearse ya con su campo `color` relleno (nueva lista de 25 categorías proporcionada por el usuario, ver `AGENTS.md` sección 4), en vez de dejarlo vacío/por defecto del tema.
- [x] **Auditoría de categorías por defecto (2026-07-21)**: actualizar las categorías sembradas según la nueva lista (nombre, `appliesTo` y `color`).

## Fase 6 — Registro de movimientos (gasto/ingreso)
- [x] Modelo `Transaction` + `TransactionRepository`.
- [/] Formulario "Añadir movimiento": selector de fecha, descripción, importe (formateado según `currencyCode` de la familia), tipo (gasto/ingreso), categoría (filtrada por tipo y con búsqueda por texto), método de pago (tarjeta/transferencia/efectivo/bizum), cuenta asociada.
- [x] Guardar el movimiento: **obligatorio** guardar el ID del usuario que lo creó (`createdBy`).
- [x] Actualizar `currentBalance` de la cuenta en una única Firestore transaction (atómico).
- [x] Igual para edición y borrado: recalcular el saldo de la cuenta afectada dentro de la misma transacción atómica.
- [x] Listado de movimientos con filtros: por cuenta, por categoría, por rango de fechas, por tipo y método de pago. Soporte completo para modo oscuro y UI adaptativa.
- [x] Mostrar en el detalle del movimiento quién lo registró.
- [x] Reglas de seguridad para `transactions`.
- [x] **Correcciones de UX en la pantalla de Movimientos (2026-07-16)**:
  - [x] Indicador de carga (`ProgressBar`) mientras se buscan/filtran movimientos, visible desde que cambia cualquier filtro hasta que llega el resultado de Firestore.
  - [x] Mostrar el método de pago de cada movimiento en el listado (`item_transaction.xml`), reutilizando el mapeo código→etiqueta ya existente para los filtros.
  - [x] Navegación de vuelta al Dashboard desde Movimientos: flecha atrás en la Toolbar (`ic_arrow_back`) que hace `navigateUp()`.
  - [x] El color de la categoría (`Category.color`) ahora se usa realmente en el listado: un punto de color y el nombre de la categoría se pintan con ese color (con fallback a `colorPrimary` del tema si el valor no es válido).
  - [x] Se sustituyen los colores de importe (ingreso/gasto) codificados en hexadecimal por los recursos `@color/success` y `@color/error` de `colors.xml`, para mantener consistencia con la paleta de la app.
- [x] **Bugfix (2026-07-16): el listado de Movimientos mostraba movimientos de cuentas archivadas**. `TransactionRepository` no conoce el concepto de cuenta archivada (solo trabaja con `transactions`), así que el filtrado se hace en `TransactionListFragment` a partir de la lista de cuentas ya cargada (`viewModel.getAccounts(familyId)`, que —igual que en el bug del Dashboard— devuelve activas y archivadas juntas). Cambios:
  - Nuevo `Set<String> archivedAccountIds`: se rellena al recibir las cuentas, separando las archivadas de las activas.
  - El spinner "Filtrar por cuenta" ahora solo lista cuentas activas (`allAccounts` pasa a contener solo esas), para no poder filtrar explícitamente por una cuenta archivada.
  - `updateTransactions()` descarta, antes de pintar la lista, cualquier movimiento cuyo `accountId` esté en `archivedAccountIds`.
  - Se relanza `updateTransactions()` al resolverse la carga de cuentas, por si los movimientos ya se habían pintado sin filtrar (llegan por listeners independientes y en cualquier orden).
- [x] **Bugfix (2026-07-16): el desglose por categoría del Dashboard sumaba movimientos de cuentas archivadas**, igual que ya pasaba con el listado de Movimientos. `DashboardViewModel.fetchFamilyData()` combinaba `transactionRepository.getTransactions(...)` (todos los movimientos del periodo, sin distinguir cuenta activa/archivada) con `categoryRepository.getCategories(...)` para construir el desglose, sin cruzar con el estado `active` de la cuenta de cada movimiento — así que ingresos, gastos y el desglose por categoría del Dashboard incluían indebidamente movimientos de cuentas ya archivadas. Cambios en `DashboardViewModel`:
  - Se guarda el último snapshot de cada fuente asíncrona (`activeAccountIds`, `latestTransactions`, `latestCategories`) y se añade `recomputeStatistics()`, que se invoca cada vez que cambia cualquiera de las tres (llegan de listeners independientes, en cualquier orden).
  - `recomputeStatistics()` filtra `latestTransactions` quedándose solo con los movimientos cuyo `accountId` pertenece a `activeAccountIds` (calculado a partir de `Account.isActive()`, igual que ya se hacía para `netBalance`) y pasa esa lista filtrada a `processTransactions()`.
  - Con esto, `totalIncome`, `totalExpense` y `categoryBreakdown` quedan consistentes con `netBalance` y con el desglose por cuenta: todos excluyen ya las cuentas archivadas.
  - **Decisión de negocio aplicada de forma transversal**: todas las estadísticas "en general" de la app (Dashboard y, en el futuro, Fase 8) deben calcularse únicamente sobre cuentas activas — una cuenta archivada no debe alterar ingresos, gastos, desgloses ni ninguna métrica agregada, aunque sus movimientos históricos se conserven en Firestore. Ver también la entrada correspondiente en la sección "Decisiones tomadas durante el desarrollo" de `AGENTS.md`.
- [x] **Criterio a aplicar en la Fase 8 (Estadísticas avanzadas) desde el principio**: todo cálculo de la nueva pestaña de Estadísticas (evolución mensual, variación de gasto, distribución por categorías, gasto medio, matriz histórica de netos) debe filtrar previamente los movimientos por cuenta activa, con el mismo criterio que el Dashboard (excluir `accountId` de cuentas con `active == false`), salvo que el propio filtro de cuenta seleccionado por el usuario incluya explícitamente una cuenta archivada para consultar su histórico puntual.

## Fase 6 bis — Importación de movimientos desde CSV (solo admin/owner)
> Requisito nuevo (2026-07-17). Ver detalle completo de las reglas de parseo/importación en `AGENTS.md` sección 4, "Importación de movimientos desde CSV". Depende de que existan ya `AccountRepository`, `CategoryRepository` y `TransactionRepository` (Fases 4, 5 y 6).
- [x] `CsvTransactionParser` (`data/importer/`): detecta el delimitador (tabulador, coma o punto y coma) a partir de la cabecera `Fecha Concepto Categoría Valor Tipo Método Cuenta`, descarta la línea de cabecera, y parsea cada fila a un objeto intermedio (`ImportedRow`) o a un error con el número de fila y el motivo.
- [x] `TransactionImportRepository` (`data/importer/`): dado un `List<ImportedRow>` válido y el `familyId`,
  - resuelve cuenta por nombre (case-insensitive) o crea una nueva (`initialBalance: 0`, `active: true`);
  - resuelve categoría por nombre + `appliesTo` compatible (case-insensitive) o crea una nueva (`isDefault: false`, `color` de `CategoryColorPalette`);
  - agrupa las filas válidas en uno o varios `WriteBatch` (máx. 500 operaciones cada uno) que crean los `Transaction` y actualizan `currentBalance` de cada cuenta afectada con `FieldValue.increment()` del delta total de esa cuenta.
- [x] `ImportTransactionsFragment` (`ui/transactions/`), accesible solo para `admin`/`owner` (mismo criterio que "Gestionar categorías"): selector de fichero con `ACTION_OPEN_DOCUMENT`, vista previa/confirmación antes de importar, indicador de progreso durante la escritura, y pantalla de resumen final (movimientos importados, cuentas nuevas, categorías nuevas, filas descartadas con motivo).
- [x] Punto de entrada a la pantalla de importación (por ejemplo, un botón en `TransactionListFragment` o en Ajustes de familia), visible solo para `admin`/`owner`.
- [x] Probar con un CSV de ejemplo que incluya al menos: una fila con cuenta ya existente, una con cuenta nueva, una con categoría ya existente, una con categoría nueva, una fila con fecha inválida, una con método de pago no reconocido — verificar que el resumen final refleja correctamente cada caso.

## Fase 7 — Posición neta / Dashboard
- [x] Pantalla principal (home tras login): saldo total de la familia (suma de `currentBalance` de cuentas activas).
- [x] Desglose por cuenta (lista/tarjetas).
- [x] (Movido a Fase 8) Desglose ingresos vs gastos del periodo seleccionado.
- [x] (Movido a Fase 8) Desglose por categoría del periodo seleccionado.
- [x] **Bugfix (2026-07-16): una cuenta archivada seguía apareciendo en el Dashboard**. `AccountRepository.getAccounts(familyId)` devuelve **todas** las cuentas de la familia (activas y archivadas), ya que otras pantallas (p. ej. el listado de gestión de cuentas de la Fase 4) sí necesitan poder mostrar las archivadas. El problema era que `DashboardAccountAdapter` pintaba esa lista tal cual, sin filtrar, por lo que al archivar una cuenta (`active: false`) esta seguía saliendo en el desglose por cuenta del Dashboard. Cambio: `DashboardAccountAdapter.setItems()` ahora descarta las cuentas con `active == false` antes de añadirlas a `items`, de forma que el Dashboard solo muestra cuentas activas (consistente con el saldo total, que ya sumaba solo `currentBalance` de cuentas activas). No se toca `AccountRepository` para no romper el listado de gestión de cuentas, que sigue necesitando ver también las archivadas.
- [x] (Movido a Fase 8) **Nuevo requisito de UX (2026-07-17): navegación desde el desglose por categoría a Movimientos, con filtros preaplicados.**
- [x] **Simplificación del Dashboard (2026-07-19)**: Eliminación del selector de fechas y desgloses de ingresos/gastos y categorías, centralizando estas funciones en la pestaña de Estadísticas.

## Fase 7 bis — Pertenencia a varias familias
> Requisito nuevo (2026-07-18): un usuario deja de estar limitado a una única familia. **Restricción explícita del propietario: no se modifica ningún campo ni colección ya existente.** `users/{uid}.familyId` conserva su nombre (solo cambia su significado, de "la única familia" a "la familia activa") y `families/{familyId}/members/{uid}` no gana ningún campo nuevo (ni `uid` ni `familyName`). La funcionalidad se construye añadiendo, sin tocar nada de lo anterior, la subcolección `users/{uid}/memberships/{familyId}`. Ver el diseño completo en `AGENTS.md`, sección 4 "Pertenencia a varias familias (Fase 7 bis)" y sección 5 (reglas nuevas), y las asunciones tomadas en la entrada correspondiente de "Decisiones tomadas durante el desarrollo" (entrada del 2026-07-18 marcada como "corrección"). Depende de que Fases 1-7 ya funcionen (reutiliza `CreateFamilyFragment`, `JoinByCodeFragment`, `AcceptInvitationFragment`, `FamilySettingsFragment`, y toda la lógica de abandonar familia de la Fase 3).

### Modelo de datos (solo aditivo — nada existente cambia) y migración
- [x] Nuevo POJO `FamilyMembership` (`familyId`, `familyName`, `role`, `joinedAt`), que representa un documento de la nueva subcolección `users/{uid}/memberships/{familyId}`.
- [x] Nuevo `MembershipRepository` (o métodos nuevos en `FamilyRepository`) con las operaciones de escritura sobre esta subcolección: crear, actualizar `role`, actualizar `familyName`, borrar.
- [x] Instrumentar todos los puntos donde hoy se crea/actualiza/borra un `member` para que, en la misma operación (o inmediatamente después, cuando no puedan ir en el mismo `WriteBatch` por tocar subcolecciones de usuarios distintos), hagan lo mismo sobre el `membership` del usuario afectado:
  - [x] Creación de familia (Fase 2): crear `users/{uid}/memberships/{familyId}` (`role: owner`) junto con the `member` `owner`.
  - [x] Aceptar invitación por email (Fase 2): crear el `membership` del usuario junto con su `member`.
  - [x] Aprobar `code_request` (Fase 3): crear el `membership` del usuario junto con su `member`.
  - [x] Cambiar el rol de un miembro (Fase 3): actualizar `role` en el `membership` de ese usuario a la vez que en su `member`.
  - [x] Cambiar el nombre de la familia (`FamilySettingsFragment`): además del batch existente que actualiza `familyName` en todos los `members/*`, iterar esos mismos miembros y actualizar también `familyName` en `users/{uid}/memberships/{familyId}` de cada uno.
  - [x] Abandonar familia / expulsar miembro (Fase 3): borrar `users/{uid}/memberships/{familyId}` del usuario afectado a la vez que se borra su `member`.
  - [x] Traspaso de `owner` (Fase 3): actualizar el `role` del `membership` del nuevo `owner` (y del anterior, si sigue en la familia).
  - [x] Deep-delete de familia (sale el último miembro): borrar también su `membership`.
- [x] **Self-heal automático de usuarios ya existentes (sin script ni intervención manual)**: en la pantalla splash/loading (Fase 1), justo tras resolver el login, comprobar si `users/{uid}/memberships` está vacío; si lo está pero `familyId` no es `null`, leer el `member` del usuario en `families/{familyId}/members/{uid}` y el nombre en `families/{familyId}.name`, y crear con esos datos el `membership` correspondiente en `users/{uid}/memberships/{familyId}` (`role`, `familyName`, `joinedAt` = fecha actual, al no poder recuperar la fecha real de alta) antes de continuar la navegación. Es responsabilidad exclusiva de la app: no requiere ningún script, tarea manual del gestor de la base de datos ni acción visible por parte del usuario. Una vez creado el `membership`, esta comprobación deja de tener efecto para ese usuario.

### Repositorios
- [x] `FamilyRepository.getUserFamilies(uid)` (o método equivalente en `MembershipRepository`): listener en tiempo real sobre `users/{uid}/memberships`, devuelve una lista de `FamilyMembership`. Sustituye al `collectionGroup("members")` del diseño descartado.
- [x] `FamilyRepository.switchActiveFamily(uid, familyId)`: sigue escribiendo en el campo `familyId` (no `activeFamilyId`) de `users/{uid}`; valida (o confía en que la UI ya solo ofrece familias del propio `memberships`) y escribe `users/{uid}.familyId = familyId`.
- [x] `FamilyRepository`/lógica de "abandonar familia" (Fase 3): al salir de una familia, tras la limpieza/traspaso ya existente y el borrado del `membership` correspondiente (ver tarea de arriba), comprobar si al usuario le quedan otros `memberships`; si tiene alguno, fijar `familyId` a uno de los restantes y no navegar a onboarding; si no le queda ninguno, dejar `familyId = null` y navegar a onboarding (comportamiento actual).
- [x] Borrado de cuenta de usuario (Fase 3): leer `users/{uid}/memberships` (colección propia del usuario, sin `collectionGroup`) y ejecutar en cada familia listada la misma lógica de "abandonar familia" (incluyendo traspaso de `owner` o deep-delete si es el único miembro), no solo en la familia activa.

### Reglas de seguridad de Firestore
- [x] `users/{uid}/memberships/{familyId}`: `allow read, write: if request.auth.uid == uid`. Al ser una subcolección privada del propio usuario, no hace falta ninguna regla nueva sobre `members` de otras familias (a diferencia del diseño con `collectionGroup`, ya descartado).
- [x] Restringir la escritura de `users/{uid}.familyId` a valores `null` o a un `familyId` para el que exista `users/{uid}/memberships/{familyId}`.
- [x] Probar ambas reglas con el Firebase Emulator Suite: un usuario no debe poder leer ni escribir `memberships` de otro usuario, y no debe poder fijar `familyId` a una familia para la que no tiene `membership`.

### Routing y sesión
- [x] Actualizar la lógica de la pantalla splash/loading (Fase 1) y de `WelcomeFragment`: **tras ejecutar el self-heal automático de la tarea anterior** (que puede haber creado el primer `membership` del usuario), comprobar si `users/{uid}/memberships` está vacío (→ onboarding). Si no está vacío pero `familyId` es `null` o no corresponde a ningún `membership` (por ejemplo, expulsado de la que tenía activa), fijar automáticamente el primero disponible como activo y continuar a Dashboard. Si es válido, ir a Dashboard directamente.
- [x] Cachear `familyId` (familia activa) en memoria (por ejemplo en `AppContainer` o una clase de sesión) para no releerlo en cada pantalla; invalidar/refrescar la caché al cambiar de familia.

### UX — Selector de familias
- [x] `FamilySwitcherFragment` (bottom sheet o pantalla, en `ui/family/`): lista las familias del usuario (nombre, rol, indicador visual de cuál es la activa) leyendo `users/{uid}/memberships`; tocar una familia distinta a la activa cambia `familyId` y navega al Dashboard.
- [x] Botones "Crear otra familia" y "Unirme a otra familia por código" dentro del selector, que reutilizan `CreateFamilyFragment`/`JoinByCodeFragment` en modo "añadir familia" (no reemplazan la familia activa actual salvo que el usuario elija explícitamente cambiar a la nueva al terminar).
- [x] **Único punto de entrada al selector**: botón desplegable junto al nombre de la familia en la cabecera del Dashboard, arriba a la izquierda. Cambiar de familia activa, y crear o unirse a una familia adicional, solo se puede iniciar desde ahí — no hay ningún otro acceso (ni desde Ajustes ni desde ninguna otra pantalla).
- [x] **Cambiar de familia limpia la navegación**: al confirmar el cambio de `familyId`, hacer `popBackStack` hasta el Dashboard (grafo raíz) antes de recargar, para no dejar en el back-stack pantallas de Cuentas/Movimientos/Categorías/Estadísticas/Miembros con datos de la familia anterior. Verificar que todos los `ViewModel` de esas pantallas recargan sus listeners de Firestore con el nuevo `familyId` en vez de reutilizar los antiguos.
- [x] `MyFamiliesFragment` (en Ajustes): listado **puramente de solo lectura** de `users/{uid}/memberships` con el rol en cada una (sin ninguna acción de cambiar la activa desde aquí); para cambiar de familia o para abandonar una en concreto, el usuario debe volver al Dashboard y usar el desplegable de la cabecera, y una vez esté activa la familia que quiere abandonar, usar el botón "Salir de la familia" ya existente en `FamilySettingsFragment` (se decide no duplicar ahí la lógica de traspaso de `owner`/deep-delete).

### UX — Crear/unirse a una familia adicional
- [x] `CreateFamilyFragment`/`JoinByCodeFragment`/`AcceptInvitationFragment`: dejar de asumir que se ejecutan siempre desde onboarding sin familia previa. Al terminar con éxito, si el usuario ya tenía un `familyId` activo distinto, preguntar si quiere cambiar ahora a la familia recién creada/unida o seguir donde estaba, en vez de sobrescribir `familyId` automáticamente.
- [x] Comprobación de invitaciones por email pendientes: además de en `WelcomeFragment` (onboarding sin familia), añadir una comprobación al iniciar sesión/abrir la app estando ya dentro de una familia, y mostrar un aviso in-app (badge en el selector o diálogo) en vez de limitarlo a la pantalla de bienvenida.

### Pruebas manuales de la fase
- [X] Usuario nuevo (0 familias): el flujo de onboarding no cambia respecto al actual.
- [X] Usuario con 1 familia: crear o unirse a una segunda familia sin perder el acceso a la primera; verificar que puede cambiar entre ambas desde el selector y que Cuentas/Movimientos/Categorías muestran siempre los datos de la familia activa, nunca mezclados.
- [X] Abandonar una de dos familias: debe quedar en Dashboard de la familia restante, no en onboarding.
- [X] Abandonar la única familia restante: debe ir a onboarding, igual que el comportamiento previo a esta fase.

## Fase 8 — Estadísticas avanzadas (Pestaña Independiente)
> **Criterio transversal de esta fase**: salvo que el usuario filtre explícitamente por una cuenta archivada, todas las estadísticas de esta pantalla (evolución mensual, variación de gasto, distribución por categorías, gasto medio, matriz histórica) se calculan solo con movimientos de cuentas activas — mismo criterio ya aplicado en el Dashboard (Fase 7, bugfix 2026-07-16). Filtrar los movimientos por `accountId` perteneciente a una cuenta con `active == true` antes de agregar nada.
- [x] Configurar `StatisticsFragment` como una nueva sección principal en el `BottomNavigationView`.
- [x] Añadir dependencia MPAndroidChart.
- [x] Llevar el gasto por categoria del dashboard a las estadisticas avanzadas (abajo en distribucion por categorias)
- [x] **Resumen y Evolución Mensual**: Gráfico de barras (Ingreso vs Gasto) y línea de Balance Neto (Ingreso - Gasto) mes a mes, con tarjetas resumen de totales del periodo.
- [x] **Variación del Gasto**: Mostrar el % de variación del gasto total respecto al mes anterior con indicadores visuales de tendencia (verde para ahorro, rojo para incremento).
- [x] **Distribución por Categorías**:
  - [x] Gráfico de donut interactivo con el % de gasto de cada categoría sobre el total mensual y el importe en euros.
- [x] **UX y Rendimiento**: Implementar filtros de tiempo/cuenta, estados de carga, manejo de "Sin datos" y transiciones fluidas entre gráficos. Se han simplificado las estadísticas eliminando el gasto medio mensual, el ranking de categorías y la matriz histórica de netos por decisión de UX. Se ha optimizado la carga en Dashboard y Estadísticas para evitar parpadeos y se han mejorado los gráficos con zoom, scroll y etiquetas detalladas.

## Fase 9 — Calidad, seguridad y pulido
- [x] Revisión completa de las reglas de seguridad de Firestore.
- [x] Reducir al maximo posible SIN perder funcionalidad las llamadas a firebase para no superar el limite de llamadas.
- [x] Manejo de estados vacíos (sin cuentas, sin movimientos, sin familia) y de errores de red.
- [x] Verificar que la persistencia offline de Firestore funciona razonablemente bien.
- [x] Revisión de accesibilidad básica (`contentDescription`, tamaños de texto, contraste).

## Fase 9 bis — Privacidad: consentimiento y exportación de datos
> Revisado 2026-07-21: se elimina la parte de cifrado de aplicación con Cloud KMS/Cloud Functions por exigir vincular una cuenta de facturación (plan Blaze), que el propietario descarta explícitamente. Ver `AGENTS.md`, entrada 2026-07-21. El cifrado en tránsito/reposo de Firestore ya cubre el requisito legal básico.

### Medidas organizativas y legales (no requieren código)
- [ ] (Acción manual del humano/legal) Redactar la Política de Privacidad y el Aviso Legal.
- [ ] (Acción manual del humano/legal) Revisar/aceptar el DPA de Google Cloud para Firebase y confirmar la región de Firestore.
- [x] `PrivacyConsentFragment` (`ui/onboarding/`): checkbox obligatorio de aceptación en el primer login; al aceptar, escribe `privacyPolicyAcceptedAt`.
- [x] **Self-heal para usuarios ya existentes**: si `privacyPolicyAcceptedAt` no existe, mostrarlo una única vez en el splash/loading (mismo patrón que Fase 7 bis).
- [x] **Exportación de datos personales**: opción "Descargar mis datos" en Ajustes; genera JSON con perfil, `memberships` y movimientos propios (`createdBy == uid`); compartir vía `Intent.ACTION_SEND`.

### Auditoría de las reglas de seguridad ya existentes
- [x] Ampliar la tarea de la Fase 9 "Revisión completa de las reglas de seguridad" con una pasada LOPD/RGPD: confirmar que ningún dato de un usuario es legible fuera de sus familias, y que `users/{uid}/memberships` sigue aislada por `uid`.
- [x] Documentar en `AGENTS.md` que el cifrado en tránsito/reposo de Firestore cubre el Art. 32 RGPD para datos que no son de categoría especial (Art. 9), y que no se aplica cifrado de aplicación adicional.

### Pruebas manuales de la fase
- [x] Un usuario existente ve la pantalla de consentimiento una única vez en su siguiente login y no vuelve a verla después.
- [x] "Descargar mis datos" genera un JSON solo con los datos propios del usuario, nunca de otros miembros.

## Fase 10 — Publicación
- [ ] Icono de la app, splash screen.
- [ ] Ficha de Google Play, política de privacidad.
- [ ] Preparar la firma de release: generar keystore fuera del repo, configurar `signingConfigs`.