# Weft · Fase 3 · Qué probar a mano

APK: `weft-fase3-v0.0.2.apk` (en la carpeta **WEFT** del Escritorio del Mac).
Huella SHA-256: `acad18ddeddb6d7c19de0b4e9d712af8f28bf40bb6af58bd93e3d755d8117655`

Se instala **encima** de la 0.0.1 sin perder nada (misma firma). Si alguien tiene la 0.0.1, que la
actualice: esa versión mostraba en Chats «relay r2 · via Tor · 38 ms», un dato de ejemplo del diseño
que no era verdad.

En cada prueba, lo que **debe pasar** está después de la flecha →.
Primero necesitas una identidad con tu relay puesto (pruebas 1 y 2 de la Fase 2).

---

## 1 · El estado real de tu relay

1. Abre **Chats** y mira la barra de arriba.
   → Dice `relay 159.69.21.145:5223 · direct`, con el punto verde. Ya no aparece «r2» ni «via Tor».
2. Ve a **Security** → **Relays**.
   → Tus dos relays (mensajes y archivos), cada uno con **in use** o **standby**.
3. Toca el relay de mensajes → **Test now**.
   → «Working · answered in … ms». Vuelve a Security: la fila muestra esos milisegundos.
4. En esa misma hoja, toca **Remove relay**.
   → No lo quita: dice que es tu único relay de mensajes. (Weft nunca usa servidores de otros.)

## 2 · Rotate con un solo relay

1. En **Chats**, toca **Rotate**.
   → Aviso «No other relay answers · add a second one to switch». Nada cambia.
   (El cambio real se prueba cuando exista el segundo servidor; ver «Pendiente».)

## 3 · Tor con Orbot

Necesitas **Orbot** (gratis, en F-Droid o en orbot.app).

1. Sin Orbot instalado: **Security** → **Privacy** → activa **Connect through Tor**.
   → Aviso «Install Orbot first». El interruptor se queda apagado.
2. Instala Orbot, ábrelo una vez y pulsa **Start** (conectar).
3. Vuelve a Weft → activa **Connect through Tor**.
   → «Tor on · connecting through Orbot». En Chats la barra dice `… · via Tor`.
4. Envía un mensaje a un contacto.
   → Llega igual (puede tardar unos segundos más).
5. Abre Orbot y **páralo**. Vuelve a Weft y espera medio minuto en Chats.
   → La barra se pone roja: «Tor on · open Orbot to connect». **Weft no se conecta sin Tor**:
   los mensajes esperan hasta que Orbot vuelve.
6. Arranca Orbot otra vez.
   → La barra vuelve a verde `via Tor` y lo pendiente se envía.
7. (Si prefieres) Desactiva **Connect through Tor**.
   → «Tor off · relays can see your IP address».

## 4 · Recibir mensajes con Weft bloqueada

Hacen falta **dos móviles** con Weft que sean contactos entre sí.

1. La primera vez que abras **Chats**, Android pregunta si Weft puede enviar notificaciones → **Permitir**.
   → Aparece el aviso fijo «Weft is running» (puede estar plegado abajo del todo).
2. **Security**: comprueba que **Receive messages while locked** está encendido (viene así).
3. Pon **Lock automatically** en **Immediately**. Sal de Weft (botón de inicio).
4. Desde el otro móvil, envíate un mensaje.
   → Llega el aviso **«New message»**, sin nombre ni texto.
5. Toca el aviso.
   → Weft pide el PIN. Con tu PIN, el mensaje ya está en el chat.
6. Mete un PIN **equivocado** en esa pantalla y después, sin desbloquear, pide otro mensaje desde el otro móvil.
   → El aviso llega igual: un PIN equivocado no cierra nada.
7. Ahora toca el **candado** de Chats (bloqueo manual) y envíate otro mensaje.
   → **No** llega aviso: el candado cierra todo. Al meter el PIN, el mensaje aparece.
8. Apaga **Receive messages while locked**, sal y envíate un mensaje.
   → Tampoco llega aviso hasta que entras con el PIN.
9. Déjalo como prefieras. Para un viaje de riesgo (fronteras, detenciones), mejor **apagado**.

## 5 · Enlaces sin rastreo

1. Envía a un contacto: `https://example.org/nota?id=7&utm_source=twitter&fbclid=abc`
   → En la burbuja aparece `https://example.org/nota?id=7`, subrayado.
2. Toca el enlace.
   → No se abre: sale la hoja «Open this link?» con la dirección entera y tres botones.
   **Open in Tor Browser** solo está activo si tienes Tor Browser instalado.
3. Toca **Copy link**.
   → «Link copied · cleared in 60 s».
4. **Security** → **Privacy** → apaga **Remove tracking from links** y repite el paso 1.
   → Ahora se envía tal cual. Vuelve a encenderlo.

## 6 · Cambio automático de relay (necesita el segundo servidor)

1. **Security** → **Relays**: **Switch when blocked** encendido (viene así).
2. Con dos relays de mensajes, pide a Claude que apague uno un momento.
   → En unos 2–3 minutos, aviso «Switched to relay … · chats keep working» y los mensajes siguen llegando.

---

## Pendiente (no se puede probar todavía)

- **Segundo servidor**: sin él no hay cambio real de relay (pruebas 2 y 6) y el enrutado privado
  rebota en el mismo servidor. Comprarlo en Hetzner es cosa tuya; Claude te guía.
- **Dirección .onion** en tu servidor: pendiente de tu OK para tocar el servidor.
- **Script de relays para clientes** (punto 8 de la fase): pendiente.
- **Notificaciones push (UnifiedPush)**: aplazadas; en esta versión, la conexión en segundo plano.
