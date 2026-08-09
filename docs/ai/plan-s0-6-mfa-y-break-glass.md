# Bloque 6 — MFA administrativo y recuperación de emergencia (DISEÑO)

**Fecha: 2026-08-06** · **V37 IMPLEMENTADO Y VERIFICADO** · **V38 NO aprobado todavía (§17).**

Desarrolla el §6.1 y el §6.2 del [Plan S0](plan-s0-seguridad-identidad-gobierno.md). Se apoya en el
Bloque 5, ya cerrado: existe la banda `TENANT_ADMIN` como cosa propia y hay una fuente de verdad
para saber quién gobierna.

> **Historial de revisiones, porque explica el diseño.**
>
> **1ª versión (rechazada).** Proponía una **cuenta break-glass permanente con banda
> `PLATFORM_ADMIN`** y contraseña en custodia partida. Se rechazó por cuatro motivos: mezclaba
> administración de plataforma, recuperación técnica, acceso extraordinario y gobierno de tenants en
> una identidad privilegiada permanente; una contraseña partida **sigue siendo una contraseña
> reutilizable** y **no prueba que participaran dos custodios**; los códigos de respaldo de 50 bits
> con SHA-256 simple no cumplían el mínimo; y el límite de intentos "por desafío" **se elude**
> pidiendo desafíos nuevos.
>
> **2ª versión (corregida en lo estructural, insuficiente en el detalle).** Sustituyó la cuenta por
> una **concesión técnica**, pero dejaba cinco controles como "propuestas", aceptaba el paso TOTP
> futuro, no definía el invariante de administrador **operativo**, no decía **quién ejecuta** la
> concesión ni cómo se gobiernan los custodios, y confiaba la doble aprobación a un `CHECK` de
> desigualdad de textos.
>
> **3ª versión (esta).** Los cinco controles pasan a **aprobados**; el paso futuro se retira; se
> define el invariante de disponibilidad administrativa; la doble aprobación pasa a **dos registros
> independientes**; y el consumo de la concesión se vuelve atómico e idempotente por tipo.

---

## 1. La pregunta que ordena el bloque

No es *"¿cómo añadimos TOTP?"*. Es:

> **Si el segundo factor se pierde, ¿quién repone el acceso, y por qué eso no es una puerta trasera?**

Tres niveles, en orden, y el tercero existe **porque** los dos primeros pueden agotarse:

| Nivel | Quién | Condición de entrada |
|---|---|---|
| **1** | El titular, con un código de respaldo | Conserva códigos (§5) |
| **2** | Otro `TENANT_ADMIN`, que **revoca** el factor (no lo fija) | Queda al menos otro administrador **operativo** (§6.2, §7) |
| **3** | **Concesión técnica de recuperación**, con doble aprobación | Es el **último** administrador operativo, o no queda ninguno (§9) |

El nivel 2 es la **razón operativa** por la que el Bloque 5 retiró el límite de un administrador
(D-31): con uno solo, un teléfono perdido es una caída de gobierno.

---

## 2. Restricciones que no se pueden mover

| # | Restricción | Consecuencia |
|---|---|---|
| **R1** | El token admite **tres roles** y su formato es byte-compatible con GlassFish | El estado de MFA **no viaja en el token**. Ni "este usuario tiene MFA", ni "se autenticó con MFA hace N minutos" (§8) |
| **R3** | `LoginResponse` está **congelado** | El desafío va por endpoints **aditivos** (§4) |
| **R5** | No se edita una migración aplicada | Tipos de evento y de token nuevos entran por migración propia |

Y la regla del proyecto que este bloque **no** relaja:

> **Nadie fija la credencial de otro.** Aplicada al segundo factor: **nadie enrola el factor de
> otro.** Un administrador puede **revocar** el de un compañero; enrolarlo es siempre acto del
> titular.

**Lo que este bloque NO hace:** no emite `PLATFORM_ADMIN`, no crea ninguna cuenta permanente, y no
toca `concesion_acceso_tenant`. La administración de plataforma sigue sin existir.

---

## 3. Enrolamiento TOTP

### 3.1 Parámetros

| Parámetro | Valor | Por qué |
|---|---|---|
| Algoritmo | **HMAC-SHA1** | RFC 6238 admite SHA-1, SHA-256 y SHA-512. Se elige SHA-1 por **compatibilidad más amplia** con los autenticadores reales, no porque SHA-256 rompa todos. **SHA-1 no es una debilidad práctica en HMAC-TOTP**: el riesgo real es el *phishing* |
| Dígitos | **6** | Ídem. El espacio corto se compensa con el límite de intentos (§4.3) |
| Periodo | **30 s** | Estándar |
| Secreto | **160 bits**, Base32 sin relleno | Longitud del bloque de SHA-1 |
| Pasos admitidos | **actual y anterior. NO el siguiente** | §3.2 |

> **TOTP no es resistente al phishing.** Es la primera versión, no la definitiva: **WebAuthn** queda
> como evolución cuando existan HTTPS y dominio definitivos (Fase 5).

### 3.2 Por qué se retira el paso futuro (corrección)

La versión anterior admitía `±1` —anterior, actual y siguiente, 90 s de ventana—. **Contradice el
anti-replay** de §3.4:

- aceptar el paso `+1` deja **usar un código antes de su ventana natural**;
- y al sellarlo en `ultimo_paso`, el código **actual** y el **siguiente** quedan por debajo del
  último aceptado, así que el usuario se queda fuera durante hasta un minuto por haber acertado.

Aceptar el futuro **no hace falta** con relojes sincronizados: la deriva que importa en la práctica
es la del cliente atrasado, no adelantado.

```
Pasos admitidos: actual (t) y anterior (t-1).      Ventana efectiva: 60 s.
```

Si algún día hiciera falta el `+1`, habría que documentar cómo se evita adelantar `ultimo_paso`.
**La alternativa simple es mejor y es la que se implementa.**

### 3.3 El secreto en reposo, y la clave que lo cifra

AES-256-GCM con clave de entorno. **No es hash**: un TOTP hay que poder recalcularlo. Por eso la
clave **no puede vivir en PostgreSQL** — si vive ahí, cifrar no protege de nada.

```
factor_autenticacion:
    secreto_cifrado BYTEA     -- criptograma; el tag va integrado si la biblioteca
                              --  lo administra (no se asume representación aparte)
    nonce           BYTEA
    version_clave   SMALLINT  -- cuál de las claves lo cifró
```

**Perder esta clave deja a todos los administradores sin segundo factor**: es un fallo de
*disponibilidad*, y por eso su gestión es diseño, no nota al pie.

| Aspecto | Regla |
|---|---|
| Dónde vive | **Fuera de PostgreSQL**, inyectada por el orquestador |
| Versionado | `version_clave` por fila: descifra lo viejo mientras cifra con lo nuevo |
| Rotación | **Clave actual y anterior** a la vez durante la ventana; un barrido recifra y sube la versión |
| Respaldo | **Cifrado y separado**. **Nunca dentro del dump**: un respaldo que lleva la base y su clave no está cifrado, está acompañado |
| Restauración | Entra en `operacion/restaurar-verificar.ps1` con **comprobación de descifrado** de un factor conocido, sin exponer la clave en el log |
| Pérdida | Escenario declarado: obliga a revocar todos los factores y reenrolar |

