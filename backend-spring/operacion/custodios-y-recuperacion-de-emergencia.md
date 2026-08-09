# Custodios y recuperación de emergencia del gobierno

Procedimiento operativo del **nivel 3** de la recuperación de acceso (Bloque 6,
[diseño §9](../../docs/ai/plan-s0-6-mfa-y-break-glass.md)). Cubre las dos cosas que el diseño deja
explícitamente al procedimiento y no al código: **quiénes son los custodios** y **qué se hace, paso
a paso, el día que haga falta**.

> **Dos puertas, y conviene no confundirlas** (D-S0-53):
>
> | Puerta | Qué exige |
> |---|---|
> | **Implementación y pruebas de V38** | Nada de esto. Se verifica con secretos **generados durante la corrida E2E** y destruidos con su base efímera |
> | **Activación operativa o producción** | Todo esto: dos custodios reales designados, secretos entregados por separado, operador definido y prueba de custodia satisfactoria |
>
> Es decir: **la designación no bloquea el código, bloquea el encendido.** Este documento gobierna
> la segunda puerta.
>
> Mientras tanto rige la sección 2: **el objetivo es no necesitar esto nunca.**

---

## 1. La regla que ordena todo lo demás

**La recuperación de emergencia repone gobierno; no gobierna.** No es una cuenta, no es un rol y no
produce sesión: es un permiso temporal, de un solo uso y de alcance fijo, para devolverle el mando a
**una** persona de **un** tenant.

De ahí salen las tres reglas que este procedimiento existe para sostener:

1. **Nadie fija la contraseña ni el segundo factor de otro.** La concesión reactiva la cuenta,
   revoca el factor y repone la membresía. El titular vuelve a enrolar **él mismo**. No hay
   excepción, tampoco en una emergencia.
2. **Ningún custodio conoce las dos partes.** Lo dice el diseño y no lo puede comprobar el software:
   dos filas distintas prueban que se presentaron dos secretos, no que había dos personas. Lo único
   que hace que la separación sea real es este procedimiento.
3. **Quien ejecuta no custodia.** El operador que corre la herramienta es una **tercera** identidad.
   Si el operador fuera además custodio, tendría una de las dos llaves y el teclado — y la doble
   aprobación se quedaría en una sola persona con dos contraseñas.

---

## 2. Hoy, sin V38: cómo se evita necesitar esto

El invariante de **administrador operativo** (D-S0-37) bloquea, a propósito, revocarle el factor al
último `TENANT_ADMIN` capaz de gobernar. Es decir: **un tenant con un solo administrador operativo
no se puede recuperar por la vía ordinaria.** Eso no es un defecto, es lo que obliga a que la
emergencia exista y sea excepcional — pero mientras la emergencia no esté construida, **es un
callejón sin salida**.

La mitigación es aburrida y funciona:

| Medida | Cadencia | Cómo se comprueba |
|---|---|---|
| **Mantener ≥ 2 administradores operativos** por organización | Permanente | `GET /accesos` (pantalla *Seguridad y accesos*): al menos dos filas con banda `TENANT_ADMIN`, cuenta activa, MFA activo y sin cambios pendientes |
| Que el segundo administrador **entre de verdad** cada cierto tiempo | Mensual | Un `LOGIN_OK` suyo en el aviso de gobierno. Una cuenta de reserva que nadie usa se descubre caducada el día que hace falta |
| Que cada administrador **guarde sus códigos de respaldo** | Al enrolar y al regenerar | Se le exige confirmarlo en pantalla; el recuento vive en `GET /accesos` |
| Vigilar el recuento de códigos | Mensual | La columna «Segundo factor» avisa cuando quedan pocos |

**Si aun así el tenant se queda sin administrador operativo antes de que exista V38**, no hay camino
soportado dentro del producto. Lo que hay es una intervención en base de datos por parte de quien
administre la instalación, y este documento **no la describe** a propósito: escribirla la
convertiría en un procedimiento normal, que es exactamente lo que V38 viene a impedir. Se registra
como incidente y se prioriza V38.

---

