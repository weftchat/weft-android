# Weft Chat — terminarla: fases 4.5 y 5 con Claude Code

Cómo usarlo, igual que con las fases 2–4: abre Claude Code en la carpeta `weft`, copia **un solo prompt**,
pégalo y deja que trabaje. Contesta cuando pregunte. No pases a la siguiente hasta tener el APK y la lista
de pruebas hecha.

Las pruebas de verdad necesitan un **Pixel con GrapheneOS** (paso A1 de ROADMAP.md). Lo que se pueda,
en el emulador del Mac mientras tanto.

---

## PROMPT FASE 4.5 · Borrados nuevos

```
Lee CLAUDE.md, BRIEF.md, docs/ROADMAP.md (paso A3) y docs/IDEAS-AEGIS.md. Vamos con la FASE 4.5 · Borrados nuevos.

Alcance:
1. Borrado por inactividad: si Weft no se desbloquea con el PIN real en N días (3 / 7 / 14 / 30), se borra
   el perfil real igual que el borrado de emergencia (primero claves del Keystore, luego archivos).
   Apagado por defecto. Debe funcionar aunque la app no se abra (AlarmManager / WorkManager sin Google).
2. Borrado sin red: si el móvil pasa N horas seguidas sin ninguna conexión (12 / 24 / 48 / 72), se borra.
   Apagado por defecto. Aviso claro: en un avión o zona sin cobertura también salta.
3. Borrado remoto por contacto de confianza: el dueño elige 1–3 contactos y genera un código de borrado
   para cada uno (se comparte fuera de la app, por ejemplo en persona). Si llega ese código por el chat de
   ese contacto, el móvil borra el perfil real en silencio, sin mostrar el mensaje.
   Opción "borrado de grupo": el mismo código enviado a un grupo de confianza borra a todos los que lo activaron.
   El código no se guarda en claro: solo su huella (hash) con sal.
4. Guía previa: antes de activar cualquiera de estas opciones, una pantalla explica qué borra, cuándo
   salta y que no se puede deshacer, con confirmación.
5. Todo en la pantalla Security del diseño v3, con el mismo estilo. Textos en inglés.

Reglas:
- Antes de escribir código, explícame en lenguaje llano cómo lo vas a hacer (sobre todo cómo se cuenta el
  tiempo sin que lo pueda engañar alguien cambiando la hora del móvil, y cómo funciona el código remoto)
  y espera mi OK.
- Perfil señuelo: estas opciones solo afectan al perfil real; en el señuelo aparecen apagadas.
- Commits pequeños, uno por función. Compila después de cada paso.
- Al final: APK de release firmado (v0.0.5) y lista de pruebas en español, paso a paso, en
  docs/PRUEBAS-FASE-4.5.md. Actualiza BRIEF.md con las decisiones nuevas.
```

---

## PROMPT FASE 5 · Distribución (Weft Chat terminada)

```
Lee CLAUDE.md, BRIEF.md y docs/ROADMAP.md (pasos A4, A5 y A6). Vamos con la FASE 5 · Distribución.

Primero, DECISIONES A CONFIRMAR CONMIGO antes de codificar (explícame cada una en lenguaje llano):
a) applicationId definitivo (hoy app.weft, provisional): propón opciones y riesgos de cambiarlo después.
b) Relays por defecto para el público: relays de Weft sin contraseña, separados del relay privado.
   Dime qué hay que desplegar en ~/Developer/weft-infra y cuánto cuesta.
c) Segundo servidor de relay en otro país/proveedor para el enrutado privado.

Después:
1. Versión v0.1.0: APK firmado (APK Signature v3), build reproducible y su huella SHA-256.
2. GitHub Releases en weftchat/weft-android con el APK, la huella y notas en inglés; instrucciones para
   Obtainium. ci/check-no-google.sh debe pasar.
3. Accrescent: prepara todo lo que pide (metadatos, capturas, firma) y dime qué tengo que hacer yo.
4. Web weftchat.com: botón de descarga, huella SHA-256, guía de instalación en GrapheneOS y textos
   legales actualizados. La web se sube a Porkbun Static Hosting (ver docs/PENDIENTES-WEB.md).
5. Kit del piloto de 30 personas: guía de instalación y primeros pasos en español y en inglés, en
   lenguaje muy llano, y una plantilla para que me reporten fallos por Weft.

Reglas: commits pequeños; yo no uso Terminal ni git, haz tú todo y explícame cada paso. Nada de secretos en git.
```