`ValidadorConfiguracionSeguridad` gana una comprobación: en `prod`, `MFA_CLAVE_CIFRADO` ausente,
corta o igual a un literal conocido **detiene el arranque** (criterio de D-S0-20).

### 3.4 Anti-replay: atomicidad, no comparación

> **Al implementarlo apareció un segundo sitio con el mismo problema, y no era el TOTP.** El
> contador de intentos **del desafío** se escribía en la misma transacción que después lanzaba el
> error, así que el `rollback` lo borraba: el límite de cinco intentos no contaba nada y bastaba
> insistir sobre el mismo desafío. Se movió a transacción propia (`REQUIRES_NEW`), que es lo que ya
> hacen `EventosSeguridad` y `BloqueoAccesos` **por esta misma razón**. Regla que conviene
> generalizar: *lo que cuenta fallos no puede viajar en la transacción que falla*.

Guardar `ultimo_paso` y comparar **no basta**: dos peticiones simultáneas leen el mismo valor y
aceptan el mismo código antes de que ninguna escriba. Un OTP debe aceptarse **una sola vez** durante
su vigencia, y eso es una condición de carrera.

**El veredicto es cuántas filas afectó la actualización:**

```sql
UPDATE factor_autenticacion
   SET ultimo_paso = :paso, ultimo_uso_en = now()
 WHERE id = :id
   AND (ultimo_paso IS NULL OR ultimo_paso < :paso)
```

`1` → válido y consumido. `0` → **ya se usó o es anterior**, se rechaza. Solo una petición gana. La
alternativa (`SELECT … FOR UPDATE`) se descarta porque mantendría el bloqueo mientras se calcula el
HMAC.

### 3.5 Obligatoriedad sin cortar el gobierno

D-S0-19 fija MFA **obligatorio** para `TENANT_ADMIN`; **posible pero no exigido** para `BROKER` y
`AGENTE`. Aplicado tal cual, el día del despliegue el administrador existente quedaría fuera.

**`debe_enrolar_mfa` + sesión capada**, gemelo de `debe_cambiar_contrasena`:

```
Con debe_enrolar_mfa = true, pasan SOLO:
    GET  /perfil          GET  /perfil/mfa
    POST /perfil/mfa      POST /perfil/mfa/confirmar
    POST /auth/logout
El resto: 403 con código ENROLAMIENTO_MFA_REQUERIDO
```

**No alcanza a administrar miembros, ni a clientes, ni a ninguna operación de gobierno.** El logout
entra porque encerrar a alguien en una sesión de la que no puede salir es un fallo, no una medida.

La migración pone `debe_enrolar_mfa = true` a toda cuenta con membresía `TENANT_ADMIN` activa.

---

## 4. El login con segundo factor

### 4.1 Desafío en dos llamadas

```
POST /auth/mfa/desafio     { usuario, contrasena }
    sin MFA activo  → 200 + LoginResponse   (el cuerpo congelado, intacto)
    con MFA activo  → 202 + { desafio, expiraEn, metodo: "TOTP" }

POST /auth/mfa/verificar   { desafio, codigo }
    → 200 + LoginResponse
```

**El SPA usa siempre el mismo camino**: 200 es entrar, 202 es pedir el código. Se descartó la
variante de un solo cuerpo porque obliga al cliente a saber de antemano lo que no puede saber, y
quema códigos legítimos contra contraseñas mal escritas.

`POST /auth/login` (el congelado) **responde 401 a las cuentas con MFA activo**.

### 4.2 Qué es el `desafio`

Secreto de un solo uso, **5 minutos**, **hasheado en la base**, que **no autoriza nada**: no es un
JWT, no pasa por el filtro, no sirve para ningún otro endpoint. Vinculado a:

```
credencial · organización · emitido_en · expira_en · intentos · estado · contexto_autenticacion
```

**Se reutiliza `token_acceso`** (V31) con el tipo `DESAFIO_MFA`, con cuatro condiciones: el **tipo es
obligatorio en toda consulta** —un desafío no puede canjearse como recuperación de contraseña, ni al
revés—; la tabla gana `intentos` y `estado`; se **generalizan comentarios y nombres de dominio**; y
existe **revocación específica**, distinta de la invalidación por emisión.

> **Tensión honesta:** con esas columnas la reutilización deja de ser gratis. Se mantiene porque los
> cinco invariantes que importan —hash en vez de valor, un solo uso sellado en la misma transacción,
> caducidad obligatoria, emitir invalida el anterior, y quién lo emitió— ya están construidos y
> probados ahí, y duplicarlos es como se desincronizan.

### 4.3 Límite de intentos: tres controles

Un TOTP de 6 dígitos son 10⁶ y ahora hay **2** códigos válidos a la vez (§3.2): quien tenga la
contraseña acierta con ~500.000 intentos. **Limitar solo por desafío no sirve** — se piden desafíos
nuevos y el contador vuelve a cero. Emitir un secreto nuevo **no puede reiniciar el conteo
acumulado**.

| Control | Alcance | Efecto |
|---|---|---|
| **Por desafío** | 5 intentos | Al sexto el desafío muere |
| **Acumulado por cuenta** | ventana deslizante, **no se reinicia al emitir desafío** | Espera progresiva |
| **Por IP / bloque** | `BloqueoAccesos` existente | El escalado que ya protege el login |

```
 5 fallos acumulados → exige desafío nuevo
10 fallos            → espera de 5 minutos
15 fallos            → espera de 15 minutos + evento de seguridad
```

**Sin bloqueo indefinido**: bloquear para siempre es una denegación de servicio contra el
administrador. Un login MFA correcto **reduce gradualmente** los contadores; no los borra de golpe,
para que una ráfaga no se limpie con un acierto.

**Se reutiliza `intento_acceso`** (V30) con `clave_tipo = 'MFA_CUENTA'`: ya tiene ventana deslizante,
escalado y **hash del identificador**.

---

## 5. Códigos de respaldo

| Decisión | Valor |
|---|---|
| Cantidad | **8** |
| Formato | `IDENT-SSSS-SSSS-SSSS-SSSS` — identificador público + **80 bits** de secreto |
| Identificador | 4 caracteres Base32 Crockford, **único por factor**, en claro e indexado |
| Secreto | 16 caracteres Base32 Crockford = **80 bits aleatorios** |
| Almacenamiento | **Hash lento con sal** (`PasswordHasher`, el mismo aprobado para contraseñas) |
| Uso | **Un solo uso**, `usado_en` sellado en la transacción que emite la sesión |
| Regenerar | Invalida **todos** los anteriores, usados o no |
| Aviso | Al usar uno la respuesta dice **cuántos quedan**; con ≤ 2, `GET /perfil` lo señala |

