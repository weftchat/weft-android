# Weft — brief para Claude Code

Este documento es la única fuente de verdad del proyecto. No inventes nada que no esté aquí; si algo no está decidido, pregunta antes de codificar.

## Qué es Weft

App de mensajería centrada en **privacidad total de metadatos**: no solo que nadie lea los mensajes, sino que nadie sepa quién habla con quién, cuándo ni desde dónde. Para periodistas, políticos y cualquiera que necesite comunicarse sin dejar rastro.

- **Piloto:** 30 personas del entorno del fundador. Después, app pública.
- **Plataforma v1:** **solo Android nativo**, Kotlin + Jetpack Compose. Objetivo principal: **GrapheneOS** (sin Google Play Services). iOS queda para más adelante.
- **Protocolo:** **SimpleX** (no Matrix, no Signal). Sin identificadores de usuario. El núcleo de SimpleX (Haskell) se compila como librería nativa y la interfaz Kotlin se construye encima, igual que la app oficial de SimpleX. Licencia AGPLv3: el código será público.
- **Nombre y marca:** Weft. Logotipo: chevron dentro de cuadrado redondeado (está en los HTML de `design/`).

## El relay ya existe

Servidor propio en Hetzner (Nuremberg), desplegado desde `~/Developer/weft-infra`. Las direcciones están en `~/Developer/weft-infra/ADDRESSES.txt` (una `smp://` para mensajes y una `xftp://` para archivos). La app debe traerlas **preconfiguradas** y permitir que el usuario añada las suyas.

## Modelo de amenaza (lo que la app debe resistir)

| Amenaza | Respuesta de Weft |
|---|---|
| Vigilancia de red (ISP, Estado) | Sin IDs de usuario; enrutado privado por 2 relays; Tor opcional |
| Servidor comprometido o requerido judicialmente | El relay solo ve colas cifradas sin dueño; no hay cuenta que entregar |
| Incautación del móvil | PIN + base de datos SQLCipher; PIN de coacción con perfil señuelo; borrado de emergencia; borrado tras 10 intentos |
| Coacción para abrir la app | Un segundo PIN abre un perfil con chats inofensivos, indistinguible del real |
| Extracción forense por cable (USB) | **Guardia USB**: al detectar conexión de datos USB, bloqueo inmediato, borrar claves de la RAM y pedir passphrase. Opción avanzada (apagada por defecto): borrado total al detectar USB. Recomendar GrapheneOS (bloqueo de puerto USB, PIN de coacción del sistema, auto-reinicio) |
| Obligado a dar el código | **Contraseña de pánico**: la app parece abrirse normal mientras borra en silencio chats, contactos y claves |
| Fuga por fotos | Metadatos EXIF/GPS eliminados en el dispositivo antes de cifrar |
| Bloqueo del servicio | Rotación automática de relay; relays del usuario |
| Malware en el dispositivo | Fuera de alcance de cualquier app; por eso GrapheneOS |

## Decisiones fijadas (no reabrir)

