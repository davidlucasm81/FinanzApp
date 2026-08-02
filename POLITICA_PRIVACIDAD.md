# Política de Privacidad de FinanzApp

**Última actualización:** 2 de agosto de 2026

Esta Política de Privacidad describe cómo FinanzApp ("la app", "nosotros") recoge,
usa y protege los datos personales de sus usuarios ("tú", "el usuario").

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
| Identificador de publicidad del dispositivo e interacción con los anuncios (impresiones, clics) | Generado por el SDK de Google AdMob | Mostrarte anuncios banner no intrusivos que financian el desarrollo de la app (solo si no eres usuario Premium ni estás en la Whitelist) |
| Estado de la compra Premium (identificador de producto, identificador de transacción/recibo) | Generado por Google Play al completar la compra y gestionado por RevenueCat | Activar y verificar tu acceso Premium (sin anuncios) y permitir restaurarlo en otro dispositivo |

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
- **Google AdMob** (Google Ireland Ltd.): si **no** eres usuario Premium ni
  estás en la Whitelist, la app muestra anuncios en formato banner (nunca
  intersticiales ni formatos que bloqueen el uso) a través de este servicio.
  AdMob puede recibir el identificador de publicidad de tu dispositivo y
  datos técnicos de la interacción con el anuncio para poder mostrarlo y
  medir su rendimiento. Antes de mostrarte anuncios personalizados, si tu
  ubicación lo requiere (Espacio Económico Europeo, Reino Unido o Suiza),
  te pedimos tu consentimiento mediante un formulario estándar (User
  Messaging Platform de Google), que puedes revisar en cualquier momento
  desde Ajustes → "Gestionar consentimiento de anuncios". Si compras
  Premium o formas parte de la Whitelist, dejamos de solicitar anuncios a
  AdMob por completo.
- **RevenueCat**: gestiona y verifica tu compra Premium de por vida y
  permite restaurarla si cambias de dispositivo. Recibe el identificador
  de la compra/recibo generado por Google Play, no tus datos de pago
  (tu tarjeta la gestiona directamente Google Play, nunca nosotros ni
  RevenueCat).
- No vendemos tus datos a terceros ni se los cedemos con fines
  publicitarios propios; el único tratamiento publicitario es el descrito
  arriba, necesario para mostrarte los anuncios banner que financian el
  desarrollo de la app cuando no eres usuario Premium.

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