**El identificador es lo que hace viable el hash lento.** Sin él habría que probar contra las 8
filas —ocho derivaciones lentas por intento y una palanca de DoS regalada—, que era el argumento con
el que la 1ª versión justificaba SHA-256 simple. Con identificador **se verifica una sola fila**.

**Consumir un código NO desactiva el MFA.** Solo deja entrar, con la sesión marcada como
**autenticada por recuperación**. Reemplazar el factor desde ahí exige **escribir la contraseña otra
vez** (§8).

---

## 6. Recuperación ordinaria

### 6.1 Nivel 1 — autorrecuperación

El titular entra con un código y reemplaza su factor. El código no desactiva MFA, no se reutiliza y
**no concede permisos adicionales**.

### 6.2 Nivel 2 — revocación por otro `TENANT_ADMIN`

**Quién puede revocar el factor de quién** (regla completa, faltaba):

| Actor | Puede revocar el MFA de | No puede |
|---|---|---|
| `TENANT_ADMIN` | **Cualquier usuario de su tenant** — agente, broker u otro administrador | Sí mismo; ni a nadie de otro tenant (**404**) |
| `BROKER` | **Nadie.** Ni a sus agentes | Administrar cuentas es gobierno (D-S0-18) |
| `AGENTE` | Nadie | — |

**Y el corte que evita dejar la organización sin gobierno recuperable:**

> Si el afectado es el **último `TENANT_ADMIN` operativo** (§7), la revocación ordinaria **se
> rechaza**. Esa revocación exige el **nivel 3**.

Sin ese corte, un administrador podría revocarse el factor a sí mismo por interpuesta persona —o
revocar el del único compañero— y dejar el tenant sin nadie capaz de gobernar ni de recuperar.

**Condiciones de la revocación ajena:**

| Condición | Motivo |
|---|---|
| **MFA reciente** del actor (§8) | Quien repone un factor ajeno debe haber probado el suyo hace minutos, no hace horas |
| **No contra sí mismo** | Sería autoconcederse la salida del segundo factor |
| **Motivo obligatorio** | Sin motivo escrito no se distingue después de un abuso |
| **Confirmación explícita** que nombre el efecto | "Tendrá que volver a enrolarlo" |
| Invalida **todas** las sesiones del afectado | §10 |
| Activa `debe_enrolar_mfa` | El titular enrola personalmente |
| Evento **+ alerta persistente** a todos los `TENANT_ADMIN` | §11 |

---

## 7. Invariante de disponibilidad administrativa

La V34 garantiza "≥ 1 membresía `TENANT_ADMIN` activa". **No basta.** Una cuenta bloqueada, sin
factor o con un cambio obligatorio pendiente **no es un administrador operativo**: figura en el
recuento y no puede gobernar.

**Definición — `TENANT_ADMIN` operativo:**

```
membresía TENANT_ADMIN activa
  Y credencial activa (estado_administrativo = 'A')
  Y factor MFA en estado ACTIVO
  Y NOT debe_cambiar_contrasena
  Y NOT debe_enrolar_mfa
```

**Antes de cada una de estas operaciones, el sistema comprueba que quede al menos otro administrador
operativo:**

- suspender una cuenta;
- dar de baja una membresía;
- **revocar un factor MFA**;
- cambiar el rol de una membresía;
- aplicar una acción de recuperación de emergencia.

Si el resultado sería cero → `ReglaNegocioException` con el mensaje que nombra la causa, y la
operación no entra.

**Dónde vive.** Igual que en D-S0-9: la **guarda de aplicación da el mensaje** y **el trigger da la
garantía**. V37 **sustituye la función del trigger de V34** por una que cuente administradores
*operativos*, no membresías. Es una dependencia nueva del trigger sobre `factor_autenticacion` y
`credencial_usuario`, y se acepta a propósito: un invariante que solo cuenta filas de una tabla no
es el invariante que hace falta.

> **Consecuencia deseada:** la primera cuenta administrativa de un tenant **no puede quedarse sin
> MFA** una vez enrolada, porque nadie —ni ella misma— puede revocarlo por la vía ordinaria. Para
> eso está el nivel 3.

---

## 8. Reautenticación reforzada: "MFA reciente" ≠ "JWT válido"

El token está congelado (R1) y **no lleva** cuándo se probó el segundo factor. Inferir "MFA
reciente" de que la sesión nació con MFA hace horas sería falso: una sesión robada a media tarde
pasaría el control.

**Token de elevación**, del lado servidor:

| Propiedad | Valor |
|---|---|
| Cómo se obtiene | Desafío de reautenticación específico: `POST /perfil/elevacion` con contraseña **+** TOTP vigente |
| Vida | **5 minutos** |
| Ligado a | **credencial + tenant + acción concreta** |
| Uso | **Un solo uso**, consumido en la transacción de la operación |
| Dónde vive | `token_acceso` con tipo `ELEVACION`, hasheado como los demás |
| Transporte | Cabecera propia, **nunca** en la URL |

**Operaciones que lo exigen:**

| Acción | Confirmación |
|---|---|
| Activar MFA | Contraseña **+ primer TOTP** |
| Cambiar factor | Contraseña **+** factor vigente o código de respaldo |
| Regenerar códigos | Contraseña **+** TOTP vigente |
| Desactivar el factor propio | Contraseña **+** TOTP vigente. **No desde una sesión común** |
| **Revocar factor ajeno** | `TENANT_ADMIN` **+ token de elevación vigente +** motivo |
| Recuperación de emergencia | Dos aprobaciones **+** concesión (§9) |

---

## 9. Nivel 3 — concesión técnica de recuperación

**No es una cuenta. No es un rol. No es una sesión.** Es un permiso temporal, acotado y de un solo
uso, para devolver el gobierno de **un** tenant a **una** persona.

### 9.1 Por qué no es una cuenta

Una cuenta —aunque esté inactiva y con la contraseña partida— es una identidad privilegiada
**permanente**, con contraseña reutilizable, en la que se puede *iniciar sesión*. La concesión **no
produce token ni sesión**: no hay nada en lo que entrar. Cada acción es una llamada individual que
presenta el secreto y consume una capacidad.

### 9.2 Quién la ejecuta, y desde dónde

**Herramienta concreta:** `backend-spring/operacion/recuperar-gobierno.ps1`.

| Pregunta | Respuesta |
|---|---|
| ¿Qué ejecuta? | Llama a una **interfaz administrativa local**, no a la API pública |
| ¿Dónde escucha esa interfaz? | Conector de gestión **ligado a `127.0.0.1`**, en puerto propio, **no publicado por Docker ni por el proxy inverso**. No es alcanzable desde la red |
| ¿SQL libre? | **Nunca.** El script llama a un endpoint de gestión; el endpoint llama a un service. Toda la lógica y todas las guardas son las mismas del producto |
| ¿Cómo se autentica la herramienta? | Por dos cosas a la vez: **llegar por el conector de gestión** y **presentar las dos aprobaciones** (§9.3) |
| ¿Identidad del operador? | `OPERADOR` obligatorio, se pide por consola y **se graba en la auditoría** junto a los dos custodios |
| ¿Los secretos por línea de comandos? | **No**: se piden por consola con entrada oculta, para que no queden en el historial ni en la lista de procesos |
| ¿Desde una estación cualquiera? | No: exige acceso al host donde corre el backend. Ese es el control de red |