## 3. Los dos custodios

### 3.1 Qué es un custodio, y qué no

| Es | No es |
|---|---|
| Alguien que **guarda una de las dos llaves** de la recuperación de emergencia | Un administrador del sistema |
| Alguien que **aprueba** una recuperación concreta, para una persona concreta | Alguien con acceso a datos del negocio |
| Una identidad registrada, con su secreto propio y su historial de rotaciones | Una cuenta con la que se pueda iniciar sesión |

Un custodio **no necesita ser usuario de ControlLocal**, y conviene que no lo sea: su función es
autorizar, no operar.

### 3.2 Requisitos de designación

1. **Exactamente dos activos como mínimo.** Nunca menos. Un tercero de reserva es recomendable, pero
   sigue habiendo **dos aprobaciones**, no tres.
2. **Personas distintas, con responsabilidades distintas.** Dos custodios que reportan a la misma
   persona y comparten despacho no son dos manos separadas.
3. **Ninguno puede ser el operador** que ejecuta la herramienta (§1, regla 3).
4. **Ninguno puede conocer el secreto del otro.** Ni siquiera «por si acaso», ni en un sobre común.
5. **Localizables.** Un custodio que no se puede contactar en una hora no sirve para una emergencia;
   por eso hay reserva y por eso se rota.

### 3.3 Acta de designación

> **SIN RELLENAR.** La designación es una decisión de la organización, no de este documento. Se
> completa a mano, se firma y se conserva fuera del repositorio. **Aquí no se escriben secretos, ni
> siquiera parcialmente.**

**El acta ES el registro de vigencia.** No hay tabla de custodios en la base (D-S0-51): el sistema
solo verifica dos hashes de configuración y guarda, en cada concesión, qué identificadores
participaron. Quién es custodio hoy lo dice este papel firmado, no una consulta.

| Puesto | Nombre y cargo | Identificador (`RECUPERACION_CUSTODIO_*_ID`) | Alta | Última rotación | Vigente |
|---|---|---|---|---|---|
| Custodio A | *(pendiente)* | *(pendiente)* | — | — | — |
| Custodio B | *(pendiente)* | *(pendiente)* | — | — | — |
| Custodio de reserva | *(opcional)* | | — | — | — |
| Operador autorizado | *(pendiente)* | *(pendiente)* | — | — | — |

El identificador del operador también se anota: la concesión guarda las **tres** identidades y la
base rechaza que coincidan (D-S0-52). Un operador que además figure como custodio no podrá emitir
nada, y eso es intencionado.

### 3.4 El secreto de cada custodio

| Propiedad | Regla |
|---|---|
| Origen | **Lo genera el sistema.** El custodio no lo elige: una frase elegida por una persona no tiene 128 bits |
| Entropía | **≥ 128 bits** aleatorios |
| Dónde vive su hash | **En configuración** (`RECUPERACION_CUSTODIO_*_HASH`), **fuera de PostgreSQL**. Un hash de custodio dentro de la base que ese mismo mecanismo viene a rescatar es una llave guardada dentro de la casa |
| Formato del hash | **PBKDF2 con sal** (hash lento), como una contraseña. Nunca hash simple |
| Entrega | **Una sola vez**, en el acto de alta, directamente a su titular |
| Custodia | Gestor de contraseñas de la organización **o** sobre sellado en caja fuerte. **Una de las dos, escrita en el acta** |
| Prohibido | Correo, chat, hoja de cálculo compartida, captura de pantalla, y **el gestor de contraseñas del otro custodio** |
| Rotación | **Anual y después de cada uso**, sin excepción |
| Intentos fallidos | Bloqueo progresivo, y **un evento de seguridad por cada intento** |

**Por qué se rota después de cada uso y no solo al año:** en una emergencia el secreto se teclea, a
veces con prisa, a veces en una máquina que no es la habitual y con alguien al lado. Después de eso
ya no es un secreto de una sola persona; es uno que *probablemente* lo siga siendo. Rotarlo cuesta
diez minutos.

### 3.5 Rotación y reemplazo: **de uno en uno**

