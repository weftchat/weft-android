# Weft · Fase 4 · Qué probar a mano

APK: `weft-fase4-v0.0.3.apk` (en la carpeta **WEFT** del Escritorio del Mac).
Huella SHA-256: `4e1c67133efca2345bec8033bcc7808af62eb8972da419996293abbb7fb89099`

Se instala **encima** de la 0.0.1 o la 0.0.2 sin perder nada (misma firma).

**Casi todo aquí necesita dos móviles** con Weft, cada uno con tu relay puesto (Fase 2, prueba 2) y
que sean contactos entre sí. Para las llamadas de grupo de 3 o más personas, un tercer móvil.
Las pruebas de la Fase 3 que quedaron pendientes (avisos «New message», enlaces) se pueden hacer a la vez.

En cada prueba, lo que **debe pasar** está después de la flecha →.

---

## 0 · Poner tu servidor de llamadas (en cada móvil)

1. En el Mac abre `Developer/weft-infra/ICE-SERVERS.txt` y copia **las tres líneas** que empiezan por
   `stun:` y `turn:` (van por IP, sin nombres).
2. En Weft: **Security** → **Calls** → **Add call server** → pega → **Add**.
   → «Call server added» y aparecen tres filas `159.69.21.145:3478 · …`, sin contraseña a la vista.
3. Prueba a pegar una línea con un nombre en vez de IP (por ejemplo cambia la IP por `turn.weftchat.com`).
   → «Use the server’s IP address, not a name».

## 1 · Crear un grupo

1. **Chats** → **+** → **New group**.
2. Escribe un nombre (por ejemplo `prueba`) y marca a tu contacto.
   → El botón dice «Create group · 2 people». Sin nombre o sin nadie marcado, no se puede pulsar.
3. **Create group**.
   → Entras al chat del grupo: «2 members · E2E» arriba y la nota «group keys rotate when someone leaves».
   Aviso «Group created · disappearing messages on (1 h)».
4. En el otro móvil, en **Chats** aparece el grupo. Ábrelo.
   → En lugar de la barra de escribir sale «You were invited to this group» y **Join group**. Púlsalo.
5. Escribid los dos en el grupo.
   → Cada mensaje del otro lleva su nombre, en su color, en el primero de cada tanda.

## 2 · Miembros, invitar, expulsar, salir

1. Toca el **nombre del grupo** arriba.
   → Hoja «Group members»: tú primero, el otro; «Remove» junto al otro (eres el dueño).
2. **Invite with a link or QR**.
   → Un QR y «Copy invitation link». (Con un tercer móvil: escanéalo desde **Add** → **Scan their code**.)
3. **Remove** al otro → luego, desde su móvil, mira el grupo.
   → Ya no puede escribir en él.
4. Crea otro grupo y desde el otro móvil pulsa **Leave group**.
   → Sale del grupo y vuelve a Chats.

## 3 · Llamada 1:1

1. Abre la conversación con tu contacto → botón del **teléfono** (junto al temporizador).
   → La primera vez, Android pide el **micrófono** → Permitir. Pantalla de llamada: «calling…».
2. En el otro móvil (con Weft abierta y desbloqueada):
   → Pantalla «Incoming call · E2E» con **Answer** / **Decline**. Pulsa **Answer**.
3. Hablad.
   → Os oís. Arriba: «E2E encrypted · 00:12» contando. Dos recuadros: «You» y el otro.
4. **Mute** → el botón se pone claro y tu recuadro muestra el micro tachado; el otro deja de oírte.
5. **Speaker** alterna entre altavoz y auricular.
6. **Camera** → la primera vez pide la cámara. El otro te ve.
7. Flecha **abajo** (minimizar).
   → Vuelves a la app y arriba queda «Return to call · …». Tócalo para volver.
8. **Leave**.
   → En los dos: «Call ended · mm:ss».

## 4 · Llamada entrante con Weft cerrada o bloqueada

1. En el móvil B, sal de Weft (botón de inicio).
2. Desde A, llama a B.
   → En B suena y aparece un aviso que dice solo **«Incoming call»**, sin nombre.
3. Toca el aviso.
   → Se abre Weft; si estaba bloqueada, pide el PIN. Tras el PIN, pantalla **Answer** / **Decline**.
4. Con **Decline**, en A la llamada termina.

## 5 · La llamada nunca revela tu IP

1. Durante una llamada, no hay nada que mirar en pantalla: Weft solo permite conectar **a través de tu
   servidor de llamadas**. Si quitas tus servidores (Security → Calls → Remove en todas) y llamas:
   → «Add a call server in Security first · calls only use your own». No hay llamada.
2. Con **Connect through Tor** activado, al empezar una llamada:
   → Aviso «Calls don’t use Tor · only your call server sees your IP».

## 6 · Llamada de grupo (voz, hasta 6)

Mejor con 3 móviles.

1. En el grupo, botón del **teléfono** → **Voice call · up to 6 people**.
   → Pantalla de llamada de grupo con tu recuadro. Arriba «connecting…».
2. En los otros móviles, abrid el grupo.
   → Franja verde **«Call in progress · 1 in it · Join»**. Pulsad **Join**.
3. Hablad.
   → Todos os oís. Cada uno aparece en su recuadro; quien aún se está conectando sale más apagado.
   Arriba «E2E encrypted · mm:ss».
4. Uno pulsa **Leave**.
   → Desaparece su recuadro en los demás; la llamada sigue entre los que quedan.
5. En la app oficial de SimpleX (si alguien del grupo la usa) solo se ve «Group call · Weft».

## 7 · Llamada de grupo con vídeo (hasta 4)

1. **Video call · up to 4 people** → pide la cámara la primera vez.
   → Los vídeos de los demás aparecen en cuadrícula.
2. Con 4 personas ya dentro, una quinta intenta **Join**.
   → «This call is full».

---

## Lo que se sabe de antemano

- **Emulador del Mac**: el micrófono del emulador falla (macOS no le deja usar el del Mac) y se puede
  quedar colgado unos segundos. Para llamadas, usa móviles reales.
- Las llamadas de grupo solo funcionan **entre usuarios de Weft** (SimpleX no las tiene).
- Si algo no va, dile a Claude qué prueba, qué paso y qué salió en pantalla.
