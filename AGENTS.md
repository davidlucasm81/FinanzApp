# AGENTS.md — FinanzApp

> Este archivo debe vivir en la raíz del repositorio. Cualquier agente de IA que trabaje sobre este código (Claude Code u otro) debe leerlo **antes** de escribir una sola línea. Define el propósito, la arquitectura, el modelo de datos y las reglas de trabajo del proyecto.

## 1. Qué es esta aplicación

FinanzApp es una app Android para que una familia lleve sus finanzas juntas: ingresos, gastos y cuentas bancarias compartidas, con estadísticas y una vista clara de la posición neta del hogar.

Nombre de la app: **FinanzApp**.

- `applicationId` propuesto: `com.finanzapp.app`
- Repositorio sugerido: `finanzapp-android`

## 2. Stack tecnológico (decisiones ya tomadas, no cambiarlas sin motivo)

| Área | Decisión | Motivo |
|---|---|---|
| Lenguaje | Java (no Kotlin) | Requisito explícito del propietario del proyecto |
| Build | Gradle (Groovy DSL), Android Gradle Plugin más reciente compatible con Android Studio actual | Estándar |
| Arquitectura | MVVM (ViewModel + LiveData) + capa Repository | Separar UI de acceso a datos, testeable |
| Navegación | Single-Activity + Jetpack Navigation Component + Fragments | Evita explosión de Activities, back-stack gestionado |
| Vistas | XML + ViewBinding (no Data Binding, no Compose) | Coherente con "Java + Android Studio clásico"; menor complejidad de build |
| Inyección de dependencias | Manual, vía un `AppContainer` sencillo en la clase `Application` | Evita la complejidad de anotaciones de Hilt en un proyecto Java puro; se puede migrar más adelante si se desea |
| Autenticación | Firebase Authentication + proveedor Google, integrado con **Credential Manager** (`androidx.credentials`) | Es la API recomendada actualmente por Google/Firebase; `GoogleSignInClient`/One Tap están deprecados desde 2025 |
| Base de datos | Cloud Firestore | Tiempo real, offline-first, encaja con el modelo familiar/colaborativo |
| Gráficos/estadísticas | MPAndroidChart | Librería Java madura, muy usada en apps de finanzas personales, gratuita |
| Fechas/importes | `java.time` (API desugarizada) para fechas; `NumberFormat`/`Currency` de Java para importes | Evita bugs de zona horaria y de formato de moneda |
| IA (sugerencia de categorías) | ELIMINADO | Requisito eliminado por decisión del usuario |

Notas:
- Usa siempre el Firebase BoM (Bill of Materials) más reciente en vez de fijar versiones sueltas de cada librería; comprueba la versión actual en la consola de Firebase / documentación oficial al escribir el `build.gradle`, no la des por supuesta de memoria.
- `minSdk` recomendado: 26 (Android 8.0). `targetSdk`: el que proponga Android Studio por defecto al crear el proyecto (la versión estable más reciente en ese momento).

## 3. Estructura de paquetes propuesta

```
com.finanzapp.app/
├── FinanzAppApplication.java          // Application class, inicializa AppContainer
├── data/
│   ├── model/                    // POJOs: User, Family, Member, Invitation, Account, Transaction, Category, ImportRowResult
│   ├── repository/               // AuthRepository, FamilyRepository, AccountRepository, TransactionRepository, CategoryRepository
│   ├── firebase/                 // Constantes de rutas Firestore, mappers documento<->POJO
│   └── importer/                  // CsvTransactionParser (detección de delimitador, parseo de filas), TransactionImportRepository (resuelve/crea cuentas y categorías, escribe en batch)
├── ui/
│   ├── auth/                      // LoginActivity/Fragment
│   ├── onboarding/                // WelcomeFragment, CreateFamilyFragment, InitialAccountsFragment (alta de cuentas + posición neta inicial), JoinByCodeFragment, PendingApprovalFragment, AcceptInvitationFragment
│   ├── settings/                  // SettingsFragment, ProfileFragment
│   ├── family/                    // FamilySettingsFragment, MemberListFragment, InviteMemberFragment, FamilySwitcherFragment (selector de familia activa), MyFamiliesFragment (listado de todas las familias del usuario)
│   ├── accounts/                  // AccountListFragment, AddEditAccountFragment
│   ├── transactions/              // AddEditTransactionFragment, TransactionListFragment, filtros, ImportTransactionsFragment (importación CSV)
│   ├── categories/                // ManageCategoriesFragment
│   ├── dashboard/                 // DashboardFragment (posición neta)
│   └── statistics/                // StatisticsFragment + subvistas de gráficos
├── viewmodel/                      // Un ViewModel por pantalla (o colocado junto a cada paquete de ui/, a elección del agente)
└── util/                           // Constants, CurrencyFormatter, DateUtils, Result<T>, InputValidators, CategoryColorPalette (paleta fija usada para categorías por defecto y para nuevas categorías creadas automáticamente)
```

## 4. Modelo de datos en Firestore

Todas las subcolecciones de una familia cuelgan de `families/{familyId}` para que las reglas de seguridad sean simples: "si eres miembro aprobado de esa familia, puedes leer/escribir en sus subcolecciones".