### 9.3 Dos aprobaciones independientes, no un `CHECK` de textos

La versión anterior confiaba en `CHECK (custodio_a <> custodio_b)`. **Eso solo compara dos textos de
una fila**: un operador escribe dos nombres distintos y pasa.

**La doble aprobación pasa a ser estructural**: dos registros, cada uno con su verificación.

```
aprobacion_recuperacion
    id
    id_concesion            → concesion_recuperacion
    identificador_custodio  VARCHAR(60)  NOT NULL   -- el de configuracion, no una FK
    aprobado_en             TIMESTAMPTZ  NOT NULL
    orden                   SMALLINT     NOT NULL
    UNIQUE (id_concesion, identificador_custodio)   -- el mismo no aprueba dos veces
```

**La concesión pasa a `VIGENTE` solo cuando existen dos aprobaciones válidas de custodios
distintos.** Mientras haya una, queda en `PENDIENTE` y no autoriza nada.

> **`hash_evidencia` se retira.** Guardaba el hash del secreto presentado, y eso es material
> derivado de un secreto vivo dentro de una tabla que un administrador puede leer: regala un
> objetivo de fuerza bruta a cambio de nada, porque la verificación ya ocurrió contra el hash de
> configuración. Lo que prueba la aprobación es la **fila**, no una copia del secreto.

#### Tres identidades, no dos *(2026-08-06 — D-S0-52)*

El diseño enumeraba `custodio_a` y `custodio_b` y dejaba al **operador** solo en la consola. No
basta: la regla «quien ejecuta no custodia» es inaplicable si la identidad de quien ejecuta no se
conserva junto a las otras dos. La concesión guarda las **tres**, y la base las separa:

```sql
CHECK (custodio_a <> custodio_b)
CHECK (operador <> custodio_a)
CHECK (operador <> custodio_b)
```

**No vale que el operador aparezca solo en un log de texto.** Un registro que se puede reescribir no
prueba quién estuvo; la fila de la concesión, sí.

> **Lo que esto prueba, sin adornos:** que se presentaron **dos secretos en manos separadas**.
> Ningún control de software prueba que había dos personas. Lo que sí mejora frente a la contraseña
> partida es sustancial: los secretos **nunca se combinan** en una credencial reutilizable, se
> verifican por separado contra **filas distintas**, y el `UNIQUE` impide que uno cubra las dos
> partes.

### 9.4 Los custodios: configuración, no subsistema *(corregido 2026-08-06 — D-S0-51)*

> **Corrección.** Una versión anterior de esta sección convertía a los custodios en **una tabla**
> con altas, bajas, rotación y pruebas selladas. Se retira: es un subsistema de administración de
> identidades para gobernar **dos** secretos que cambian una vez al año. La vigencia de un custodio
> es **gobierno organizacional** —un acta firmada—, no estado de aplicación.

Los custodios se verifican contra **dos hashes en configuración**, fuera de PostgreSQL:

```
RECUPERACION_CUSTODIO_A_ID     RECUPERACION_CUSTODIO_A_HASH
RECUPERACION_CUSTODIO_B_ID     RECUPERACION_CUSTODIO_B_HASH
```

> **Por qué también el identificador y no solo el hash.** Sin él, «custodio A» sería una etiqueta de
> ranura y las tres desigualdades de §9.3 no podrían compararse contra la identidad del operador:
> el `CHECK` pasaría siempre y no probaría nada. Con identificador, `operador <> custodio_a` es una
> comprobación real.

| Regla | Valor |
|---|---|
| Secreto | Aleatorio, **≥ 128 bits**, generado por el sistema; el custodio no lo elige |
| Almacenamiento | **PBKDF2 con sal**, como una contraseña, **en configuración** — nunca en la base |
| Dónde lo guarda el custodio | Gestor de contraseñas de la organización o sobre sellado; **queda escrito en el procedimiento** |
| Qué guarda la base | **Solo los identificadores que participaron en cada concesión.** Ni secretos, ni hashes, ni padrón de custodios |
| Rotación | **Anual y después de cada uso** — política operativa, no tabla |
| Reemplazo | **De uno en uno**, manteniendo siempre válido el otro. Procedimiento en [`operacion/custodios-y-recuperacion-de-emergencia.md`](../../backend-spring/operacion/custodios-y-recuperacion-de-emergencia.md) §3 |
| Prueba fallida | **Se revierte la configuración.** Nunca se deja el sistema con una sola custodia |
| Intentos | **Bloqueo progresivo** y **evento por cada intento fallido** |
| Prohibición | **Un custodio no puede conocer las dos partes.** Es regla de procedimiento, y por eso está escrita |

> **Evolución declarada:** dos aprobaciones **firmadas** independientes o llaves físicas
> (FIDO2) son mejores que dos secretos compartidos. Queda como mejora posterior, no como
> requisito de este bloque.

### 9.5 El secreto de la concesión

| Propiedad | Valor |
|---|---|
| Entropía | **256 bits aleatorios** |
| Codificación | **Base64URL** |
| Almacenamiento | **SHA-256** — adecuado *porque* la entropía está fijada en 256 bits |
| Presentación | **Una sola vez**, al completarse la segunda aprobación |
| Comparación | **Tiempo constante** |
| Transporte | Cabecera. **Nunca en parámetros de URL** |
| Cabeceras | `Cache-Control: no-store` |
| Logs | **Nunca** |
| Fin de vida | Invalidación **inmediata** al agotarse las acciones, al cerrarse o al caducar |

### 9.6 Qué puede hacer, exactamente

Tres tipos de acción, **como máximo una vez cada uno**, y solo sobre `id_persona_objetivo`:

1. **Reactivar** la cuenta administrativa existente.
2. **Revocar** su factor MFA (deja `debe_enrolar_mfa`).
3. **Restablecer** su membresía `TENANT_ADMIN`.

```
accion_recuperacion:  UNIQUE (id_concesion, tipo)
```

Sin ese `UNIQUE`, `max_acciones = 3` deja ejecutar **tres veces la misma**. Y cada acción es
**idempotente**: aplicarla sobre un estado que ya la cumple no falla ni consume capacidad.

**No obliga a ejecutar las tres.** Si la cuenta ya está activa, basta con restablecer la membresía
si falta y revocar el MFA si corresponde. **La concesión se cierra sola en cuanto el tenant vuelve a
tener un `TENANT_ADMIN` operativo** (§7), aunque haya usado una sola acción.

| No puede | |
|---|---|
| Leer captaciones, clientes, solicitudes, documentos, contratos ni comisiones | **Ningún** dato comercial |
| Crear personas | Repone gobierno, no puebla el tenant |
| Fijar contraseñas | La regla del proyecto no tiene excepciones |
| Tocar otro tenant u otra persona | El alcance se fija al emitir y es inmutable |
| Modificar su propio alcance o prorrogarse | La prórroga exige **aprobación dual nueva** |
| Escribir en `historial_estado` | No produce hechos de negocio |

