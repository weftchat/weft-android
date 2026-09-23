# Weft · Fase 2 · Qué probar a mano

APK: `weft-fase2-v0.0.1.apk` (en la carpeta **Weft** del Escritorio del Mac).
Huella SHA-256: `561a695a35698b8a9ba4b66a81618c38feb51d965fdc4b53eaab263f915214f5`

> **Aviso importante.** Varias de estas pruebas **borran todo sin posibilidad de recuperarlo**,
> que es justo lo que tienen que hacer. Hazlas con una identidad **de prueba**, nunca con chats que
> quieras conservar. Al final de la lista está el orden recomendado para no perder nada útil.

## Antes de empezar

1. **Instalar el APK en tu Android.** Pídeme «pásame el APK al móvil»: te doy una dirección para
   abrirla en el navegador del teléfono, conectado a la misma Wi‑Fi que el Mac. El móvil te pedirá
   permiso para instalar apps de «fuentes desconocidas»; es normal, porque Weft no viene de la Play Store.
2. Si ya tenías una versión anterior de Weft, **desinstálala primero**: esta fase cambia cómo se
   guardan los datos y no puede abrir las identidades antiguas.
3. Para el PIN de coacción hace falta tu relay. Cuando llegues a ese paso, dímelo y te paso sus
   direcciones de forma segura; llevan contraseña y no deben ir por WhatsApp ni por email.

En cada prueba, lo que **debe pasar** está después de la flecha →.

---

## 1 · Crear identidad y PIN

1. Abre Weft → **Create identity** → escribe un apodo (por ejemplo `prueba`) → **Choose a PIN**.
2. Elige un PIN de 6 cifras y repítelo.
   → Aparece **Chats** vacío y el aviso «PIN set · Weft is ready».
3. Toca el **candado** (arriba a la derecha en Chats).
   → Pide «Enter your PIN».
4. Escribe un PIN **equivocado**.
   → Los puntos se ponen rojos y tiemblan. No entra.
5. Escribe tu PIN correcto.
   → Vuelve a Chats. (Si fallaste antes, tardará unos segundos: es la espera que crece con cada fallo.)

## 2 · Añadir tu relay

1. Pestaña **Security** → baja hasta **Relays** → **Add your own relay**.
2. Pega la dirección `smp://…` → **Add relay**.
   → Mensaje verde pidiendo la dirección `xftp://`.
3. Pega la dirección `xftp://…` → **Add relay**.
   → Aparecen dos relays con la etiqueta **in use**.

## 3 · PIN de coacción (perfil señuelo)

1. **Security** → activa el interruptor de **Duress PIN**.
2. Elige un PIN **distinto** del tuyo (por ejemplo `000000`) y repítelo.
   → Tarda unos segundos (está creando el señuelo) y aparece «Duress PIN set» y la fila «PIN •••••• Change».
3. Candado → escribe el **PIN de coacción**.
   → Se abre Weft **igual que siempre**: mismo tiempo, misma animación.
4. Ve a **Profile**.
   → El apodo **no** es el tuyo: es el perfil señuelo, vacío.
5. Ve a **Security**.
   → Ahí el PIN de coacción aparece **apagado**, como en un perfil normal. Nada delata que existe otro.
6. (Opcional) Añade en el señuelo un contacto de confianza que sepa que es el perfil de engaño,
   para que no esté vacío.
7. Candado → tu **PIN real**.
   → Vuelve tu perfil, con tu apodo, y Security muestra el PIN de coacción encendido.

## 4 · Bloqueo automático

1. **Security** → **Lock automatically** → **Immediately**.
2. Sal de Weft (botón de inicio) y vuelve a abrirla.
   → Pide el PIN. El texto dice «Weft locks itself as soon as you leave».
3. Pon **After 30 seconds**. Sal, espera **10 segundos** y vuelve.
   → **No** pide PIN.
4. Sal, espera **más de 30 segundos** y vuelve.
   → Pide PIN.
5. Déjalo en **After 1 minute** (o lo que prefieras).

## 5 · Capturas de pantalla bloqueadas

1. Dentro de Weft, intenta hacer una captura (botones de encendido + bajar volumen).
   → El móvil dice que la app no lo permite, o la captura sale negra.
2. Abre el selector de apps recientes (el cuadrado, o deslizar desde abajo y mantener).
   → La tarjeta de Weft sale en blanco o negro, sin ver los chats.

## 6 · Guardia USB (solo con el móvil real)

Necesitas un cable USB y un ordenador (el Mac sirve).

1. Desbloquea Weft y **sal a la pantalla de inicio** (Weft queda en segundo plano).
2. Conecta el móvil al ordenador y, en el aviso de USB del móvil, elige **Transferencia de archivos**.
3. Abre Weft.
   → Pide el PIN (se bloqueó sola al detectar el cable de datos).
4. Desconecta. Desbloquea Weft, sal a inicio y conecta ahora con el modo **Solo cargar** / **Sin
   transferencia de datos**.
5. Abre Weft.
   → Si el bloqueo automático aún no ha saltado, **no** pide PIN: un cable solo de carga no cuenta.

> Cada móvil informa del cable a su manera. Si en el paso 3 no pidió el PIN, dímelo con el modelo
> de tu móvil.

## 7 · Borrar tras 10 PIN fallidos

1. **Security** → activa **Wipe after 10 wrong PINs**.
2. Candado.
   → El texto bajo «Enter your PIN» dice «wrong PIN 10× wipes this phone».
3. Mete un PIN equivocado.
   → Dice «… · 1/10».
4. Mete tu PIN correcto.
   → Entra y el contador vuelve a cero.
5. *(Opcional, borra todo)* Diez fallos seguidos borran la identidad. Las esperas entre intentos
   crecen, así que tarda unos 9 minutos.

---

## Pruebas que BORRAN (hazlas al final, en este orden)

## 8 · Código de pánico

1. **Security** → activa **Panic code** → elige un código distinto de los otros dos (por ejemplo `111111`).
   → «Panic code set» y la fila «Code •••••• Change».
2. Candado → escribe el **código de pánico**.
   → Se abre Weft **exactamente igual** que con un PIN normal, pero es el **perfil señuelo**
   (compruébalo en Profile).
3. Candado → escribe tu **PIN real**.
   → **No entra**: se comporta como un PIN equivocado. Tu perfil real ya no existe.
4. Candado → PIN de coacción.
   → Sigue abriendo el señuelo.

## 9 · Guardia USB con borrado (opcional)

1. **Security** → **Wipe on USB data** → lee el aviso → **Turn on**.
2. Desbloquea, sal a inicio y conecta el cable en modo **Transferencia de archivos**.
3. Abre Weft.
   → Aparece la pantalla de bienvenida «Your keys. Your words.»: todo borrado.

## 10 · Borrado de emergencia

1. Crea una identidad de prueba nueva.
2. **Security** → **Wipe everything now** (botón rojo al final).
3. Mantén pulsado **Hold to wipe** 1 segundo y **suelta**.
   → La barra roja retrocede. No pasa nada.
4. Mantén pulsado **2 segundos** sin soltar.
   → El contenido se desvanece y aparece «This phone is clean».
5. **Start over**.
   → Pantalla de bienvenida, como si Weft no se hubiera configurado nunca.

---

## Qué NO está en esta fase

- Temporizador de mensajes efímeros por defecto (la fila «Default disappearing timer» del diseño).
- Relays con estado real, rotación y Tor: Fase 3.
- Fotos, grupos y llamadas: fases siguientes.