```
users/{uid}
  displayName: string
  email: string
  photoUrl: string
  activeFamilyId: string | null   // familia actualmente seleccionada en la UI; null solo si el usuario no pertenece a ninguna familia. Desde la Fase 7 bis, un usuario puede pertenecer a N familias a la vez (ver `families/{familyId}/members/{uid}` como fuente de verdad de la lista); este campo NO es la lista de familias, solo cuál está activa.
  createdAt: timestamp

families/{familyId}
  name: string
  currencyCode: string            // ISO 4217, ej. "EUR", "USD"
  inviteCode: string              // código corto único, ej. 6-8 caracteres alfanuméricos
  createdBy: uid
  createdAt: timestamp

families/{familyId}/members/{uid}
  uid: string                     // (Fase 7 bis) duplicado del id del documento; necesario porque Firestore no permite filtrar un collectionGroup query por el id del documento padre. Es lo que permite construir "a qué familias pertenece este usuario" con `collectionGroup("members").whereEqualTo("uid", uid)` en vez de mantener una lista redundante en `users/{uid}` que podría desincronizarse.
  displayName: string
  email: string
  role: "owner" | "admin" | "member"
  status: "approved"              // el doc solo se crea cuando ya está aprobado
  joinedAt: timestamp
  familyName: string               // (Fase 7 bis) desnormalizado desde `families/{familyId}.name`, para poder pintar el selector de familias (nombre + rol) con una única lectura del collectionGroup, sin N+1 lecturas a `families/{familyId}`. Debe mantenerse sincronizado: cualquier cambio de `families/{familyId}.name` (en `FamilySettingsFragment`) debe propagarse también a `familyName` en todos sus documentos `members/*` (batch write).

families/{familyId}/invitations/{invitationId}
  type: "email_invite" | "code_request"
  targetEmail: string | null      // solo si type = email_invite
  requestedByUid: string | null   // solo si type = code_request
  invitedByUid: string | null     // admin que invita, si aplica
  status: "pending" | "accepted" | "approved" | "rejected"
  createdAt: timestamp
  resolvedAt: timestamp | null
  resolvedByUid: string | null

families/{familyId}/accounts/{accountId}
  name: string
  initialBalance: number
  currentBalance: number          // desnormalizado, se recalcula con cada movimiento (Firestore transaction)
  active: boolean
  createdBy: uid
  createdAt: timestamp

  // `initialBalance` NO es inmutable: puede editarse en cualquier momento tras crear la cuenta
  // (por ejemplo para corregir la posición neta inicial). Cada edición debe recalcular
  // `currentBalance` mediante un delta dentro de la misma Firestore transaction:
  //   currentBalance_nuevo = currentBalance_actual + (initialBalance_nuevo − initialBalance_anterior)
  // para no perder el efecto de los movimientos ya registrados. Ver Fase 4 del plan.

families/{familyId}/categories/{categoryId}
  name: string
  appliesTo: "income" | "expense" | "both"
  icon: string
  color: string
  isDefault: boolean
  createdBy: uid | null           // null si es una categoría por defecto del sistema

families/{familyId}/transactions/{transactionId}
  accountId: string
  date: timestamp
  description: string
  amount: number                  // siempre positivo; el signo lo da "type"
  type: "income" | "expense"
  categoryId: string
  paymentMethod: "tarjeta" | "transferencia" | "efectivo" | "bizum" | "tarjeta_restaurante" | "tarjeta_transporte" | "domiciliacion_bancaria"
  createdBy: uid
  createdAt: timestamp
```

### Pertenencia a varias familias (Fase 7 bis)

> Requisito nuevo (2026-07-18). Hasta la Fase 7 bis, un usuario solo podía pertenecer a una familia a la vez (`users/{uid}.familyId`); esto se elimina de la lista de "fuera de alcance" (sección 10) y pasa a ser un requisito de la v1.

- **Fuente de verdad de "a qué familias pertenezco"**: la colección `families/{familyId}/members/{uid}` (ya existente), consultada como `collectionGroup("members")` filtrando por el nuevo campo `uid`. No se mantiene una lista de familias en `users/{uid}`, para evitar dos fuentes de verdad que puedan desincronizarse (por ejemplo, si se expulsa a alguien y se olvida actualizar la lista).
- **Familia activa**: `users/{uid}.activeFamilyId` sigue existiendo con el mismo propósito que antes (qué familia ve el usuario ahora mismo), pero deja de ser "la única familia del usuario" para pasar a ser "la familia seleccionada en el selector". Toda la app (Cuentas, Movimientos, Categorías, Estadísticas, Dashboard, Miembros) sigue funcionando exactamente igual que hasta ahora tomando como referencia un único `familyId` en cada pantalla — ese `familyId` pasa a alimentarse siempre de `activeFamilyId` en vez de asumir que es el único que existe.
- **Cambiar de familia activa** es una operación explícita del usuario (selector de familias, ver más abajo) que solo escribe `users/{uid}.activeFamilyId`; no mueve datos ni afecta a otros miembros.
- **Cambiar de familia implica limpiar la navegación**: todas las pantallas por debajo del Dashboard (Cuentas, Movimientos, Categorías, Estadísticas, Miembros) muestran datos de la familia activa; al cambiar de familia hay que volver siempre al Dashboard (`popBackStack` hasta el grafo raíz) y forzar la recarga de todos los `ViewModel` que dependan de `familyId`, para no mezclar ids de cuentas/categorías/movimientos de una familia con otra.
- **Selector de familias**: nuevo `FamilySwitcherFragment`, accesible desde el Dashboard (tocando el nombre de la familia en la cabecera) y desde Ajustes. Lista, para el usuario actual, todas sus familias (nombre, rol, indicador de cuál es la activa) leyendo el collectionGroup descrito arriba, permite cambiar de activa, y ofrece dos accesos: "Crear otra familia" y "Unirme a otra familia por código", que reutilizan `CreateFamilyFragment`/`JoinByCodeFragment` (Fase 2) en un "modo añadir familia" en vez del modo "onboarding inicial".
- **Onboarding**: el *routing* tras el login deja de mirar `familyId == null` y pasa a mirar si el `collectionGroup` de familias del usuario está vacío. Si está vacío → onboarding (crear/unirse a la primera familia), igual que hasta ahora. Si no está vacío pero `activeFamilyId` es `null` o ya no corresponde a ninguna de sus familias (por ejemplo, fue expulsado de la que tenía activa) → fijar automáticamente como activa la primera familia disponible y continuar a Dashboard, sin pasar por onboarding. Si no está vacío y `activeFamilyId` es válido → Dashboard directo.
- **Crear/unirse a una familia estando ya dentro de la app** (no en onboarding): reutiliza los mismos flujos de la Fase 2, pero al terminar con éxito no se sobreescribe `activeFamilyId` automáticamente si el usuario ya tenía una familia activa — se pregunta si quiere cambiar ahora a la familia recién creada/unida o seguir donde estaba.
- **Invitaciones por email a un usuario que ya tiene familia**: hasta ahora solo se comprobaban en `WelcomeFragment` (antes de tener familia). Con multi-familia, un usuario que ya está dentro de la app debe poder recibir y aceptar invitaciones a una familia adicional. Se añade una comprobación periódica/al abrir sesión (no solo en onboarding) de invitaciones `email_invite` pendientes para el email del usuario en cualquier familia, con un aviso in-app (badge o diálogo) en vez de solo en la pantalla de bienvenida.
- **Abandonar una familia**: si al usuario le quedan otras familias tras salir, se cambia automáticamente `activeFamilyId` a otra de las restantes y se permanece en Dashboard (ya no se navega a onboarding). Solo si esa era su última familia se navega a onboarding, igual que el comportamiento actual.
- **Borrado de cuenta de usuario**: debe recorrer *todas* las familias del usuario (mismo collectionGroup) y aplicar en cada una la lógica ya existente de "abandonar familia" (incluyendo traspaso de `owner` o deep-delete si es el único miembro), no solo en la familia activa.
- **Asunciones tomadas al no estar especificado en el enunciado original** (documentadas también en la sección "Decisiones tomadas durante el desarrollo"):
  - No se comprueba ni se avisa si un email invitado ya pertenece a otra familia: no hay forma de comprobarlo sin exponer datos de otro usuario a quien invita, así que el flujo de invitación no cambia.
  - La pantalla "Mis familias" (listado completo, fuera del selector rápido) es de solo lectura + cambio de activa; para abandonar una familia concreta hay que cambiar primero a ella y usar el botón "Salir de la familia" ya existente en Ajustes de familia, en vez de duplicar la lógica de traspaso de `owner`/deep-delete en dos sitios distintos.
  - El campo `uid` en `members/{uid}` no existe en los documentos creados antes de esta fase; en vez de un script de migración con Admin SDK (no hay Cloud Functions en este proyecto), se aplica "self-heal" en cliente: si un usuario abre su propio documento de member y detecta que le falta `uid` (o `familyName`), lo rellena con un `update` en ese momento.

### Aclaración sobre un requisito ambiguo del enunciado original

El requisito 6 mencionaba un campo **"tipo de moneda (tarjeta, transferencia, efectivo, bizum)"**. Esos valores no son monedas (EUR/USD), son **métodos de pago**. Se ha interpretado que hay dos campos distintos:
- `amount` junto con la moneda de la familia (`currencyCode`, fijada a nivel de unidad familiar) → el valor económico del movimiento.
- `paymentMethod` → cómo se pagó (ver lista completa más abajo).

Si esta interpretación no es correcta, corrígela antes de que el agente empiece a implementar transacciones (Fase 5 del plan).

### Métodos de pago (`paymentMethod`)

| Valor interno | Etiqueta visible |
|---|---|
| `tarjeta` | Tarjeta |
| `efectivo` | Efectivo |
| `transferencia` | Transferencia |
| `bizum` | Bizum |
| `tarjeta_restaurante` | Tarjeta restaurante |
| `tarjeta_transporte` | Tarjeta transporte |
| `domiciliacion_bancaria` | Domiciliación bancaria |

`domiciliacion_bancaria` se ha añadido porque en España es muy habitual pagar así recibos recurrentes (Hipoteca, Seguros, Servicios) — sin él, esos movimientos no tendrían un método de pago natural. Quítalo si no lo quieres.

### Categorías por defecto

Basado en tu lista, organizada por tipo (nota: en tu tabla original "Hipoteca" aparecía junto a "Ingreso" y "Reformas" junto a "Gasto", pero eso era el cruce accidental de dos columnas independientes de la hoja de cálculo — Hipoteca es un gasto). Se han añadido algunas categorías de ingreso adicionales, ya que la lista original solo traía "Nómina", y unas pocas de gasto habituales en una economía familiar española que no estaban (Impuestos, Comunidad, Mascotas, Donaciones). Todas son editables/eliminables desde la app; esto es solo el set semilla.

Desde el 2026-07-17, cada categoría del set semilla lleva también un `color` fijo (hex `#RRGGBB`), asignado a mano para que la primera vez que la familia abre la app las categorías ya se vean distinguibles entre sí sin que nadie tenga que personalizarlas. Estos mismos 33 colores forman la paleta `CategoryColorPalette` (`util/`), que se reutiliza para: (a) colorear cualquier categoría nueva creada automáticamente durante la importación CSV (sección "Importación de movimientos desde CSV" más abajo) — eligiendo el primer color de la paleta no usado todavía por otra categoría de la misma familia, o si ya se han agotado los 33, generando uno determinista a partir de un hash del nombre; y (b) proponer color en las sugerencias de categorías por IA (ver "Sugerencia de categorías por IA"). El color de una categoría, sea semilla o creada automáticamente, sigue siendo editable a mano por el usuario en cualquier momento (Fase 5), esto no cambia.

**Ingreso**

| Categoría | `appliesTo` | Color |
|---|---|---|
| Nómina | income | `#2E7D32` |
| Otros ingresos | income | `#558B2F` |

**Gasto**

| Categoría | `appliesTo` | Color |
|---|---|---|
| Hipoteca | expense | `#6D4C41` |
| Reformas | expense | `#8D6E63` |
| Servicios | expense | `#455A64` |
| Internet | expense | `#1E88E5` |
| Seguros | expense | `#3949AB` |
| Supermercado | expense | `#F4511E` |
| Restaurantes | expense | `#FB8C00` |
| Alcohol | expense | `#6A1B9A` |
| Transporte | expense | `#039BE5` |
| Salud | expense | `#E53935` |
| Ropa | expense | `#D81B60` |
| Educación | expense | `#5E35B1` |
| Ocio | expense | `#F9A825` |
| Viajes | expense | `#00ACC1` |
| Ahorros | expense | `#7CB342` |
| Informática | expense | `#546E7A` |
| Libros | expense | `#8E24AA` |
| Streaming | expense | `#C2185B` |
| Deporte | expense | `#26A69A` |
| Bebidas | expense | `#FFB300` |
| Peluquería | expense | `#EC407A` |
| Regalos | expense | `#AB47BC` |
| Hogar | expense | `#A1887F` |
| Misceláneo | expense | `#78909C` |
| Impuestos | expense | `#C62828` |
| Comunidad | expense | `#4527A0` |
| Mascotas | expense | `#FF7043` |
| Donaciones | expense | `#66BB6A` |

Ninguna categoría usa `appliesTo: "both"` en el set semilla; si el usuario necesita una categoría mixta (por ejemplo "Ajustes") puede crearla manualmente marcándola como tal.

### Importación de movimientos desde CSV

Requisito nuevo (2026-07-17): permitir importar movimientos en bloque desde un fichero exportado por el usuario (por ejemplo desde una hoja de cálculo), con esta cabecera y orden de columnas:

```
Fecha	Concepto	Categoría	Valor	Tipo	Método	Cuenta
```

Reglas de importación (`data/importer/`):
- **Delimitador**: el fichero puede venir con tabulador, coma o punto y coma; `CsvTransactionParser` detecta el delimitador a partir de la línea de cabecera (probar en ese orden) en vez de asumir uno fijo, porque distintas hojas de cálculo exportan de forma distinta. La primera línea siempre se trata como cabecera y se descarta.
- **Fecha → `date`**: formato esperado `dd/MM/yyyy`. Si una fila no puede parsearse con ese formato, la fila se descarta y se añade al informe de errores; no se intenta adivinar otros formatos para no importar fechas incorrectas silenciosamente.
- **Concepto → `description`**: texto libre, tal cual.
- **Categoría → `categoryId`**: se busca una categoría existente en la familia con ese `name` (case-insensitive) y `appliesTo` compatible con el `Tipo` de la fila (`income`/`expense`, o `both`). Si no existe, se crea una categoría nueva (`isDefault: false`, `createdBy: uid` del importador, `color` tomado de `CategoryColorPalette` como se describe arriba).
- **Valor → `amount`**: se acepta coma o punto como separador decimal. `amount` en el modelo de datos es siempre positivo (sección 4), así que se usa el valor absoluto; el signo/tipo del movimiento lo decide únicamente la columna `Tipo`, nunca el signo de `Valor` (si contradicen, gana `Tipo`, documentado aquí para que no sea un criterio inventado sobre la marcha).
- **Tipo → `type`**: `"Ingreso"` → `income`, `"Gasto"` → `expense` (case-insensitive). Cualquier otro valor descarta la fila y se reporta como error.
- **Método → `paymentMethod`**: se compara (case-insensitive, ignorando acentos) contra las **etiquetas visibles** de la tabla de métodos de pago de este documento (Tarjeta, Efectivo, Transferencia, Bizum, Tarjeta restaurante, Tarjeta transporte, Domiciliación bancaria). Si no hay coincidencia, la fila se descarta y se reporta como error — no se asume un método por defecto, porque en un movimiento financiero el método de pago no es un dato que el agente deba inventar (ver sección 9).
- **Cuenta → `accountId`**: se busca una cuenta existente en la familia con ese `name` (case-insensitive). Si no existe, se crea automáticamente con `initialBalance: 0`, `active: true`, `createdBy: uid` del importador.
- **Escritura**: usar `WriteBatch` de Firestore para las filas válidas (límite de 500 operaciones por batch, partir en varios batches si hace falta) en vez de una Firestore transaction por fila — sería demasiado lento para una importación de cientos de filas. El `currentBalance` de cada cuenta afectada se actualiza con `FieldValue.increment(deltaTotalDeLaCuenta)` (suma de los importes con signo de todas las filas válidas que apuntan a esa cuenta), aplicado en el mismo batch. Esto es una excepción documentada al patrón "una Firestore transaction por movimiento" usado en el resto de la app (Fase 6): se acepta porque la importación es una única operación lógica de alta, `increment()` sigue siendo atómico a nivel de campo, y evita cientos de round-trips secuenciales.
- **Resultado**: al terminar, mostrar un resumen (movimientos importados, cuentas nuevas creadas, categorías nuevas creadas, filas descartadas con el número de fila y el motivo) antes de que el usuario navegue fuera de la pantalla.
- **Permisos**: como la importación puede crear categorías, y la gestión de categorías está restringida a `admin`/`owner` (Fase 5, sección 5), la pantalla de importación (`ImportTransactionsFragment`) solo es accesible para `admin`/`owner`, igual que "Gestionar categorías".
- **Selección de fichero**: usar Storage Access Framework (`ACTION_OPEN_DOCUMENT`) para que el usuario elija el fichero desde donde quiera (no requiere subirlo a Firebase Storage, se lee y se descarta localmente); no añade ninguna dependencia nueva.

### Sugerencia de categorías por IA (ELIMINADO)

Este requisito ha sido eliminado por decisión del usuario.

### Aclaración sobre la aprobación de invitaciones

El enunciado pide que el creador de la familia apruebe la incorporación. Para que ambos caminos de unión tengan sentido a la vez, se ha diseñado así:
- **Invitación por correo**: la inicia el admin (ya es una aprobación implícita, porque el admin elige a quién invita). Cuando el invitado la acepta, entra directamente como `member` aprobado.
- **Unión por código**: la inicia cualquier persona que conozca el código (que puede haberse compartido fuera del control del admin), así que genera una solicitud (`code_request`) que el admin debe aprobar o rechazar explícitamente antes de que la persona pase a ser `member`.

## 5. Reglas de seguridad de Firestore (esqueleto conceptual)

Implementar (y testear con el Firebase Emulator Suite) algo equivalente a:
- Un usuario solo puede leer/escribir su propio documento en `users/{uid}`.
- Solo se puede leer/escribir en `families/{familyId}/**` si `request.auth.uid` existe como documento en `families/{familyId}/members`, o si el usuario está en proceso de crear la familia o resolver su propia invitación.
- Solo un `member` con `role == "admin"` o `role == "owner"` puede: aprobar/rechazar `code_request`, cambiar `currencyCode`, editar una cuenta ya existente (nombre, `initialBalance`, `active`), archivar o eliminar cuentas, gestionar categorías del sistema, y cambiar el rol de otros miembros. Un member normal puede leer y crear cuentas nuevas, pero no editar, archivar ni eliminar las existentes.
- **Jerarquía de Roles**: Solo el `owner` puede cambiar el rol de un `admin`. Un `admin` solo puede cambiar el rol de un `member`. Nadie puede cambiar el rol del `owner`.
- Al crear una familia, el creador recibe el rol de `owner`.
- Al abandonar la familia, si el que sale es el `owner`, la propiedad debe traspasarse a otro miembro (priorizando `admin`).
- Nadie escribe directamente `currentBalance` de una cuenta salvo a través de la misma transacción de Firestore que crea/edita/borra el movimiento correspondiente, o que edita el `initialBalance` de la cuenta (para que nunca se descuadre).
- Una cuenta solo puede eliminarse físicamente si no tiene ningún movimiento asociado; si ya tiene movimientos, solo puede archivarse/desactivarse (`active: false`), nunca borrarse, para no perder el histórico.
- La importación CSV escribe categorías nuevas, así que queda sujeta a la misma regla que ya restringe "gestionar categorías del sistema" a `admin`/`owner`; las reglas de Firestore no necesitan un caso especial nuevo, pero si `TransactionRepository`/`CategoryRepository` exponen un modo "batch" para la importación, ese modo debe seguir pasando por las mismas reglas de creación de `accounts`, `categories` y `transactions` ya definidas (nada de un camino alternativo sin reglas).
- **(Fase 7 bis) Lectura del propio documento de member vía `collectionGroup("members")`**: además de la regla ya existente ("si eres miembro aprobado de esa familia, puedes leer sus subcolecciones"), se añade `allow read: if resource.data.uid == request.auth.uid`, para que un usuario pueda listar sus propios documentos de member en *cualquier* familia (necesario para el selector de familias) sin poder leer los de otros miembros de familias a las que no pertenece.
- **(Fase 7 bis) `users/{uid}.activeFamilyId`**: el propio usuario puede escribir este campo únicamente si `request.resource.data.activeFamilyId` es `null` o corresponde a una familia donde ya existe como `members/{uid}` con `status: approved` (comprobar con `exists()` sobre `families/$(request.resource.data.activeFamilyId)/members/$(request.auth.uid)`), para que nadie pueda "activar" una familia a la que no pertenece.
- **(Fase 7 bis) `familyName` desnormalizado en `members/{uid}`**: solo se puede escribir junto con el resto de campos del member (alta, self-heal) o como parte de la actualización de `families/{familyId}.name` por un `admin`/`owner` (mismo permiso ya exigido para cambiar el nombre de la familia); nunca de forma independiente por un miembro cualquiera.

Este es un punto crítico: no dejarlo para el final, implementarlo en cuanto exista el modelo de datos (Fase 2 del plan). Las reglas de la Fase 7 bis se añaden e implementan junto con esa fase, no antes (dependen del campo `uid` en `members`).

## 6. Gestión de credenciales y api keys (requisito no negociable)

- `google-services.json` **nunca** se sube al repo. Debe estar en `.gitignore` desde el primer commit.
- Añadir un `google-services.json.example` (con estructura ficticia) y documentar en el `README.md` cómo cada desarrollador obtiene el suyo desde la consola de Firebase.
- Cualquier otra clave (por ejemplo, si en el futuro se usa alguna API de terceros) va en `local.properties` (ya ignorado por defecto por Android Studio) y se expone al código vía `BuildConfig`, nunca hardcodeada en `.java` ni en `strings.xml`.
- El keystore de firma de release (`*.jks`/`*.keystore`) tampoco se sube al repo.
- `.gitignore` mínimo desde el primer commit:
  ```
  local.properties
  google-services.json
  *.jks
  *.keystore
  .gradle/
  /build/
  /captures/
  .idea/
  *.iml
  ```
- Antes de cada commit, comprobar con `git status` que ninguno de estos ficheros está en staging.

## 7. Requisitos funcionales (resumen operativo)

1. **Login**: Firebase Auth + Google vía Credential Manager. Persistencia de sesión automática.
2. **Almacenamiento**: Cloud Firestore como única fuente de verdad; habilitar persistencia offline del SDK.
3. **Secretos**: ver sección 6.
4. **Alta/unión a familia**: crear familia (nombre + moneda) o unirse (código o invitación por email), con aprobación de admin para el caso de código. Al crear una familia, el propio asistente permite además dar de alta las cuentas iniciales y así fijar la posición neta inicial (puede omitirse y hacerse después). Ver sección 4.
5. **Cuentas bancarias**: N cuentas por familia, cada una con nombre, saldo inicial y saldo actual mantenido de forma atómica. El saldo inicial de cualquier cuenta puede editarse en cualquier momento (recalculando el saldo actual de forma atómica) y pueden crearse nuevas cuentas o eliminarse las existentes en cualquier momento (el borrado físico solo si la cuenta no tiene movimientos; en caso contrario, se archiva).
6. **Movimientos**: fecha, descripción, importe, tipo (gasto/ingreso), categoría, método de pago, cuenta asociada.
7. **Posición neta**: pantalla resumen con saldo total, desglose por cuenta, por ingresos/gastos del periodo, y por categoría.
8. **Estadísticas avanzadas (Pestaña Independiente)**: sección dedicada con evolución mensual (ingreso/gasto/neto), variación porcentual respecto al mes anterior, distribución por categoría (donut + % sobre total), ranking de categorías, gasto medio por categoría/cuenta y matriz histórica de netos. Uso de MPAndroidChart y enfoque en alta UX (filtros, estados de carga y visualización clara).
9. **Importación de movimientos desde CSV** (solo `admin`/`owner`): dado un fichero con columnas `Fecha Concepto Categoría Valor Tipo Método Cuenta`, insertar los movimientos correspondientes; si la cuenta o la categoría de una fila no existen en la familia, crearlas automáticamente. Ver detalle en la sección 4, "Importación de movimientos desde CSV".
10. **Sugerencia de categorías por IA** (ELIMINADO): Requisito eliminado por decisión del usuario.
11. **Pertenencia a varias familias** (Fase 7 bis): un usuario puede pertenecer a N familias a la vez, cambiar entre ellas mediante un selector de familia activa, y crear o unirse a familias adicionales sin dejar de pertenecer a las anteriores. Ver detalle en la sección 4, "Pertenencia a varias familias (Fase 7 bis)".

## 8. Convenciones de código

- Java, estilo estándar de Android (clases en PascalCase, métodos/variables en camelCase, constantes en UPPER_SNAKE_CASE).
- Un `Repository` por entidad; solo los `Repository` hablan directamente con Firestore. Los `ViewModel` nunca importan clases de `com.google.firebase.firestore` directamente.
- Usar `LiveData` para exponer datos observables a la UI; evitar callbacks anidados en la UI.
- Cada pantalla nueva = 1 `Fragment` + 1 `ViewModel` + (si aplica) actualización de `Repository`.
- Commits pequeños y descriptivos, idioma consistente en todo el repo.
- No añadir dependencias nuevas sin justificarlo brevemente en el commit o en este archivo.

## 9. Cómo debe trabajar el agente de código

- Seguir `PLAN_DESARROLLO.md` fase por fase, en orden; no saltar fases aunque parezcan independientes (cada fase asume que la anterior compila y funciona).
- Antes de cada fase, releer la sección relevante de este archivo.
- Si una fase requiere una acción manual que el agente no puede hacer (crear el proyecto de Firebase, habilitar el proveedor Google en la consola, generar el SHA-1, etc.), detenerse y pedir al humano que la realice, dejando claro qué hace falta recibir de vuelta (por ejemplo, el `google-services.json` actualizado).
- Marcar cada tarea del plan como completada (`- [x]`) al terminarla, para que el progreso quede registrado en el propio repositorio.
- **Mantenimiento del Plan**: Si durante el desarrollo se implementan funcionalidades, mejoras de UX o correcciones que no estaban contempladas originalmente en `PLAN_DESARROLLO.md`, el agente **debe** añadirlas al plan (preferiblemente en una sección de "Mejoras" o dentro de la fase correspondiente) para mantener la trazabilidad del proyecto.
- Ante cualquier ambigüedad de negocio (no técnica) que no esté resuelta en este documento, no inventar: preguntar o documentar la asunción tomada en la sección "Decisiones tomadas durante el desarrollo" de este mismo archivo.

## 10. Fuera de alcance en la v1 (posible trabajo futuro)

- Multi-divisa con conversión automática entre cuentas de distinta moneda.
- ~~Un usuario perteneciendo a más de una unidad familiar a la vez.~~ Pasa a ser un requisito de la v1 desde 2026-07-18 — ver Fase 7 bis en `PLAN_DESARROLLO.md` y la sección "Pertenencia a varias familias" en el punto 4 de este documento.
- Movimientos recurrentes/automatizados y alertas de presupuesto.
- Exportación a Excel/PDF.
- Notificaciones push (FCM) para solicitudes de unión pendientes.
- Modo oscuro (fácil de añadir después, no bloquea nada).

---

## Decisiones tomadas durante el desarrollo

*(el agente debe ir añadiendo aquí cualquier decisión relevante no cubierta arriba, con fecha)*

- **2026-07-11**: Definido el set semilla de categorías (5 de ingreso, 28 de gasto) y de métodos de pago (7 valores, incluyendo `tarjeta_restaurante`, `tarjeta_transporte` y `domiciliacion_bancaria`) — ver sección 4.
- **2026-07-11**: Se añade soporte para abandonar la familia. Si la familia se queda sin miembros, se debe realizar un borrado recursivo de todas sus subcolecciones para no dejar datos huérfanos en Firestore (limpieza de `accounts`, `transactions`, `categories`, etc.).
- **2026-07-11**: Reestructuración de fases para priorizar la gestión de familia (Miembros e Invitaciones) a la Fase 3. Se confirma que cada movimiento (`Transaction`) debe guardar obligatoriamente el `createdBy` (UID del autor) para mostrarlo en la UI.
- **2026-07-13**: Nuevo requisito: el asistente de creación de familia (Fase 2) incorpora un paso adicional para dar de alta las cuentas iniciales y fijar así la posición neta inicial de la familia; el usuario puede omitirlo y añadir cuentas más tarde desde Ajustes. Como consecuencia, se adelanta desde la Fase 4 una versión mínima del modelo `Account`, `AccountRepository` y el formulario de alta de cuenta, para que estén disponibles durante el onboarding (el resto de la Fase 4 —listado, archivado— se completa después). Asunciones tomadas al no estar especificado en el enunciado original:
  - El saldo inicial (`initialBalance`) de una cuenta deja de ser fijo tras la creación: puede editarse en cualquier momento posterior, recalculando `currentBalance` mediante un delta dentro de la misma Firestore transaction, para no perder el efecto de los movimientos ya registrados.
  - Igual que sucede con cambiar `currencyCode`, tanto la edición del saldo inicial como el archivado/borrado de una cuenta quedan restringidos a miembros con `role: admin`; la creación de cuentas sigue abierta a cualquier miembro aprobado, como ya estaba definido.
  - Una cuenta solo se borra físicamente si no tiene ningún movimiento asociado; si ya tiene movimientos, la app no permite el borrado y ofrece únicamente archivar/desactivar, para no perder histórico.
- **2026-07-13**: Reorganización de la arquitectura de ajustes: el perfil de usuario se desplaza a la foto del Dashboard y la configuración de familia a la barra de navegación inferior. Se añade soporte para carga de imágenes con Glide y una capa de seguridad extra (verificación por texto) para el borrado de cuentas.
- **2026-07-13**: Implementado sistema de "Deep Delete" para familias (borrado recursivo de subcolecciones) y detección automática de invitaciones por email en la pantalla de bienvenida.
- **2026-07-13**: Se elimina el campo `type` del modelo `Account`. No existen tipos de cuenta: una cuenta se define únicamente por `name` e `initialBalance` (además de `currentBalance`, `active`, `createdBy`, `createdAt`). Afecta al modelo `Account`, al formulario de alta/edición de cuenta (onboarding y Fase 4) y a cualquier UI que mostrara el tipo.
- **2026-07-16**: Refuerzo de UX en el listado de cuentas (Fase 4): las acciones "Archivar" y "Eliminar" pasan a ser mutuamente excluyentes y visibles según el estado real de la cuenta, en vez de mostrarse siempre las tres acciones y descubrir la restricción al fallar el borrado. Regla aplicada: si la cuenta tiene algún movimiento asociado, solo se ofrece Archivar (además de Editar); si no tiene ninguno, solo se ofrece Eliminar (además de Editar). Para ello, `Account` incorpora un campo transitorio `hasTransactions` (no persistido, `@Exclude` en Firestore) que `AccountRepository.getAccounts()` calcula cruzando en tiempo real con la colección `transactions` de la familia. La regla de negocio en sí (no permitir el borrado físico de cuentas con movimientos) ya estaba definida y aplicada a nivel de `Repository`/reglas de seguridad desde la Fase 4 original; este cambio solo la refleja también en la interfaz para evitar que el usuario intente una acción que sabemos de antemano que será rechazada.
- **2026-07-16**: Bugfix — un `member` normal obtenía `PERMISSION_DENIED` al intentar archivar o eliminar una cuenta, porque `AccountListFragment` mostraba esos botones a cualquier miembro sin comprobar su rol, mientras que las reglas de Firestore (sección 5) restringen ambas acciones a `admin`/`owner`. Se añade en el fragmento una comprobación en tiempo real del rol del usuario (`families/{familyId}/members/{uid}.role`) y los botones "Archivar"/"Eliminar" ahora solo se muestran si el usuario es `admin`/`owner`; un `member` normal solo ve "Editar". Queda pendiente verificar `AddEditAccountFragment` (no revisado en este cambio) para confirmar que tampoco permite a un `member` modificar `initialBalance`, ya que la regla de `update` de `accounts` solo permite a un member cambiar `name`.
- **2026-07-16**: Ampliación de la decisión anterior — se decide que un `member` normal tampoco puede editar cuentas en absoluto (ni siquiera el nombre). `AccountListFragment` oculta también "Editar" para member (solo admin/owner ven las tres acciones; member ve la cuenta en modo lectura). `firestore.rules` se actualiza en consecuencia: la regla `update` de `accounts` deja de permitir a un member cambiar `name`. Al revisar esa regla se detectó y corrigió un bug más grave y no relacionado con lo pedido: el campo `currentBalance` tampoco estaba entre los campos permitidos para member, lo que habría impedido a cualquier member registrar movimientos (`TransactionRepository` actualiza `currentBalance` de la cuenta como parte de la misma transacción atómica que crea/edita/borra el `Transaction`). Se corrige permitiendo explícitamente que un member modifique `currentBalance` — únicamente a través de ese flujo de movimientos, nunca editando la cuenta directamente (la UI de edición de cuenta sigue oculta para member).
- **2026-07-17**: **Bugfix — Toasts fantasmas al entrar en pantallas de listado (Cuentas, Movimientos, etc.).** Causa real: `AccountViewModel` (y otros) utilizaban `MutableLiveData` para los resultados de operaciones asíncronas. Como `LiveData` es un "state holder", al navegar de vuelta a un fragmento y re-suscribirse, el `LiveData` entregaba inmediatamente el último `Result.Success` guardado, provocando que se mostraran de nuevo los `Toast` de acciones que el usuario ya había completado. Solución:
  - Se añade `SingleLiveEvent.java` en `util/`. Es una subclase de `MutableLiveData` que lleva un flag interno (`mPending`) para asegurar que el valor solo se entrega una vez; las suscripciones posteriores no reciben el valor anterior.
  - Se migran todos los campos de resultados de acciones en los ViewModels (`AccountViewModel`, `TransactionViewModel`, `FamilyViewModel`, `CategoryViewModel`, `OnboardingViewModel`) de `MutableLiveData` a `SingleLiveEvent`.
  - Con esto, los `Toast` (y cualquier otra acción de un solo uso como navegaciones tras éxito) solo ocurren exactamente una vez tras la llamada al `setValue`/`postValue`, resolviendo los avisos duplicados al navegar.
- **2026-07-17**: Nuevo requisito: **importación de movimientos desde CSV** y **sugerencia de categorías por IA**. Ver el detalle completo de ambas en la sección 4 (subsecciones "Importación de movimientos desde CSV" y "Sugerencia de categorías por IA") y las tareas correspondientes en `PLAN_DESARROLLO.md`. Resumen de las asunciones tomadas al no estar especificadas en el enunciado original:
  - El fichero puede venir separado por tabulador, coma o punto y coma; se detecta automáticamente en vez de asumir uno fijo.
  - La fecha se asume en formato `dd/MM/yyyy`; una fila con fecha no parseable se descarta y se reporta, no se intenta adivinar el formato.
  - El signo/tipo del movimiento lo decide siempre la columna `Tipo`, nunca el signo de `Valor` (que se toma en valor absoluto).
  - El método de pago debe coincidir (case-insensitive, ignorando acentos) con una de las etiquetas ya definidas en la tabla de métodos de pago de este documento; si no coincide con ninguna, la fila se descarta y se reporta como error en vez de asumir un método por defecto.
  - Cuentas y categorías que no existan se crean automáticamente; las categorías nuevas creadas así toman color de la paleta `CategoryColorPalette` (la misma paleta fija de las categorías semilla, ver sección 4).
  - Por volumen, la importación escribe en `WriteBatch` (no una Firestore transaction por fila) y actualiza `currentBalance` de cada cuenta afectada con `FieldValue.increment()`, en vez del patrón "una transaction por movimiento" usado en el alta manual (Fase 6); se documenta como excepción deliberada.
  - Al crear una familia, las categorías semilla pasan a llevar también un `color` fijo predefinido (antes no se especificaba color en la siembra); los 33 colores se listan en la tabla de categorías por defecto de la sección 4.
  - La sugerencia de categorías por IA usa Firebase AI Logic (Gemini) en vez de llamar directamente a una API de terceros desde el cliente, para no tener que gestionar una API key nueva en la app (coherente con la sección 6). Las sugerencias nunca se guardan automáticamente: el usuario debe confirmarlas una a una mediante checklist antes de crear nada en Firestore.
  - Tanto la importación CSV como el botón de sugerencia de categorías por IA quedan restringidos a `admin`/`owner`, igual que el resto de gestión de categorías (Fase 5), porque ambas funciones pueden crear categorías.
- **2026-07-17**: **Nuevo requisito de UX: navegación desde el desglose por categoría del Dashboard a Movimientos.** Al pulsar una categoría en el desglose de gasto por categoría del Dashboard, la app navega a `TransactionListFragment` con el filtro de categoría preseleccionado (esa categoría) y el filtro de rango de fechas preseleccionado (el mismo rango desde/hasta que estuviera activo en ese momento en el Dashboard). Se añaden argumentos de navegación `preselectedCategoryId` (string, nullable) y `preselectedStartDateMillis`/`preselectedEndDateMillis` (long, `-1L` = sin fecha) al destino `transactionListFragment` en el nav graph. `TransactionListFragment` aplica estos valores una sola vez al recibirlos (tras cargar categorías), sin bloquear que el usuario los cambie manualmente después. Asunción tomada al no estar especificado: el filtro de cuenta no se preselecciona (queda en "todas"), ya que el click procede del desglose global, no de una cuenta concreta.
- **2026-07-18**: **Nuevo requisito de negocio: un usuario puede pertenecer a varias familias a la vez** (hasta ahora estaba explícitamente fuera de alcance, sección 10). Se añade la Fase 7 bis a `PLAN_DESARROLLO.md`. Cambios de modelo de datos: `users/{uid}.familyId` se renombra a `activeFamilyId` (misma idea, pero ya no implica que sea la única familia); `families/{familyId}/members/{uid}` gana los campos `uid` (para poder hacer `collectionGroup("members")` filtrado por usuario) y `familyName` (desnormalizado, para pintar el selector sin N+1 lecturas). Ver el detalle completo, incluidas las asunciones tomadas al no estar especificado en el enunciado original (invitaciones a usuarios ya en otra familia, alcance de la pantalla "Mis familias", estrategia de migración del campo `uid`), en la sección 4 de este documento, "Pertenencia a varias familias (Fase 7 bis)".
- **2026-07-16**: **Decisión de negocio: todas las estadísticas "en general" de la app (saldo total, ingresos/gastos del periodo, desglose por categoría en el Dashboard y, en el futuro, toda la Fase 8 de Estadísticas avanzadas) se calculan únicamente sobre cuentas activas.** Una cuenta archivada (`active == false`) no debe aportar a ninguna métrica agregada, aunque sus movimientos históricos se conserven intactos en Firestore (no se borran, solo se excluyen de los cálculos). Esto ya se aplicaba al saldo total y al desglose por cuenta del Dashboard (Fase 7); con esta decisión se extiende explícitamente a ingresos/gastos del periodo y al desglose por categoría (bugfix del mismo día en `DashboardViewModel`, ver `PLAN_DESARROLLO.md` Fase 7) y se deja como requisito de diseño para toda la Fase 8. Única excepción prevista: si el propio usuario filtra explícitamente por una cuenta archivada (p. ej. para consultar su histórico puntual), esa vista concreta sí puede mostrar sus datos, pero nunca debe alterar los totales/agregados generales.