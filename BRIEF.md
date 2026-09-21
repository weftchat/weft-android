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
| Fuga por fotos | Metadatos EXIF/GPS eliminados en el dispositivo antes de cifrar |
| Bloqueo del servicio | Rotación automática de relay; relays del usuario |
| Malware en el dispositivo | Fuera de alcance de cualquier app; por eso GrapheneOS |

## Decisiones fijadas (no reabrir)

- Identidad = par de claves generado en el dispositivo. **Sin email, teléfono ni KYC.** Recuperación de cuenta aplazada a v2 (será frase BIP-39).
- Contactos solo por **QR o enlace de un solo uso**. Sin búsqueda de usuarios ni directorio.
- **Cero dependencias de Google.** Notificaciones por **UnifiedPush** (servidor ntfy propio, aún no desplegado); si no hay distribuidor, conexión persistente en segundo plano. CI debe fallar si aparece cualquier librería de Google/Firebase en el APK.
- Datos en reposo: **SQLCipher**, claves en Android Keystore respaldado por hardware, `allowBackup=false`.
- **FLAG_SECURE** en todas las pantallas de chat (sin capturas ni vista previa en el selector de apps).
- Permisos: cámara (QR y fotos), micrófono (llamadas), notificaciones. **Nada más.**
- Mensajes efímeros **activados por defecto** (1 h tras lectura), configurable.
- Grupos: los nativos de SimpleX, tope 50 miembros.
- Llamadas 1:1: WebRTC de SimpleX vía relay TURN propio (pendiente de desplegar).
- Llamadas de grupo: **no existen en SimpleX**. v1: malla WebRTC hasta 6 participantes, clave repartida por el chat de grupo. SFU en v2 si hace falta.
- Sin blockchain, tokens ni pagos. Nunca.
- Distribución: **Accrescent** (principal) + releases en GitHub con APK firmado y SHA-256 para Obtainium. Firma con clave propia (APK Signature v3). Builds reproducibles como objetivo desde v1.
- Rendimiento medible: abrir un chat < 100 ms; arranque en frío < 1 s en un Pixel de hace 4 años.

## Diseño — dirección "Vault" (estilo wallet cripto)

Las 7 pantallas están en `design/*.dc.html` como HTML autocontenido: **son la referencia exacta** de layout, espaciado, tipografía y color. Ábrelos en un navegador para verlos. Reprodúcelos en Compose con fidelidad; no "mejores" el diseño.

**Tokens:**
- Fondo `#0A0A12` · superficie `#12121C` · tinta (texto sobre acento) `#0B0B14`
- Texto `#F4F3FF` · muted `#9A98B5` · dim `#7F7DA0`
- Acento: degradado lavanda `#A78BFA → #C4B5FD` (botones primarios, burbujas propias, anillos, toggles activos). Texto sobre él siempre en tinta, nunca blanco.
- **Menta `#5CF0B8` solo para estados seguros/verificados/cifrados.** Peligro `#FF6B8A` solo para borrar/wipe.
- Cristal: `rgba(255,255,255,0.04)` con borde `rgba(255,255,255,0.08)`, radio 20 px. Botones en píldora (999 px).
- Tipografía: **Manrope** 700-800 para UI (titulares con tracking -0.03em), **JetBrains Mono** para claves, huellas, relays, tiempos y etiquetas en mayúsculas con tracking 0.14em.
- Sin barra de estado ni teclado dibujados. Áreas táctiles ≥ 44 px. Contraste ≥ 4.5:1.

**Pantallas (orden del flujo):**
1. `Main` — Crear identidad (clave de dispositivo, apodo opcional)
2. `Unlock` — PIN de 6 dígitos
3. `Chats` — lista con selector All / Direct / Timed, estado del relay, nav inferior
4. `Conversation` — chat 1:1 con efímeros, foto con metadatos eliminados
5. `AddContact` — QR de un solo uso, escanear, copiar enlace
6. `Security` — coacción, privacidad, red y relays, wipe
7. `Profile` — avatar, apodo, huella, cuenta

## Plan por fases (cada una termina en algo usable por el piloto)

| Fase | Entrega |
|---|---|
| 0 · Base | Proyecto Kotlin + Compose sobre el núcleo SimpleX; arranca en GrapheneOS; SQLCipher; firma propia; build reproducible; CI sin Google |
| 1 · Identidad y chat 1:1 | Las 7 pantallas funcionando contra el relay propio |
| 2 · Seguridad del dispositivo | PIN de coacción + señuelo, wipe de emergencia, wipe tras 10 intentos, FLAG_SECURE, bloqueo automático |
| 3 · Red | Relays del usuario, enrutado privado por defecto, Tor (Arti), rotación automática, UnifiedPush |
| 4 · Grupos y llamadas | Grupos ≤ 50, llamadas 1:1 con TURN propio, llamadas de grupo en malla ≤ 6 |
| 5 · Distribución | Accrescent, GitHub releases, web con textos legales |

**Empieza por la Fase 0.** Antes de escribir código, propón la estructura del proyecto y cómo vas a integrar el núcleo de SimpleX (qué repositorio, qué versión, cómo se compila la librería nativa para arm64) y espera confirmación.

## Reglas de trabajo

- El usuario no maneja Terminal ni git: tú haces commits y explicas cada paso en lenguaje llano.
- No añadas dependencias sin justificar en una línea qué aportan.
- Cada fase se cierra con un APK instalable y una lista de qué probar a mano.