Hay dos ranuras de configuración, A y B, y **solo se toca una a la vez**. La otra permanece válida
durante toda la operación; ese es el invariante que sustituye al «nunca menos de dos activos» de un
padrón que ya no existe.

1. **Generar** el secreto nuevo y su hash para la ranura que se reemplaza.
2. **Entregarlo** a su titular y anotar el cambio en el acta (§3.3).
3. **Sustituir** esa ranura en configuración y reiniciar el servicio.
4. **Probar** (§3.6) con el custodio nuevo **y** con el que no se tocó.
5. Si la prueba falla → **revertir esa ranura a su valor anterior**. Nunca se avanza dejando una
   ranura rota: el sistema quedaría con una sola custodia real y la doble aprobación sería
   decorativa.
6. Repetir con la otra ranura **solo cuando la primera esté probada**.

> **Nunca las dos a la vez.** Cambiar A y B en el mismo reinicio significa que, si el fichero de
> configuración tenía una errata, no queda ninguna llave buena — y el mecanismo que existía para
> recuperar el gobierno se convierte en otra cosa que hay que recuperar.

### 3.6 Prueba semestral

**Cada seis meses**, y siempre después de una rotación, cada custodio verifica su secreto. La
verificación:

- **no emite ninguna concesión** y no autoriza nada;
- deja evento de seguridad, pase o falle;
- se anota en el registro de §5, que es donde vive la fecha — no hay columna que sellar.

La prueba existe porque el modo de fallo real no es que alguien filtre el secreto: es que el día de
la emergencia el sobre esté vacío, la caja fuerte tenga otra combinación o el gestor de contraseñas
se haya migrado sin esa entrada.

---

## 4. Procedimiento de recuperación de emergencia

> Aplicable cuando V38 esté **encendida en esta instalación**. Hasta entonces, ver la sección 2.

### 4.1 Cuándo se activa

Los tres a la vez:

1. El tenant **no tiene ningún `TENANT_ADMIN` operativo** (cuenta activa + MFA activo + sin cambios
   obligatorios pendientes);
2. la vía ordinaria está agotada: no hay otro administrador que revoque el factor (nivel 2) y el
   titular **no tiene códigos de respaldo** utilizables;
3. hay una persona identificada a la que devolverle el gobierno.

Si falta cualquiera de los tres, **no se activa**. En particular: «es más rápido» no es un motivo.

### 4.2 Antes de tocar nada

| # | Paso | Quién |
|---|---|---|
| 1 | Confirmar el estado real en *Seguridad y accesos* y dejar constancia de qué se vio | Operador |
| 2 | Identificar a la **persona objetivo** por su `idPersona`, no por su nombre | Operador |
| 3 | Redactar el **motivo**: qué pasó, desde cuándo, qué se intentó antes | Operador |
| 4 | Contactar a los **dos custodios** por separado. Nunca en el mismo hilo, y nunca pidiéndoles el secreto | Operador |

### 4.3 La recuperación

Se ejecuta **desde el host donde corre el backend**, contra el conector de gestión ligado a
`127.0.0.1`. No es alcanzable desde la red, y ese es el control: no hay «recuperación remota».

```powershell
powershell -File backend-spring/operacion/recuperar-gobierno.ps1
```

| # | Paso | Quién | Qué queda registrado |
|---|---|---|---|
| 1 | Se identifica el operador y se declara tenant, persona objetivo y motivo | Operador | Emisión en `PENDIENTE` |
| 2 | El **custodio A** teclea su secreto, por consola y con entrada oculta | Custodio A | Primera aprobación |
| 3 | El **custodio B** teclea el suyo, sin ver el paso anterior | Custodio B | Segunda aprobación → la concesión pasa a `VIGENTE` |
| 4 | La herramienta muestra el secreto de la concesión **una sola vez** | — | `RECUPERACION_EMERGENCIA_EMITIDA` |
| 5 | Se aplican **solo** las acciones que hagan falta: reactivar cuenta, revocar MFA, reponer membresía | Operador | `RECUPERACION_EMERGENCIA_APLICADA`, una por acción |
| 6 | El titular **enrola su segundo factor él mismo** | Persona objetivo | `MFA_ACTIVADO` |
| 7 | La concesión **se cierra sola** al haber otra vez un administrador operativo | — | Cierre auditado |

