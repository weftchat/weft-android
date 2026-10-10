# Weft Privacy — hoja de ruta paso a paso

Actualizada: 10 oct 2026. Une el proyecto original (BRIEF.md) con las mejoras nuevas
(docs/IDEAS-AEGIS.md y el plano del ecosistema en design/plano-ecosistema).
Regla: **no se empieza un paso hasta cerrar el anterior** con algo que funcione y una lista de pruebas.

Leyenda: ✅ hecho · 🔜 siguiente · ⏳ pendiente · 💶 cuesta dinero · 🙋 lo haces tú (compras, cuentas, decisiones)

---

## Dónde estamos
- **Weft Chat v0.0.3**: fases 0–4 hechas (identidad, chat, PIN, coacción, pánico, guardia USB, relays, Tor, grupos, llamadas).
- Solo se ha probado en el **emulador del Mac**: no hay móvil Android real.
- Relay de prueba en Hetzner (Nuremberg) con TURN para llamadas.
- Web weftchat.com publicada en Porkbun; la prueba del hosting acababa el **8 oct 2026**.
- Logos de la familia, plano del ecosistema e ideas de Aegis guardados en el proyecto.

---

## BLOQUE A · Weft Chat (proyecto original) — terminarlo y publicarlo

### Paso A1 🔜 🙋 💶 Conseguir un móvil de pruebas
- Un **Google Pixel 8 o superior** (tiene MTE, la protección de memoria que vende Aegis), nuevo o de segunda mano.
- Instalar **GrapheneOS oficial** con el instalador web (grapheneos.org/install/web). Te guío paso a paso.
- Sin esto no se pueden probar de verdad la guardia USB, las notificaciones ni las llamadas.

### Paso A2 ⏳ Pruebas reales de las fases 2, 3 y 4
- Repasar en el Pixel las listas `PRUEBAS-FASE-2/3/4.md`.
- Corregir lo que falle. Resultado: v0.0.4.

### Paso A3 ⏳ Fase 4.5 · Borrados nuevos (de las ideas de Aegis)
1. Borrado por inactividad (3/7/14/30 días sin abrir Weft; apagado por defecto).
2. Borrado sin red (X horas sin conexión: bolsa Faraday o incautación; apagado por defecto).
3. Borrado remoto por contacto de confianza (código secreto enviado por el chat; también para un grupo entero).
4. Guía previa: qué borra cada opción antes de activarla.
- Resultado: v0.0.5 + lista de pruebas.

### Paso A4 ⏳ 🙋 Decisiones antes de publicar
- Confirmar `applicationId` (hoy `app.weft`, provisional).
- Relays por defecto para el público: relays de Weft **sin contraseña**, separados del privado.
- Segundo servidor de relay en otro proveedor/país (lo necesita el enrutado privado).

### Paso A5 ⏳ Fase 5 · Distribución
- APK firmado y reproducible, con huella SHA-256.
- GitHub Releases + Obtainium; después Accrescent.
- Web con textos legales y botón de descarga.

### Paso A6 ⏳ Piloto con 30 personas
- Invitaciones, guía de instalación en lenguaje llano, canal de soporte por Weft Chat.
- Recoger fallos durante 2–4 semanas y sacar v0.1.

---

## BLOQUE B · Infraestructura común (la base de todas las apps)

### Paso B1 ⏳ 🙋 💶 Renovar lo que caduca
- Hosting de weftchat.com en Porkbun (la prueba acababa el 8 oct).

### Paso B2 ⏳ 🙋 💶 Servidor de la familia
- Un servidor en un país con leyes de privacidad fuertes (candidato: FlokiNET, Islandia), pagado en cripto por 1–2 años.
- Dimensionado para unos 50 clientes. Ahí vivirán Pass, VPN, Meet y Cloud.
- Los relays de Weft Chat pueden seguir separados (repartir el riesgo).

### Paso B3 ⏳ Sistema de acceso y pago anónimo
- Códigos de acceso (tipo Mullvad: un número, sin email).
- Pago con Monero/Bitcoin (BTCPay Server propio). Siempre en la web, nunca dentro de las apps.

---

## BLOQUE C · Weft Guard (la app nueva más necesaria)

### Paso C1 ⏳ Guard básica (app normal)
- Auditoría de permisos: qué apps usan micrófono, cámara o ubicación.
- Detección de Wi-Fi falsa (mismo nombre, otro router o cifrado distinto), secuestro de DNS y portales cautivos.
- Guía de blindaje de GrapheneOS con comprobación (2G, USB-C, PIN de coacción del sistema, reinicio automático).

### Paso C2 ⏳ Guard como "propietaria del dispositivo" (Device Owner)
- Se instala en un móvil recién reseteado; da poderes de sistema sin crear un sistema operativo propio.
- Borrado de **todo el móvil** por inactividad, sin red, a distancia o tras varios cambios seguidos de modo avión.
- Bloqueo de cámara, USB e instalación de apps; bloqueo si detecta un tirón con el móvil desbloqueado.

### Paso C3 ⏳ Guard + Chat juntos
- Los borrados de Chat y Guard se coordinan: una sola configuración, un solo contacto de confianza.

---

## BLOQUE D · Resto de la familia (servicios en el servidor de B2)

Cada app tiene dos partes: el servidor (software libre instalado en B2) y la app con la marca Weft.

| Paso | App | Base open source | Primer entregable |
|---|---|---|---|
| D1 | **Weft Pass** | Vaultwarden + apps Bitwarden | Servidor + app Android con marca Weft |
| D2 | **Weft VPN** | Amnezia VPN | Servidor AmneziaWG + app con marca Weft |
| D3 | **Weft Meet** | Jitsi Meet | Videollamadas por enlace en meet.weftchat.com |
| D4 | **Weft Cloud** | Seafile + CryptPad | Bibliotecas cifradas + documentos |
| D5 | **Weft Wallet** | Cake Wallet | Monedero sin custodia (antes: asesoría legal, MiCA) |

Reglas: ningún servicio comparte datos con otro; quitar nombres y logos originales; publicar el código (AGPL/GPL).

---

## BLOQUE E · Negocio a futuro

### Paso E1 ⏳ Weft Phone
- Pixel + GrapheneOS oficial + Weft Guard (Device Owner) + todas las apps Weft configuradas.
- Pago en cripto, envío discreto. Precio por debajo de Aegis (1.299–2.199 €).
- Antes: abogado; no usar la marca GrapheneOS.

### Paso E2 ⏳ WeftOS (solo si el negocio lo permite)
- Fork de GrapheneOS con cambios propios. Necesita un equipo de 3–5 ingenieros de sistemas
  y actualizaciones mensuales en días. Hasta entonces, E1 cubre casi todo.

---

## Orden recomendado (resumen)
A1 → A2 → A3 → B1 → A4 → A5 → A6 → C1 → C2 → B2 → B3 → D1 → D2 → C3 → D3 → D4 → D5 → E1 → (E2)
