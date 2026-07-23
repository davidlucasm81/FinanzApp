# Política de Privacidad de FinanzApp

**Última actualización:** 23 de julio de 2024

Esta Política de Privacidad describe cómo FinanzApp ("la app", "nosotros") recoge,
usa y protege los datos personales de sus usuarios ("tú", "el usuario").

> ⚠️ Este texto es un borrador técnico basado en el modelo de datos real de la
> app. Antes de publicarlo debe revisarlo un profesional legal (ver tarea
> pendiente "Acción manual del humano/legal" en `PLAN_DESARROLLO.md`, Fase 9
> bis), especialmente la base legal de cada tratamiento.
>
> **Sincronización con la app (Fase 10):** la pantalla de consentimiento
> dentro de la app (`strings.xml` → `privacy_policy_text`) muestra un
> resumen de este mismo documento (mismos datos recogidos, mismo tratamiento,
> mismos derechos) y enlaza aquí para la versión completa con la identidad
> del responsable. Cualquier cambio de fondo en uno de los dos textos debe
> reflejarse en el otro para que no queden desincronizados.

## 1. Responsable del tratamiento

David (Desarrollador individual).
Email de contacto: davidlucasmora81@gmail.com

## 2. Qué datos recogemos

| Dato | Origen | Finalidad |
|---|---|---|
| Nombre, email, foto de perfil | Proveedor de identidad Google, al iniciar sesión | Identificarte dentro de tu unidad familiar |
| Fecha de aceptación de la política de privacidad | Generado por la app en el primer inicio de sesión | Acreditar tu consentimiento (Art. 7 RGPD) |
| Nombre de la unidad familiar, moneda | Introducido por ti o por quien te invita | Agrupar tus finanzas con tu familia |
| Cuentas bancarias (solo nombre y saldo, sin datos de la entidad bancaria real ni credenciales) | Introducido por ti | Calcular tu posición neta |
| Movimientos (importe, fecha, categoría, método de pago, descripción libre opcional) | Introducido por ti, o importado desde un fichero CSV que tú subes | Llevar el registro de ingresos y gastos de tu familia |
| Rol dentro de la familia (propietario/administrador/miembro) | Generado por la app | Gestionar permisos |

No recogemos datos de categoría especial (Art. 9 RGPD: salud, ideología,
orientación sexual, etc.). El campo de descripción libre de un movimiento es
de texto abierto: te recomendamos no incluir en él datos sensibles.

## 3. Con quién compartimos tus datos

- **Otros miembros de tu(s) unidad(es) familiar(es)**: ven las cuentas,
  movimientos y estadísticas de la familia a la que perteneces, nunca de
  otras familias.
- **Google Firebase** (Google Ireland Ltd.), como encargado del tratamiento:
  aloja la base de datos (Cloud Firestore) y gestiona el inicio de sesión
  (Firebase Authentication). Los datos se cifran en tránsito (TLS) y en
  reposo (AES-256).
- No cedemos tus datos a terceros con fines publicitarios ni los vendemos.

## 4. Dónde se almacenan tus datos

Los datos se almacenan en servidores de Google Cloud / Firebase en la región de Europa (España).
Firestore ofrece además persistencia offline en tu propio dispositivo para
que la app funcione sin conexión.

## 5. Tus derechos

Puedes ejercer en cualquier momento tus derechos de acceso, rectificación,
supresión, portabilidad, limitación y oposición:

- **Descargar tus datos**: desde Ajustes → "Descargar mis datos", genera un
  fichero JSON con tu perfil, tus membresías familiares y los movimientos
  que tú mismo has creado.
- **Eliminar tu cuenta**: desde Ajustes, mediante confirmación explícita
  escribiendo "BORRAR CUENTA".
- **Contacto**: para cualquier otra solicitud, escribe a davidlucasmora81@gmail.com.

## 6. Conservación de los datos

Conservamos tus datos mientras mantengas una cuenta activa. Al eliminar tu
cuenta, se elimina tu perfil y, si eras el único miembro de una familia,
también los datos de esa familia.

## 7. Cambios en esta política

Si cambiamos esta política de forma sustancial, te lo notificaremos dentro
de la app y, si es necesario, te pediremos que aceptes de nuevo el
consentimiento.