**Los secretos se piden por consola, nunca por parámetro.** Un secreto en la línea de comandos queda
en el historial del intérprete y en la lista de procesos de la máquina.

### 4.4 Lo que no se hace, nunca

- **No** se ejecutan las tres acciones «por si acaso»: si la cuenta ya está activa, solo se repone
  lo que falte. Cada acción se puede aplicar **una sola vez** por concesión.
- **No** se le fija la contraseña ni el factor a la persona objetivo. Se le devuelve el acceso; el
  factor lo pone ella.
- **No** se prorroga la ventana de 30 minutos desde la concesión en curso: prorrogar exige
  **aprobación dual nueva**. Si se agotó, se emite otra.
- **No** se usa la concesión para «aprovechar y mirar» nada: no alcanza ningún dato comercial, y el
  intento queda auditado.
- **No** se comparte el secreto de la concesión por ningún canal. Vive en la consola del operador y
  muere al cerrarse.

### 4.5 Después, el mismo día

| # | Cierre | Comprobación |
|---|---|---|
| 1 | **Rotar los dos secretos de custodio**, uno tras otro y probando cada uno (§3.5) | Acta actualizada y registro de §5 |
| 2 | Verificar que la concesión está cerrada y sin capacidad | Estado ≠ `VIGENTE` |
| 3 | Verificar que el tenant tiene **≥ 2** administradores operativos | *Seguridad y accesos* |
| 4 | Comprobar que **no** se creó ninguna cuenta ni rol nuevos | Aviso de gobierno |
| 5 | Registrar el ejercicio en §5 y archivar el motivo con la firma de los tres | Acta |

---

## 5. Registro de ejercicios, rotaciones y usos reales

> Se rellena a mano. Un simulacro que no se registra no se distingue de uno que no se hizo.

| Fecha | Tipo | Custodios | Operador | Resultado / observaciones |
|---|---|---|---|---|
| *(pendiente)* | Simulacro / Rotación / Uso real | | | |

**El simulacro completo es requisito de salida**, no una buena práctica: está en el orden aprobado
(§18.16 del diseño) y consiste en recorrer el procedimiento entero en `dev` —incluida la parte en la
que el titular vuelve a enrolar— y comprobar que la concesión **no leyó ningún dato comercial, no
creó ninguna persona, no fijó ninguna contraseña y no dejó cuenta ni rol nuevos**.

---

## 6. Qué falta, y para cuál de las dos puertas

| # | Requisito ([§17](../../docs/ai/plan-s0-6-mfa-y-break-glass.md)) | Estado | Puerta |
|---|---|---|---|
| 3 | Ciclo de vida de custodios | **Documentado aquí** (§3) | — |
| 4 | Secretos robustos y rotables | **Reglas escritas aquí** (§3.4-3.5) | — |
| 1, 2, 5, 6, 7, 8 | Herramienta, conector local, aprobaciones, secreto de concesión, acciones idempotentes, consumo atómico, cierre automático | **En construcción (V38)** | Implementación |
| — | **Designación real** de los dos custodios y del operador (§3.3) | **Pendiente** | Activación |
| 9 | **Canal externo real** | Pendiente | Activación — el arranque en `prod` falla sin él |

**Lo que se puede hacer sin la designación:** construir V38 entera y verificarla, porque los E2E
generan sus propios secretos por corrida y los destruyen con la base efímera. Un fixture no se
convierte en custodio por participar en una prueba.

**Lo que no se puede hacer sin la designación:** encender esto en una instalación real. El arranque
en `prod` se detiene si `RECUPERACION_EMERGENCIA_HABILITADA` está activa sin los dos hashes reales
y sin notificador externo — una recuperación de emergencia sin aviso externo puede usarse
**precisamente** cuando nadie está dentro para ver la campana.