### 9.7 Consumo atómico

`acciones_consumidas` se actualiza **condicionalmente**, y el veredicto vuelve a ser cuántas filas
afectó — mismo patrón que el anti-replay:

```sql
UPDATE concesion_recuperacion
   SET acciones_consumidas = acciones_consumidas + 1
 WHERE id = :id
   AND estado = 'VIGENTE'
   AND expira_en > now()
   AND acciones_consumidas < max_acciones
```

`0` filas → agotada, caducada o cerrada. **La acción concreta, el consumo y la auditoría van en una
única transacción**: si la acción falla, no se consume capacidad; si se consume, quedó auditada.

### 9.8 Ventana y caducidad

**30 minutos**, prorrogables **una sola vez** y solo con **aprobación dual nueva** — no por quien
tiene la concesión en la mano. Media hora sobra para tres acciones; cuatro horas era una ventana
desproporcionada.

Se comprueba la vigencia **en cada aplicación**, no solo con el `@Scheduled` que cierra las vencidas.
Las dos: el barrido puede no haber corrido. **La concesión caduca aunque el proceso programado no se
ejecute.**

---

## 10. Revocación de sesiones

Se reutiliza `sesiones_invalidas_desde` (V29). Qué la mueve:

| Hecho | ¿Invalida? | Por qué |
|---|---|---|
| Activar el factor | **Sí** | Las sesiones abiertas nacieron sin segundo factor |
| Reemplazar el factor | **Sí** | Cambia la credencial |
| **Revocar** el factor (propio o ajeno) | **Sí** | Momento de mayor riesgo: si la pidió un atacante, sus sesiones caen con ella |
| Regenerar códigos | **No** | No cambia quién eres ni cómo entras |
| Consumir un código de respaldo | **No** | Es un login; invalidar aquí echaría al usuario al entrar |
| Acción de recuperación de emergencia | **Solo las del afectado** | Tumbar las sesiones de la instalación en una emergencia es una DoS disfrazada de medida |

**Sesiones de 2 h y `sudo mode`** no entran aquí: viajan en el `exp` del token, que valida el legado.
Post-corte (S0.7).

---

## 11. Auditoría y aviso: no son lo mismo

| Momento | Qué hay |
|---|---|
| **Ahora** | Evento de seguridad **+ aviso persistente dentro de la aplicación** para todos los `TENANT_ADMIN` del tenant. **Implementado como LECTURA de `evento_seguridad`** (`GET /seguridad/avisos`), no como fila en `alerta` — ver **D-S0-49**: un aviso que se puede atender lo silencia, antes que nadie, quien acabe de revocar un factor sin permiso |
| **Antes de producción** | **Canal externo real**. Requisito de salida, no mejora |
| **Condición de arranque de V38** | §17: **la concesión no se habilita en producción sin canal externo**, y eso es una comprobación de arranque, no una nota |
| **Hasta entonces** | La documentación **no afirma** que el administrador será avisado fuera de la aplicación. No lo será |

Tipos de evento nuevos (V30 ya trae `MFA_OK` y `MFA_FALLIDO`):

```
MFA_ACTIVADO · MFA_REVOCADO · MFA_CODIGOS_REGENERADOS · MFA_CODIGO_RESPALDO_USADO
ELEVACION_EMITIDA · ELEVACION_FALLIDA
RECUPERACION_EMERGENCIA_EMITIDA · RECUPERACION_EMERGENCIA_APLICADA
RECUPERACION_EMERGENCIA_CADUCADA · CUSTODIO_APROBACION_FALLIDA
```

`BREAK_GLASS_ACTIVADO`, previsto en V30, **queda sin emisor**: ese mecanismo no se construye. Se deja
en el `CHECK` porque retirarlo cuesta otra migración y no aporta nada.

**Higiene, verificada por test:** ni secretos TOTP, ni códigos, ni desafíos, ni tokens de elevación,
ni secretos de custodio o de concesión, ni la clave de cifrado — en `detalle_json`, en logs ni en
respuestas.

### Confidencialidad

- El secreto TOTP **solo** se muestra en el enrolamiento; no hay endpoint que lo relea.
- `Cache-Control: no-store` en enrolamiento, códigos, elevación y concesión.
- Los datos de MFA **no viajan en el JWT congelado**.
- La concesión **no puede leer información del negocio**.
- El operador **no recibe acceso implícito** a ningún tenant.

---

## 12. Esquema

### V37 — MFA, elevación y recuperación ordinaria · **aprobado**

```
factor_autenticacion
    id, id_credencial → credencial_usuario, organizacion_id
    tipo            'TOTP'                    (CHECK, deja sitio a WebAuthn)
    secreto_cifrado BYTEA · nonce BYTEA · version_clave SMALLINT
    algoritmo, digitos, periodo               explícitos
    estado          'PENDIENTE'|'ACTIVO'|'REVOCADO'
    ultimo_paso     BIGINT                    anti-replay atómico (§3.4)
    creado_en, activado_en, revocado_en, ultimo_uso_en
    UNIQUE parcial: un solo factor ACTIVO por credencial

codigo_respaldo_mfa
    id, id_factor → factor_autenticacion
    identificador VARCHAR(8)   NOT NULL       público, localiza UNA fila
    hash_secreto  VARCHAR(255) NOT NULL       PBKDF2 con sal
    creado_en, usado_en
    UNIQUE (id_factor, identificador)

credencial_usuario
    + debe_enrolar_mfa BOOLEAN NOT NULL DEFAULT FALSE
    backfill: TRUE para toda cuenta con membresía TENANT_ADMIN activa

token_acceso
    + intentos SMALLINT NOT NULL DEFAULT 0
    + estado   VARCHAR(12) NOT NULL DEFAULT 'VIGENTE'
    ck_token_acceso_tipo → admite 'DESAFIO_MFA' y 'ELEVACION'

intento_acceso                                clave_tipo gana 'MFA_CUENTA'

trigger de V34 → SUSTITUIDO por la versión que cuenta
                 administradores OPERATIVOS (§7)

ck_evento_seguridad_tipo → los 10 tipos nuevos de §11
```

### V38 — concesión técnica de recuperación · **APLICADA (2026-08-06)**

> Aprobada e implementada. `concesion_recuperacion` (con las **tres identidades** y sus `CHECK`),
> `aprobacion_recuperacion` (doble aprobación estructural, sin `hash_evidencia`) y
> `accion_recuperacion` (`UNIQUE` por tipo). **No** crea tabla de custodios (D-S0-51). Verificada por
> `e2e-s0-emergencia` (30/30, por el conector local) y `SimulacroRecuperacionIntegrationTest`.
>
> El esquema de abajo se conserva como referencia del diseño; lo aplicado está en
> `V38__concesion_recuperacion.sql`.

```
custodio                  (§9.4)
concesion_recuperacion    (§9.5, §9.8)  -- sin custodio_a/custodio_b
aprobacion_recuperacion   (§9.3)        UNIQUE (id_concesion, id_custodio)
accion_recuperacion       (§9.6)        UNIQUE (id_concesion, tipo)
```