- Identidad = par de claves generado en el dispositivo. **Sin email, teléfono ni KYC.** Recuperación de cuenta aplazada a v2 (será frase BIP-39).
- Contactos solo por **QR o enlace de un solo uso**. Sin búsqueda de usuarios ni directorio.
- **Cero dependencias de Google.** Notificaciones por **UnifiedPush** (servidor ntfy propio, aún no desplegado); si no hay distribuidor, conexión persistente en segundo plano. CI debe fallar si aparece cualquier librería de Google/Firebase en el APK.
- Datos en reposo: **SQLCipher**, claves en Android Keystore respaldado por hardware, `allowBackup=false`.
- **PIN (confirmado por el dueño):** el PIN no se guarda en ningún sitio, ni cifrado. El PIN más una clave del Android Keystore abren la base de datos SQLCipher; un PIN incorrecto simplemente no la abre. Cada intento fallido añade una espera creciente.
- **Teclado incógnito (confirmado por el dueño):** todos los campos de texto piden al teclado que no aprenda lo que se escribe.
- **Portapapeles (confirmado por el dueño):** lo que Weft copia se marca como sensible (Android no muestra su vista previa) y se borra a los 60 s.
- **Escanear códigos QR (confirmado por el dueño):** cámara con CameraX y lectura con ZXing, todo en el teléfono.
- **Huella del dispositivo (confirmado por el dueño):** SimpleX no tiene identidad global. La "huella" de las pantallas 1 y 11 es un código de este teléfono que no identifica nada en la red; verificar a un contacto se hace con el código de seguridad de ese contacto, desde su conversación.
- **Relays en la app (confirmado por el dueño):** la app no lleva ningún relay dentro (la dirección `smp://` lleva contraseña y el APK es público). El usuario añade el suyo en el teléfono con "Add your own relay"; la app no usa los servidores preestablecidos de SimpleX.
- **FLAG_SECURE** en todas las pantallas de chat (sin capturas ni vista previa en el selector de apps).
- Permisos: cámara (QR y fotos), micrófono (llamadas), notificaciones. **Nada más.**
- Mensajes efímeros **activados por defecto** (1 h tras lectura), configurable.
- Grupos: los nativos de SimpleX, tope 50 miembros.
- Llamadas 1:1: WebRTC de SimpleX vía relay TURN propio (pendiente de desplegar).
- Llamadas de grupo: **no existen en SimpleX**. v1: malla WebRTC hasta 6 participantes, clave repartida por el chat de grupo. SFU en v2 si hace falta.
- **Enlaces sin rastreo:** antes de enviar, la app quita de los enlaces los parámetros de seguimiento (`utm_*`, `fbclid`, `gclid`, `igshid`…); activado por defecto, desactivable. Sin vista previa de enlaces por defecto (si se activa, solo a través de Tor). Tocar un enlace nunca lo abre directamente: pregunta si abrir en Tor Browser, en el navegador normal o copiar.
- **Sin consultas DNS:** los relays se contactan solo por dirección `.onion` o IP fija, nunca por nombre de dominio.
- Sin blockchain, tokens ni pagos **dentro de la app**. Nunca. (Las donaciones voluntarias van solo en la web weftchat.com, fuera de la app y sin vínculo con la identidad del usuario.)
- Distribución: **Accrescent** (principal) + releases en GitHub con APK firmado y SHA-256 para Obtainium. Firma con clave propia (APK Signature v3). Builds reproducibles como objetivo desde v1.
- Rendimiento medible: abrir un chat < 100 ms; arranque en frío < 1 s en un Pixel de hace 4 años.

## Diseño — dirección "Vault", versión 3 (estilo wallet cripto)

La referencia exacta es **`design/v3/weft-v3.html`** (prototipo navegable: ábrelo en un navegador) junto con **`design/v3/DESIGN.md`**, que traduce colores, tipografía, animaciones y comportamiento a Compose. Reprodúcelo con fidelidad; no "mejores" el diseño. Toda la app va en **inglés**. La versión anterior (7 pantallas) está archivada en `design/v2/` solo como historial.

**Pantallas v3 (11):** Create identity · PIN (elegir, repetir, entrar; PIN de coacción → perfil señuelo) · Chats · Conversation · Group chat · Group call · New group · Add contact · Security · Emergency wipe · Profile.

## Plan por fases (cada una termina en algo usable por el piloto)

| Fase | Entrega |
|---|---|
| 0 · Base | Proyecto Kotlin + Compose sobre el núcleo SimpleX; arranca en GrapheneOS; SQLCipher; firma propia; build reproducible; CI sin Google |
| 1 · Identidad y chat 1:1 | Design system v3 + pantallas Create identity, PIN, Chats, Conversation, Add contact y Profile funcionando contra el relay propio |
| 2 · Seguridad del dispositivo | PIN de coacción + señuelo, contraseña de pánico, guardia USB, wipe de emergencia, wipe tras 10 intentos, FLAG_SECURE, bloqueo automático |
| 3 · Red | Relays del usuario, enrutado privado por defecto, Tor (Arti), rotación automática, UnifiedPush |
| 4 · Grupos y llamadas | Grupos ≤ 50, llamadas 1:1 con TURN propio, llamadas de grupo en malla ≤ 6 |
| 5 · Distribución | Accrescent, GitHub releases, web con textos legales |

**Empieza por la Fase 0.** Antes de escribir código, propón la estructura del proyecto y cómo vas a integrar el núcleo de SimpleX (qué repositorio, qué versión, cómo se compila la librería nativa para arm64) y espera confirmación.

## Reglas de trabajo

- El usuario no maneja Terminal ni git: tú haces commits y explicas cada paso en lenguaje llano.
- No añadas dependencias sin justificar en una línea qué aportan.
- Cada fase se cierra con un APK instalable y una lista de qué probar a mano.
