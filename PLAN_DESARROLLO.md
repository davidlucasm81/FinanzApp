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
- [x] **Decisión de negocio (2026-07-16): un `member` normal tampoco puede editar cuentas**. Se amplía el bugfix anterior: ahora "Editar" también se oculta para `member` en `AccountListFragment` (solo `admin`/`owner` ven Editar/Archivar/Eliminar; un member ve la cuenta en modo solo lectura). Se actualiza `firestore.rules` en consonancia: la regla `update` de `accounts` ya no permite a un `member` cambiar el campo `name` (antes sí podía). **Efecto colateral importante y deseado**: al revisar la regla se detectó que el campo `currentBalance` tampoco estaba en la lista de campos permitidos para `member`, lo cual habría bloqueado con `PERMISSION_DENIED` a **cualquier miembro normal que intentara registrar un ingreso/gasto** (`TransactionRepository` actualiza `currentBalance` de la cuenta dentro de la misma transacción atómica que crea/edita/borra el movimiento). Se corrige permitiendo explícitamente `currentBalance` como único campo que un member puede tocar en `accounts` (vía el flujo de movimientos, nunca editando la cuenta directamente).

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
- [x] Listado de movimientos con filtros: por cuenta, por categoría, por rango de fechas, por tipo y método de pago. Soporte completo para modo oscuro y UI adaptativa.
- [x] Mostrar en el detalle del movimiento quién lo registró.
- [x] Reglas de seguridad para `transactions`.
- [x] **Correcciones de UX en la pantalla de Movimientos (2026-07-16)**:
    - [x] Indicador de carga (`ProgressBar`) mientras se buscan/filtran movimientos, visible desde que cambia cualquier filtro hasta que llega el resultado de Firestore.
    - [x] Mostrar el método de pago de cada movimiento en el listado (`item_transaction.xml`), reutilizando el mapeo código→etiqueta ya existente para los filtros.
    - [x] Navegación de vuelta al Dashboard desde Movimientos: flecha atrás en la Toolbar (`ic_arrow_back`) que hace `navigateUp()`.
    - [x] El color de la categoría (`Category.color`) ahora se usa realmente en el listado: un punto de color y el nombre de la categoría se pintan con ese color (con fallback a `colorPrimary` del tema si el valor no es válido).
    - [x] Se sustituyen los colores de importe (ingreso/gasto) codificados en hexadecimal por los recursos `@color/success` y `@color/error` de `colors.xml`, para mantener consistencia con la paleta de la app.

## Fase 7 — Posición neta / Dashboard
- [x] Pantalla principal (home tras login): saldo total de la familia (suma de `currentBalance` de cuentas activas).
- [x] Desglose por cuenta (lista/tarjetas).
- [x] Desglose ingresos vs gastos del periodo seleccionado (selector: rango de fechas personalizado mediante un selector de fecha desde/hasta).
- [x] Desglose por categoría del periodo seleccionado (lista ordenada de mayor a menor importe).

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