**No depende de `PLATFORM_ADMIN` ni de ninguna cuenta.**

---

## 13. Superficie REST

| Método | Ruta | Roles | Nota |
|---|---|---|---|
| POST | `/auth/mfa/desafio` | PUBLICO | Quien lo usa aún no tiene sesión |
| POST | `/auth/mfa/verificar` | PUBLICO | El desafío es su única credencial |
| GET | `/perfil/mfa` | TODOS | Estado y códigos restantes. **Nunca** el secreto |
| POST | `/perfil/mfa` | TODOS | Inicia enrolamiento |
| POST | `/perfil/mfa/confirmar` | TODOS | Activa y devuelve códigos una vez |
| DELETE | `/perfil/mfa` | TODOS | Propio; contraseña + TOTP vigente |
| POST | `/perfil/mfa/codigos` | TODOS | Regenera; contraseña + TOTP vigente |
| POST | `/perfil/elevacion` | TODOS | Token de elevación de 5 min (§8) |
| DELETE | `/accesos/{idPersona}/mfa` | TENANT_ADMIN | Nivel 2. Exige elevación. Otro tenant → 404 |

Los de `/perfil/*` son **TODOS** sin gate de rol porque su alcance es implícito y no discutible:
salen de la sesión, así que **solo hablan de quien pregunta** — el criterio de `POST
/perfil/contrasena` y `GET /sesion`.

**Las acciones de la concesión no son endpoints del producto**: viven en el conector de gestión
local (§9.2) y no producen sesión.

**Un solo punto de escritura**, como `Transiciones` y `EventosSeguridad`: `MfaServiceImpl` es el
único que toca `factor_autenticacion`. Activar son cuatro efectos inseparables —`ACTIVO`, códigos,
apagar `debe_enrolar_mfa`, invalidar sesiones— en una transacción.

---

## 14. Frontend

**Construido (2026-08-06):**

- **Enrolamiento** (`/enrolar-mfa`) con QR, verificación del primer código y entrega de los 8
  códigos de respaldo, con **confirmación explícita de que se guardaron** antes de continuar
  (D-S0-46).
- **Segundo paso del login** cuando `/auth/mfa/desafio` responde 202.
- **Sesión capada `ENROLAMIENTO_MFA_REQUERIDO`**: **fuera del shell**, como el Bloque 4.
- **Perfil**: estado del factor propio y entrada al enrolamiento voluntario.

- **Perfil → Seguridad → MFA**: estado, códigos restantes, **regenerar códigos** y **reemplazar
  autenticador**, las dos con reautenticación reforzada en el mismo cuadro (contraseña + código
  vigente). El secreto vigente y el QR anterior **no se vuelven a mostrar nunca**.
- **Gobierno de accesos** (`/seguridad`, solo `TENANT_ADMIN`): padrón de cuentas con el estado de su
  factor, **revocación de nivel 2** con motivo obligatorio, elevación y confirmación explícita, y el
  **aviso persistente** de §11 —que no se puede atender ni descartar (D-S0-49)—.

**Pendiente:**

- **La revocación no vive en `agente-detail`/`broker-detail`** sino en `/seguridad`: esas fichas son
  el expediente **comercial** y se identifican por `persona_rol.id`, que no es lo que piden las
  operaciones de acceso (D-S0-50). Desde ellas se enlaza.
- **Nada de recuperación de emergencia en el SPA.**

---

## 15. Decisiones — estado

