# Ideas sacadas de Aegis Mobile para Weft Privacy

Analizado: 10 oct 2026, desde security-aegis.com/es.

## Qué es Aegis
Venden móviles Pixel con su propio sistema, Aegis OS, basado en las protecciones de GrapheneOS, por 1.299–2.199 €.
El pago es único, con cripto (BTC, ETH, SOL, LTC, USDT y XMR bajo petición) y sin datos personales.
Incluyen Threema, Mullvad VPN y una eSIM global durante 6 meses, y Aegis Chat de por vida.
Su web no publica el código ni auditorías. Weft sí puede hacerlo (AGPL), y eso es una ventaja frente a ellos.

## Lo que Weft YA tiene (igual que Aegis)
- Perfil señuelo (PIN de coacción)
- PIN/código de pánico con borrado silencioso
- Borrado tras 10 intentos y borrado de emergencia
- Contactos por QR o enlace de un solo uso, sin número de teléfono
- Guardia USB y recomendación de GrapheneOS

## Lo que se puede añadir (posible como app, sin ser un sistema operativo)

### A Weft Chat (prioridad alta, poco trabajo)
1. **Borrado por inactividad:** si no se abre Weft en X días (por defecto apagado; 3/7/14/30), se borran las claves y los datos.
2. **Borrado sin red:** si el móvil pasa X horas sin conexión (bolsa Faraday, incautación), se borra. Apagado por defecto.
3. **Borrado remoto por contacto de confianza:** el dueño elige 1–3 contactos y les da un "código de borrado". Si un contacto lo envía por el chat, el móvil borra Weft. También sirve para borrar un grupo de confianza a la vez.
4. **Modificador de voz en llamadas:** se distorsiona la voz dentro de las llamadas de Weft (1:1 y grupo), procesándola en el propio móvil.
5. **Guía previa de borrado:** una pantalla que explica qué borra cada opción antes de activarla.

### Nueva app: Weft Guard (antes "Weft Scan") — la más necesaria
Un guardián del móvil que reúne lo mejor de Aegis que una app sí puede hacer:
- **Detección de ataques de red:** Wi-Fi gemelo malicioso (mismo nombre con otro BSSID o cifrado distinto), secuestro de DNS (comprueba respuestas contra DNS cifrado), portales cautivos falsos, redes abiertas o WEP, cambios del router (MAC de la puerta de enlace).
- **Vigilancia continua de Wi-Fi** con avisos.
- **Auditoría de permisos:** qué apps tienen micrófono, cámara, ubicación o accesibilidad, y cuáles los usaron hace poco.
- **Lista de refuerzo GrapheneOS:** guía paso a paso para activar el bloqueo de 2G, el puerto USB-C, el PIN de coacción del sistema, el reinicio automático y los interruptores de sensores (Weft no puede activarlos, pero sí enseñar a hacerlo y comprobarlo).
- **Disparadores de borrado para todo Weft:** inactividad, sin red y remoto, compartidos con Chat.

Límites: sin root no se puede detectar ARP spoofing de forma fiable, bloquear la cámara o el micrófono del sistema, ni cambiar la voz fuera de las llamadas de Weft.

### Negocio: "Weft Phone" (más adelante)
Un Pixel con GrapheneOS preinstalado y las apps Weft configuradas, con relay privado, VPN y Pass incluidos 6–12 meses, pagado con Monero/BTC y enviado en embalaje neutro.
Precio objetivo por debajo de Aegis.
Antes: revisar con un abogado, y no usar el nombre ni el logo de GrapheneOS en la marca. Es un proyecto aparte y no oficial.

### Soporte
Solo por canales cifrados (Weft Chat, SimpleX, Signal), sin formularios ni email. Así lo hace Aegis y encaja con el público de Weft.

## Lo que NO se puede copiar (es del sistema operativo)
Modificador de voz para todo el sistema, etiquetado de memoria MTE, bloqueo de 2G, interruptores de cámara y micrófono, protección contra arrebatos con el móvil bloqueado y borrado con el modo avión. Todo esto lo da GrapheneOS o un sistema propio, no una app.

## Orden recomendado
1. Weft Chat: borrado por inactividad, sin red y remoto, más la guía de borrado (antes de publicar la Fase 5).
2. Weft Guard: detección de ataques Wi-Fi/DNS, permisos y lista GrapheneOS.
3. Modificador de voz en llamadas de Weft.
4. Resto de la familia (Pass, VPN, Meet, Cloud, Wallet) según OPEN-SOURCE.md.
5. Weft Phone, cuando haya clientes y asesoría legal.
