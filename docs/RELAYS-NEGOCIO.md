# Weft — relays: qué son, cómo se venden y cómo se gestionan

## Qué es un relay (en llano)
Un servidor que guarda un rato los mensajes cifrados hasta que el destinatario los recoge, como un buzón
sellado y anónimo. No puede leerlos, no tiene cuentas, no sabe quién habla con quién. Hay dos tipos:
`smp://` (mensajes) y `xftp://` (archivos). Personas con relays distintos hablan sin problema.

## Tres formas de tener relay
1. **Relays de Weft** (gratis, por defecto en la app — decisión pendiente de confirmar, ver fase 3).
2. **Relay propio**: el usuario alquila un servidor y instala el software libre del relay.
3. **Relay privado gestionado** (lo que vendemos): lo montamos y mantenemos nosotros para un cliente.

## Costes y precios (propuesta — confirmar antes de publicar)
Coste real (Hetzner, precios desde el 15 jun 2026, sin IVA): CX23 = 5,49 €/mes + IPv4 ≈ 6 €/mes por servidor.

| Plan | Qué incluye | Precio | Coste aprox. | Margen |
|---|---|---|---|---|
| Self-hosted | Guía; el usuario lo monta | Gratis | 0 | — |
| Personal | 1 servidor con relay SMP + XFTP, país a elegir, .onion, actualizaciones | 15 €/mes · 150 €/año | ~6 €/mes | ~9 €/mes |
| Organisation | 2 servidores en 2 países, hasta 100 personas, soporte prioritario | 49 €/mes · 490 €/año | ~12 €/mes | ~37 €/mes |

Pago: tarjeta (Stripe) o anónimo con Monero/Bitcoin. Siempre en la web, nunca en la app.

## Cómo se gestiona — por etapas

### Etapa 1 · Manual (ya posible, 0–20 clientes)
- La página `relays.html` tiene "Request a relay" → email a weftchat@proton.me.
- Cobro: **Stripe Payment Links** (enlace de pago sin servidor) o dirección Monero/BTC.
- Alta: un script en `weft-infra` (lo escribe Claude Code) que con la API de Hetzner crea el servidor,
  instala el relay con .onion y devuelve las direcciones. Tú solo ejecutas "crear relay para cliente X".
- Registro: una hoja de cálculo privada (cliente = alias, plan, país, fecha de renovación, estado).
- Baja: el mismo script borra el servidor al dejar de pagar.

### Etapa 2 · Automática (cuando haya demanda)
Hace falta un pequeño backend (la web actual es estática y no puede tenerlo):
- **Portal del cliente** en `account.weftchat.com`: sin email obligatorio; cuenta = número aleatorio
  (como Mullvad VPN). Ve su plan, fecha de renovación, direcciones del relay, renovar o cancelar.
- **Pagos automáticos**: Stripe (webhooks) + **BTCPay Server** propio para Monero/Bitcoin, sin intermediarios.
- **Aprovisionamiento automático**: al confirmarse el pago, se crea el servidor en Hetzner con el relay listo.
- **Panel de administración** (solo para ti, con 2FA): lista de clientes y planes, estado de cada relay
  (en línea / caído, espacio en disco), altas, bajas, renovaciones, ingresos del mes y alertas.
- **Monitorización**: aviso si un relay cae; actualizaciones de seguridad automáticas.
- Datos mínimos: ni nombre ni IP de clientes; solo número de cuenta, plan y pagos.

### Antes de vender (obligatorio)
- Figura legal para facturar (autónomo o empresa) e IVA de servicios digitales en la UE (régimen OSS).
- Términos del servicio de pago y política de reembolsos en la web.
- Revisar con un gestor/abogado.