| # | Decisión | Estado |
|---|---|---|
| **D-S0-22** | Desafío en dos llamadas, vinculado a credencial, organización, emisión, expiración, intentos, estado y contexto | ✅ **Aprobada** |
| **D-S0-23** | Reutilizar `token_acceso` con `DESAFIO_MFA` (y `ELEVACION`) | ✅ **Aprobada con condición** (§4.2) |
| **D-S0-24** | Códigos: identificador + **≥80 bits** + **hash lento con sal**, una sola fila, un solo uso, **8 códigos** | ✅ **Aprobada** *(reemplazó la versión de 50 bits)* |
| **D-S0-25** | `debe_enrolar_mfa` + sesión capada a cinco endpoints | ✅ **Aprobada** |
| **D-S0-26** | La emergencia **repone gobierno y no gobierna**: concesión técnica, no cuenta | ✅ **Aprobada** |
| **D-S0-27** | Ventana de **30 minutos**, prorrogable una vez con aprobación dual nueva | ✅ **Aprobada** *(reemplazó las 4 h)* |
| **D-S0-28** | *(MFA de la cuenta break-glass)* | ❌ **Eliminada** — no existe tal cuenta |
| **D-S0-29** | *(Emitir `PLATFORM_ADMIN`)* | ❌ **Rechazada** |
| **D-S0-30** | Partir en **V37** y **V38** | ✅ **Aprobada** |
| **D-S0-31** | **Anti-replay atómico** por actualización condicional | ✅ **Aprobada** — control estructural, no mejora |
| **D-S0-32** | **Conteo acumulado por cuenta** + desafío + IP, espera progresiva, sin bloqueo indefinido | ✅ **Aprobada** |
| **D-S0-33** | **Versionado, rotación, respaldo separado y prueba de restauración** de `MFA_CLAVE_CIFRADO` | ✅ **Aprobada** |
| **D-S0-34** | **Reautenticación reforzada** con token de elevación de 5 min (§8) | ✅ **Aprobada** |
| **D-S0-35** | **«Registrado» no es «notificado»**: canal externo como requisito de salida **y condición de arranque de V38** | ✅ **Aprobada** |
| **D-S0-36** | *(nueva)* **Solo paso actual y anterior**; se retira el paso futuro (§3.2) | ✅ **Aprobada** |
| **D-S0-37** | *(nueva)* **Invariante de `TENANT_ADMIN` operativo** (§7), aplicado a suspensión, baja, revocación de MFA, cambio de rol y recuperación; el trigger de V34 se sustituye | ✅ **Aprobada** |
| **D-S0-38** | *(nueva)* **Doble aprobación como dos registros** con `UNIQUE (id_concesion, id_custodio)`, no un `CHECK` de textos (§9.3) | 📋 **Parte de V38** |
| **D-S0-39** | *(nueva)* **Ciclo de vida de custodios**: tabla, secretos de ≥128 bits con hash lento, rotación anual y tras cada uso, bloqueo progresivo, procedimiento de reemplazo (§9.4) | 📋 **Parte de V38** |
| **D-S0-40** | *(nueva)* **Secreto de concesión de 256 bits** Base64URL, comparación en tiempo constante, nunca en URL ni logs (§9.5) | 📋 **Parte de V38** |
| **D-S0-41** | *(nueva)* **Una ejecución por tipo de acción** (`UNIQUE`), idempotencia y **cierre automático** al recuperar gobierno (§9.6) | 📋 **Parte de V38** |
| **D-S0-42** | *(nueva)* **Consumo atómico** de la capacidad de la concesión (§9.7) | 📋 **Parte de V38** |
| **D-S0-43** | *(nueva)* **Herramienta operativa concreta** y conector de gestión local; nunca SQL libre ni endpoint expuesto (§9.2) | 📋 **Parte de V38** |
| **D-S0-44** | *(2026-08-06)* **Confirmar el enrolamiento CONSUME su paso.** El código con el que se activa el factor queda sellado en `ultimo_paso` y no sirve para autenticar. Cierra un hueco real de V37: `consumirPaso` solo mira factores `ACTIVO`, así que el primer código —el más expuesto, el que acaba de estar en pantalla— seguía valiendo hasta 30 s. **La espera del paso siguiente es la consecuencia y se acepta**; no se rebaja admitiendo dos veces el mismo paso | ✅ **Aprobada** |
| **D-S0-45** | *(2026-08-06)* **Los errores de MFA llevan `codigo` estable** además del `error` visible: `MFA_CODIGO_INVALIDO`, `MFA_CODIGO_REUTILIZADO`, `MFA_DESAFIO_INVALIDO`, `MFA_DESAFIO_VENCIDO`, `MFA_DESAFIO_CONSUMIDO`, `MFA_LIMITE_INTENTOS`, `MFA_ENROLAMIENTO_INVALIDO`. El cliente decide por el código, **nunca** por la cadena en español. El mensaje **no** se especializa donde hacerlo delataría algo: desafío inexistente, reemplazado o cuenta sin factor comparten texto y código | ✅ **Aprobada** |
| **D-S0-46** | *(2026-08-06)* **Después de enrolar se sale.** Códigos de respaldo → confirmación explícita de que se guardaron → cierre de sesión → login con contraseña y un TOTP **nuevo**. **Sin renovación transparente**: la sesión que enroló nació sin segundo factor y renovarla por detrás vaciaría de contenido la invalidación | ✅ **Aprobada** |
| **D-S0-47** | *(2026-08-06)* **Recargar la pantalla de enrolamiento inicia otro.** El secreto **no se persiste en el navegador** —sería una copia permanente del factor justo donde el factor protege—, así que el anterior queda inservible y **se avisa en pantalla** para que el usuario borre la entrada vieja de su aplicación | ✅ **Aprobada** |
| **D-S0-51** | *(2026-08-06)* **Los custodios son configuración, no un subsistema.** Se verifican contra `RECUPERACION_CUSTODIO_{A,B}_{ID,HASH}` fuera de PostgreSQL; la base guarda **solo los identificadores que participaron en cada concesión**. Se retira la tabla `custodio` con su ciclo de vida: era administración de identidades para gobernar dos secretos que cambian una vez al año, y la vigencia de un custodio es un acta firmada, no estado de aplicación. Reemplazo **de uno en uno** manteniendo válido el otro; una prueba fallida **revierte la configuración** en vez de dejar una sola custodia. La rotación anual sigue siendo política operativa | ✅ **Aprobada** |
| **D-S0-52** | *(2026-08-06)* **Tres identidades, conservadas en la fila.** La concesión guarda custodio A, custodio B **y operador**, con `CHECK` de que los tres son distintos. Sin la identidad del operador en la base, «quien ejecuta no custodia» es inaplicable: un log de texto se reescribe, una fila no. Obliga a que los custodios lleven **identificador** además de hash — si no, las desigualdades comparan etiquetas de ranura y pasan siempre | ✅ **Aprobada** |
| **D-S0-53** | *(2026-08-06)* **Dos puertas distintas para V38.** *Implementación y pruebas*: pueden empezar ya, con secretos **generados durante la corrida E2E** y destruidos con su base efímera. *Activación operativa o producción*: exige dos custodios reales designados, secretos entregados por separado, operador definido y prueba de custodia satisfactoria. Confundirlas paraliza trabajo técnico verificable sin que los fixtures se conviertan por ello en custodios reales | ✅ **Aprobada** |
| **D-S0-49** | *(2026-08-06)* **El aviso persistente de §11 se LEE de `evento_seguridad`, no se escribe en `alerta`.** Dos razones y la segunda es la que manda: (1) `alerta.id_rol_agente` es `NOT NULL` y su `CHECK` de tipos es la lista congelada de hechos comerciales, así que un aviso de gobierno no cabe sin tocar una tabla del contrato congelado; (2) **una alerta de la campana se puede atender**, y quien más interés tiene en hacer desaparecer un «se revocó el factor de X» es quien lo revocó sin permiso. `evento_seguridad` es append-only y de un solo escritor: el aviso **no se puede silenciar**. Se expone en `GET /seguridad/avisos`, solo `TENANT_ADMIN`, filtrado a los hechos de gobierno | ✅ **Aprobada** |
| **D-S0-50** | *(2026-08-06)* **`GET /accesos` publica la correspondencia persona↔rol.** Las fichas comerciales congeladas identifican por `persona_rol.id` y todas las operaciones de acceso hablan de la PERSONA; sin ese puente el SPA no puede ofrecer ninguna acción de gobierno sobre alguien que ve en una ficha. La alternativa era añadir `idPersona` a `AgenteResponse` y `BrokerResponse`, que **están congelados**. Un endpoint aditivo no rompe nada y además responde lo que hay que saber antes de tocar un acceso: si la cuenta está activa, si tiene factor y **cuántos** códigos le quedan — nunca los códigos ni el secreto | ✅ **Aprobada** |
| **D-S0-48** | *(2026-08-06)* **El QR se genera en el SPA** a partir de la `uri` que emite el backend, con una dependencia local sin red (`qrcode`): un servicio externo de QR filtraría el secreto a un tercero. La **clave manual** se muestra siempre, no escondida tras un "¿problemas?" | ✅ **Aprobada** |

### Fuera de alcance, dicho explícitamente

- **`PLATFORM_ADMIN` y la administración de plataforma** — no se adelantan.
- **`concesion_acceso_tenant`** (D-S0-16) — es otra cosa.
- **WebAuthn** y **aprobaciones firmadas / llaves físicas** para custodios — evolución posterior.
- **Sesiones de 2 h y `sudo mode`** — post-corte (S0.7).
- **MFA obligatorio para BROKER y AGENTE** — posible, no exigido.

---

## 16. Orden de implementación

1. ✅ **Documento corregido**.
2. ✅ **V37** — esquema, MFA, elevación e invariante operativo *(2026-08-06)*.
3. ✅ **Verificado**: `e2e-s0-mfa.ps1` **60/60** y 23 tests unitarios nuevos.
4. ✅ **Nivel 2** — revocación por otro `TENANT_ADMIN`, con el corte del último operativo.
5. ⬜ **Documentar** custodios y procedimiento en `operacion/`.
6. ⬜ **V38** — solo cuando se cierre §17.
7. ⬜ **Simulacro completo** (§18.16).

> **Lo que falta antes de poder usar el SPA.** El §13 (frontend) **no está construido**, y V37 marca
> `debe_enrolar_mfa` a los administradores: su sesión queda **capada** y todavía no existe la
> pantalla de enrolamiento. Hasta que exista, **el administrador solo puede enrolar por API**. No es
> un defecto de V37 —la capa es precisamente lo que evita que MFA obligatorio deje a nadie fuera—,
> pero conviene no descubrirlo abriendo el navegador.

