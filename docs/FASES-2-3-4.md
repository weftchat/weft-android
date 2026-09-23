# Weft — fases 2, 3 y 4 con Claude Code

Cómo usarlo: abre Claude Code en la carpeta `weft`, copia **un solo prompt** (el de la fase en la que estés),
pégalo y deja que trabaje. Cuando te pregunte algo, contesta. No pases a la siguiente fase hasta que la actual
termine con un APK y la lista de pruebas hecha.

Pruebas: de momento **solo emulador** (Android Studio en el Mac). La guardia USB no se puede probar de verdad
en el emulador; queda pendiente hasta tener un Pixel.

---

## PROMPT FASE 2 · Seguridad del dispositivo

```
Lee CLAUDE.md y BRIEF.md. Vamos con la FASE 2 · Seguridad del dispositivo.

Alcance (todo está en BRIEF.md, modelo de amenaza y decisiones fijadas):
1. PIN de coacción + perfil señuelo: un segundo PIN abre un perfil con chats inofensivos, indistinguible del real.
   El señuelo debe tener su propia base SQLCipher; nada en disco debe revelar que existen dos perfiles.
2. Contraseña de pánico: un tercer código. Al introducirlo, la app borra en silencio chats, contactos y claves
   del perfil real y abre el señuelo como si nada. Sin animaciones ni esperas distintas a un desbloqueo normal.
3. Guardia USB: al detectar conexión USB de DATOS (no solo carga) con la app en segundo plano o bloqueada:
   bloqueo inmediato, borrar de la RAM la clave de la base de datos, cerrar el núcleo y pedir el PIN.
   Opción avanzada en Security, APAGADA por defecto, con aviso claro: borrado total al detectar USB de datos.
   Añade un botón solo en builds debug para simular la conexión (el emulador no puede enviar el evento real).
4. Borrado de emergencia (pantalla "Emergency wipe" del diseño v3) y borrado automático tras 10 PIN fallidos.
5. FLAG_SECURE en todas las pantallas de chat (comprueba lo que ya hay y completa).
6. Bloqueo automático: al salir de la app y tras un tiempo configurable en Security.
7. Pantalla Security del diseño v3 con todas estas opciones, y un texto que recomiende GrapheneOS
   (bloqueo de puerto USB, PIN de coacción del sistema, auto-reinicio).

Reglas:
- Antes de escribir código, explícame en lenguaje llano cómo lo vas a hacer (sobre todo cómo se guardan los
  tres códigos sin que el PIN se guarde en ningún sitio, y cómo el borrado es irreversible) y espera mi OK.
- Commits pequeños, uno por función. Compila después de cada paso.
- Pruebas en el emulador del Mac (arm64). Al final: APK de release firmado y una lista de qué probar a mano,
  en español y paso a paso, porque no sé usar Terminal.
- Actualiza BRIEF.md si tomamos alguna decisión nueva.
```

---

## PROMPT FASE 3 · Red

```
Lee CLAUDE.md y BRIEF.md. Vamos con la FASE 3 · Red.

Alcance:
1. Relays del usuario: revisar y terminar "Add your own relay" (SMP y XFTP), con varios relays y prueba de conexión.
2. Enrutado privado por defecto (private message routing de SimpleX, siempre activado).
3. Tor: primero dime las opciones (Arti embebido, Orbot, proxy SOCKS del núcleo de SimpleX) con pros y contras
   en GrapheneOS, y espera que yo elija. Relays solo por .onion o IP fija, nunca por nombre de dominio.
4. Rotación automática de relay cuando uno falla o está bloqueado.
5. Notificaciones con UnifiedPush (sin Google). El servidor ntfy propio aún no existe: dime qué hay que desplegar
   en ~/Developer/weft-infra y hazlo paso a paso conmigo. Si no hay distribuidor, conexión persistente en segundo plano.
6. Enlaces sin rastreo y el aviso al tocar un enlace (Tor Browser / navegador normal / copiar), si aún no están.

Reglas: propón el plan antes de codificar y espera mi OK; commits pequeños; ninguna dependencia de Google
(ci/check-no-google.sh debe pasar); al final APK + lista de pruebas en español para el emulador.
```

---

## PROMPT FASE 4 · Grupos y llamadas

```
Lee CLAUDE.md y BRIEF.md. Vamos con la FASE 4 · Grupos y llamadas.

Alcance:
1. Grupos nativos de SimpleX, máximo 50 miembros: pantallas New group y Group chat del diseño v3
   (crear, invitar por QR o enlace, salir, expulsar, mensajes efímeros por defecto).
2. Llamadas 1:1 con el WebRTC de SimpleX a través de un TURN propio. El TURN aún no existe: dime qué desplegar
   en ~/Developer/weft-infra (coturn en Hetzner) y hazlo conmigo paso a paso. Nada de servidores STUN/TURN públicos.
3. Llamadas de grupo en malla WebRTC hasta 6 personas, con la clave repartida por el chat de grupo
   (pantalla Group call del diseño v3). Explícame antes el diseño y sus límites.
4. Permiso de micrófono solo al iniciar la primera llamada.

Reglas: plan antes de codificar y espera mi OK; commits pequeños; sin Google; al final APK + lista de pruebas
en español (llamadas entre dos emuladores si es posible).
```
