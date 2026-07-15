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

Notas:
- Usa siempre el Firebase BoM (Bill of Materials) más reciente en vez de fijar versiones sueltas de cada librería; comprueba la versión actual en la consola de Firebase / documentación oficial al escribir el `build.gradle`, no la des por supuesta de memoria.
- `minSdk` recomendado: 26 (Android 8.0). `targetSdk`: el que proponga Android Studio por defecto al crear el proyecto (la versión estable más reciente en ese momento).

## 3. Estructura de paquetes propuesta

```
com.finanzapp.app/
├── FinanzAppApplication.java          // Application class, inicializa AppContainer
├── data/
│   ├── model/                    // POJOs: User, Family, Member, Invitation, Account, Transaction, Category
│   ├── repository/               // AuthRepository, FamilyRepository, AccountRepository, TransactionRepository, CategoryRepository
│   └── firebase/                 // Constantes de rutas Firestore, mappers documento<->POJO
├── ui/
│   ├── auth/                      // LoginActivity/Fragment
│   ├── onboarding/                // WelcomeFragment, CreateFamilyFragment, InitialAccountsFragment (alta de cuentas + posición neta inicial), JoinByCodeFragment, PendingApprovalFragment, AcceptInvitationFragment
│   ├── settings/                  // SettingsFragment, ProfileFragment
│   ├── family/                    // FamilySettingsFragment, MemberListFragment, InviteMemberFragment
│   ├── accounts/                  // AccountListFragment, AddEditAccountFragment
│   ├── transactions/              // AddEditTransactionFragment, TransactionListFragment, filtros
│   ├── categories/                // ManageCategoriesFragment
│   ├── dashboard/                 // DashboardFragment (posición neta)
│   └── statistics/                // StatisticsFragment + subvistas de gráficos
├── viewmodel/                      // Un ViewModel por pantalla (o colocado junto a cada paquete de ui/, a elección del agente)
└── util/                           // Constants, CurrencyFormatter, DateUtils, Result<T>, InputValidators
```

## 4. Modelo de datos en Firestore

Todas las subcolecciones de una familia cuelgan de `families/{familyId}` para que las reglas de seguridad sean simples: "si eres miembro aprobado de esa familia, puedes leer/escribir en sus subcolecciones".

```
users/{uid}
  displayName: string
  email: string
  photoUrl: string
  familyId: string | null        // null hasta que se une a una familia
  createdAt: timestamp

families/{familyId}
  name: string
  currencyCode: string            // ISO 4217, ej. "EUR", "USD"
  inviteCode: string              // código corto único, ej. 6-8 caracteres alfanuméricos
  createdBy: uid
  createdAt: timestamp

families/{familyId}/members/{uid}
  displayName: string
  email: string
  role: "owner" | "admin" | "member"
  status: "approved"              // el doc solo se crea cuando ya está aprobado
  joinedAt: timestamp

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

**Ingreso**

| Categoría | `appliesTo` |
|---|---|
| Nómina | income |
| Otros ingresos | income |
| Ingresos extra / Freelance | income |
| Alquileres (ingreso) | income |
| Devoluciones / Reembolsos | income |

**Gasto**

| Categoría | `appliesTo` |
|---|---|
| Hipoteca | expense |
| Reformas | expense |
| Servicios | expense |
| Internet | expense |
| Seguros | expense |
| Supermercado | expense |
| Restaurantes | expense |
| Alcohol | expense |
| Transporte | expense |
| Salud | expense |
| Ropa | expense |
| Educación | expense |
| Ocio | expense |
| Viajes | expense |
| Ahorros | expense |
| Informática | expense |
| Libros | expense |
| Streaming | expense |
| Deporte | expense |
| Bebidas | expense |
| Peluquería | expense |
| Regalos | expense |
| Hogar | expense |
| Misceláneo | expense |
| Impuestos | expense |
| Comunidad | expense |
| Mascotas | expense |
| Donaciones | expense |

Ninguna categoría usa `appliesTo: "both"` en el set semilla; si el usuario necesita una categoría mixta (por ejemplo "Ajustes") puede crearla manualmente marcándola como tal.

### Aclaración sobre la aprobación de invitaciones

El enunciado pide que el creador de la familia apruebe la incorporación. Para que ambos caminos de unión tengan sentido a la vez, se ha diseñado así:
- **Invitación por correo**: la inicia el admin (ya es una aprobación implícita, porque el admin elige a quién invita). Cuando el invitado la acepta, entra directamente como `member` aprobado.
- **Unión por código**: la inicia cualquier persona que conozca el código (que puede haberse compartido fuera del control del admin), así que genera una solicitud (`code_request`) que el admin debe aprobar o rechazar explícitamente antes de que la persona pase a ser `member`.

## 5. Reglas de seguridad de Firestore (esqueleto conceptual)

Implementar (y testear con el Firebase Emulator Suite) algo equivalente a:
- Un usuario solo puede leer/escribir su propio documento en `users/{uid}`.
- Solo se puede leer/escribir en `families/{familyId}/**` si `request.auth.uid` existe como documento en `families/{familyId}/members`, o si el usuario está en proceso de crear la familia o resolver su propia invitación.
- Solo un `member` con `role == "admin"` o `role == "owner"` puede: aprobar/rechazar `code_request`, cambiar `currencyCode`, editar el `initialBalance` de una cuenta (es decir, corregir la posición neta inicial), archivar o eliminar cuentas, gestionar categorías del sistema, y cambiar el rol de otros miembros. 
- **Jerarquía de Roles**: Solo el `owner` puede cambiar el rol de un `admin`. Un `admin` solo puede cambiar el rol de un `member`. Nadie puede cambiar el rol del `owner`.
- Al crear una familia, el creador recibe el rol de `owner`.
- Al abandonar la familia, si el que sale es el `owner`, la propiedad debe traspasarse a otro miembro (priorizando `admin`).
- Nadie escribe directamente `currentBalance` de una cuenta salvo a través de la misma transacción de Firestore que crea/edita/borra el movimiento correspondiente, o que edita el `initialBalance` de la cuenta (para que nunca se descuadre).
- Una cuenta solo puede eliminarse físicamente si no tiene ningún movimiento asociado; si ya tiene movimientos, solo puede archivarse/desactivarse (`active: false`), nunca borrarse, para no perder el histórico.

Este es un punto crítico: no dejarlo para el final, implementarlo en cuanto exista el modelo de datos (Fase 2 del plan).

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
8. **Estadísticas avanzadas**: evolución temporal, comparativas por categoría/cuenta/miembro, gráficos (MPAndroidChart).

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
- Un usuario perteneciendo a más de una unidad familiar a la vez.
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