> **Al implementar apareció un segundo sitio con el problema del §3.4, y no era el TOTP.** El
> contador de intentos **del desafío** se escribía en la misma transacción que después lanzaba el
> error, así que el `rollback` lo borraba: el límite de cinco intentos no contaba nada y bastaba
> insistir sobre el mismo desafío. Se movió a transacción propia (`REQUIRES_NEW`), que es lo que ya
> hacían `EventosSeguridad` y `BloqueoAccesos` **por esta misma razón**. Regla que conviene
> generalizar: *lo que cuenta fallos no puede viajar en la transacción que falla*.

---

## 17. V38 — requisitos, y cuáles quedan

> **Estado (2026-08-06): V38 implementada y verificada.** Los requisitos 1-8 están construidos y
> cubiertos por `e2e-s0-emergencia` (30/30) y `SimulacroRecuperacionIntegrationTest`. **El 9 sigue
> pendiente y es de ACTIVACIÓN, no de implementación** (D-S0-53), junto con la designación real de
> los dos custodios: sin ellos, `prod` no arranca con la bandera encendida.

| # | Requisito | Dónde queda |
|---|---|---|
| 1 | **Herramienta operativa concreta** y conector de gestión local | §9.2 — *diseñado, falta escribirlo y probarlo* |
| 2 | **Dos aprobaciones como registros independientes** | §9.3 — diseñado |
| 3 | **Ciclo de vida de custodios** (alta, rotación, baja, prueba, bloqueo) | §9.4 diseñado + **procedimiento escrito** en [`operacion/custodios-y-recuperacion-de-emergencia.md`](../../backend-spring/operacion/custodios-y-recuperacion-de-emergencia.md) §3. **Falta la designación real**: el acta está sin rellenar a propósito — nombrar custodios es decisión de la organización |
| 4 | **Secretos de custodio robustos y rotables** | §9.4 diseñado + **reglas escritas** (`operacion/…` §3.4): origen, entropía, custodia, rotación anual **y tras cada uso**. Falta el generador y el almacén |
| 5 | **Secreto de concesión de 256 bits** | §9.5 — diseñado |
| 6 | **Una ejecución por tipo de acción**, idempotente | §9.6 — diseñado |
| 7 | **Consumo atómico** | §9.7 — diseñado |
| 8 | **Cierre automático** al recuperar un administrador operativo | §9.6 — diseñado |
| 9 | **Canal externo real** como condición de habilitación productiva | Abajo |

**El canal externo es una condición técnica, no documental.** Una recuperación de emergencia sin
aviso externo puede usarse **precisamente cuando nadie está dentro de BROX** para ver la campana.
Por eso:

- **V37** puede operar con alerta interna durante el desarrollo.
- **V38** puede desarrollarse y probarse en `dev`.
- **V38 no se habilita en producción sin canal externo**: bandera de funcionalidad
  (`RECUPERACION_EMERGENCIA_HABILITADA`) que en perfil `prod` **exige** un notificador externo
  configurado, y `ValidadorConfiguracionSeguridad` **detiene el arranque** si está encendida sin él.

---

## 18. Verificación prevista — `verificacion/e2e-s0-mfa.ps1`

1. Enrolar sin confirmar **no** activa; el factor `PENDIENTE` caduca a los 15 min.
2. Confirmar con código inválido **no** activa y consume intento.
3. Activado: `/auth/login` responde **401** y `/auth/mfa/desafio` responde **202**.
3bis. **El código del enrolamiento no se reutiliza (D-S0-44)**: confirmar con un TOTP, intentar
   `/auth/mfa/verificar` con **ese mismo** código dentro de su ventana → **rechazado**, y con
   `codigo = MFA_CODIGO_REUTILIZADO`, no `MFA_CODIGO_INVALIDO`; con el **paso siguiente** entra. En
   la base, `ultimo_paso` queda sellado en el paso de la confirmación.
4. **Replay atómico**: dos peticiones **simultáneas** con el mismo código → **una entra, la otra
   falla**. Es lo que una comparación no atómica deja pasar.
5. **Paso futuro rechazado**: el código del paso `t+1` **no** vale; el de `t` y `t-1` sí; el de `t-2`
   no. Y usar `t` **no** impide usar `t+1` en su momento.
6. Al sexto intento el desafío muere.
7. **El contador por cuenta NO se reinicia** con desafíos nuevos: 10 fallos repartidos entre tres
   desafíos **activan la espera de 5 minutos**.
8. Un login MFA correcto **reduce** los contadores sin borrarlos de golpe.
9. Código de respaldo: entra, **no** desactiva el MFA, **no** vale dos veces, y dice cuántos quedan.
   Reemplazar el factor desde esa sesión **exige la contraseña otra vez**.
10. El identificador localiza **una sola fila**: un identificador inventado falla **sin derivar
    hash**.
11. Regenerar invalida los 8 anteriores.
12. Activar el factor **invalida las sesiones vivas**; regenerar códigos **no**.
13. **Elevación**: revocar un factor ajeno **sin** token de elevación → 403; con uno **caducado** →
    403; con uno válido → 200, y el token **no vale dos veces**. Una sesión nacida con MFA hace una
    hora **no** cuenta como MFA reciente.
14. **Reglas de revocación por rol**: un `BROKER` no revoca el MFA de su agente (**403**); el
    `TENANT_ADMIN` sí; contra sí mismo **rechazado**; otro tenant **404**.
15. **Invariante operativo**: con dos administradores donde uno tiene `debe_enrolar_mfa`, revocar el
    factor del otro **se rechaza** —el primero no es operativo—; el mensaje nombra la causa. Bajar su
    membresía y suspender su cuenta **también** se rechazan.
16. **Simulacro completo de emergencia** *(cuando V38 esté aprobado)*: el **último** administrador
    pierde el teléfono y no tiene códigos → dos custodios aprueban por separado → una sola
    aprobación **no** habilita la concesión → se aplica la revocación → el administrador **vuelve a
    enrolar personalmente** → la concesión **se cierra sola** al haber otra vez un administrador
    operativo → se verifica que **nunca** leyó un dato comercial, creó una persona ni fijó una
    contraseña, y que **no dejó cuenta ni rol nuevos**.
17. **Idempotencia y unicidad de acciones**: repetir `REVOCAR_MFA` en la misma concesión **no**
    consume capacidad ni crea una segunda fila.
18. **Caducidad sin barrido**: con el `@Scheduled` detenido, una concesión vencida **no** aplica.
19. **Rotación de clave**: un factor cifrado con `version_clave = 1` sigue validando tras rotar a la
    2, y la prueba de restauración descifra un factor conocido **sin** exponer la clave en el log.
20. **Arranque**: en `prod`, `RECUPERACION_EMERGENCIA_HABILITADA` sin notificador externo **detiene
    el arranque** con un mensaje que nombra la variable.
21. Higiene: **ningún** evento, log ni respuesta lleva secreto, código, desafío, elevación o hash.
