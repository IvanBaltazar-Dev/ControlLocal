# Decisión — la oferta, la negociación y el cierre de una venta (D-N-1 … D-N-5)

**Fecha: 2026-09-05.** Corte **WP-A**. Migración **`V90__la_negociacion_y_el_cierre_de_venta.sql`**.

> **La letra es `N`, de negociación, y no `E3`.** Este documento nació numerado `D-E3-1…4` y esos
> códigos **ya estaban ocupados**: `D-E3-1` es la taxonomía del menú
> (`decision-sidebar-brox.md`), citada por `acceso.ts`, `acceso.spec.ts`, `shell.*` y por otras dos
> decisiones. Dos reglas distintas con el mismo nombre convierten cualquier cita en ambigua —y una
> cita ambigua a una regla de autoridad es peor que ninguna—, así que se renumeró **antes** de que
> nada dependiera de ella. Correspondencia, por si aparece una cita vieja:
> `D-E3-1→D-N-1`, `D-E3-2→D-N-2`, `D-E3-3→D-N-3`, `D-E3-4→D-N-5`; **`D-N-4` es nueva** y nombra la
> caída con motivo obligatorio (§5), que hasta ahora se aplicaba sin código propio.

| Código | Qué decide | Dónde |
|---|---|---|
| **D-N-1** | Contra qué precio se ofreció: referencia congelada, y FALTANTE si no hay `P` ni `U` | §2 |
| **D-N-2** | Dónde vive una oferta —la mesa— y qué escribe en la serie económica | §1 |
| **D-N-3** | El cierre de una venta y sus dos cifras (pactada y declarada) | §3 |
| **D-N-4** | La caída de la oportunidad exige motivo de vocabulario cerrado | §5 |
| **D-N-5** | El hito `C` de un alquiler también dice de qué vuelta salió | §4 |

Hasta hoy BROX sabía captar, publicar, visitar y alquilar. De la **negociación** no guardaba nada:
una propiedad pasaba de «publicada a 180.000» a «vendida» sin una sola fila que dijera a cuánto se
ofreció, cuántas vueltas hubo ni por qué se cayó. La serie económica tenía los hitos `O` (ofertado)
y `A` (aceptado) en su `CHECK` **desde V4 y nadie los escribía**: eran vocabulario sin productor.

Y el cierre de una **venta** no existía en absoluto. El único cierre que el sistema sabía registrar
era el de un alquiler (`ContratoAlquiler`), así que un encargo de venta sólo podía terminar por la
puerta manual del bróker —«cierre de captación»— sin importe, sin comprador y sin fecha de firma.

---

## 1 · La mesa: dónde vive una oferta (D-N-2)

Una oferta cuelga de la **OPORTUNIDAD** —con quién se negocia— y del **ENCARGO** —sobre qué—.

**No cuelga sólo del encargo, y la diferencia no es cosmética.** Dos interesados por el mismo
inmueble son dos oportunidades. Si las ofertas colgaran únicamente del encargo, las dos cadenas
quedarían mezcladas en una sola línea y **ninguna lectura podría volver a separarlas**: no se sabría
quién respondió a quién, ni cuál de los dos subió.

`oferta.id_captacion` se guarda **además** de poder deducirse por la oportunidad, y no es
redundancia gratuita: es lo que permite leer la serie económica de un encargo sin pasar por la mesa.
Un trigger (`tg_oferta_encargo_de_la_mesa`) exige que las dos coincidan, porque sin él una oferta
podía declarar el encargo de otra propiedad y contaminar su serie con cifras ajenas.

### La máquina, y por qué son cuatro desenlaces y no dos

```
PRESENTADA ─┬─→ ACEPTADA    el sí
            ├─→ RECHAZADA   el no a esa cifra
            ├─→ SUPERADA    la respondió una contraoferta
            └─→ RETIRADA    quien la presentó se echó atrás
```

`RECHAZADA` **no es** `SUPERADA`: a una la responde un no, a la otra una cifra nueva. `RETIRADA`
**no es** `RECHAZADA`: en una cede quien ofreció, en la otra quien escuchaba. Fundirlas ahorraría
dos códigos y borraría de la historia **quién cedió**, que es justamente lo que un histórico de
negociación existe para conservar.

`presentada_por` (`I` interesado / `P` propietario) es **obligatorio y no se deduce de la ronda**:
una negociación puede empezar por cualquiera de los dos lados, y deducirlo sería inventar quién
habló primero. En una **contraoferta** el lado **no se declara**: lo dicta la anterior y responde el
otro. Dejárselo al cliente permitiría una cadena en la que el interesado se contraoferta a sí mismo,
que no es una negociación sino dos ofertas suyas seguidas.

### Los dos invariantes que vive la base, no el servicio

| Índice | Qué impide |
|---|---|
| `uq_oferta_viva_por_oportunidad` | Dos ofertas `PRESENTADA` a la vez en la misma mesa |
| `uq_oferta_aceptada_por_oportunidad` | Dos `ACEPTADA`: una mesa tiene **un** precio de cierre |

Están en la base **y no sólo en la comprobación previa** porque dos peticiones simultáneas pasan las
dos esa comprobación: sólo el índice corta la carrera. El servicio traduce la violación a **409** con
un mensaje que se puede leer.

### Cada vuelta escribe en la serie económica

Toda oferta y toda contraoferta escriben un hito **`O`** en `precio_propiedad` con `id_captacion`
**y** `id_oferta` (columna nueva, nullable). `id_oferta` es `NULL` en `U` y `P` y no es un hueco:
esos hitos **no nacen de ninguna mesa** —salen del encargo y de su anuncio—, así que la respuesta
correcta para ellos es «ninguna». Y `NULL` en todo lo anterior a V90.

### Y se cierra la segunda puerta al mismo hito

`POST /locales/{id}/precios` es la puerta **manual** de la serie, y aceptaba **los siete** códigos
del `CHECK` porque dos de ellos —`O` y `C`— **no los producía nadie**. Eso deja de ser inofensivo
hoy: desde que `O` significa «una vuelta de esta negociación» y `C` «lo que dice este contrato»,
teclearlos sería una **segunda manera de escribir el mismo hecho**, y la segunda siempre se olvida
de algo —un `O` a mano no tiene mesa, no tiene ronda y llega con `id_oferta` nulo, indistinguible
de una fila anterior a V90; un `C` a mano afirma un cierre del que no existe documento—.

Los dos responden ahora **400** con un mensaje que **nombra la puerta correcta** en vez de limitarse
a negar (`PrecioPropiedad.HITOS_DE_PRODUCTOR`). **`P` se conserva** aunque también lo produzca la
publicación: un anuncio fuera de BROX es un hecho real que alguien tiene que poder registrar, y ahí
la mano no compite con ningún productor. `A` (aceptado) sigue en el `CHECK` **sin productor** y esta
decisión no se lo inventa: la aceptación vive en `oferta.estado = 'A'`, que es donde se puede leer
con su fecha, su actor y su ronda.

---

## 2 · La referencia: contra qué precio se ofreció (D-N-1)

Una oferta sin referencia es un número suelto. La referencia es:

1. el **último hito `P` vigente del encargo** —lo que el mercado veía—;
2. si el encargo nunca se anunció, el **último `U`** —lo que el propietario autorizó—;
3. si no hay ninguno de los dos: **FALTANTE, y se dice.**

Se guarda **cuál se usó** (`hito_referencia`), su fila (`id_precio_referencia`) y su importe y moneda
(`monto_referencia`, `moneda_referencia`). Los cuatro campos van juntos o no va ninguno
(`ck_oferta_referencia_completa`): un monto sin su moneda, o una moneda sin el hito del que salió, es
media referencia y no se puede interpretar.

**Se congela al presentar y no se recalcula nunca.** Un anuncio que baja de precio una semana después
no puede cambiar retroactivamente el descuento con el que se negoció.

**La referencia FALTANTE no se rellena.** Ni con el importe del encargo, ni con el de la propia
oferta. Un descuento calculado contra una cifra inventada es peor que no tener descuento: el primero
se cita en una negociación, el segundo se pregunta. En el cable el objeto `referencia` **no viaja**
(Jackson `NON_NULL`), y esa ausencia es la información.

**Por encargo y no por propiedad.** Un inmueble en venta *y* en alquiler tiene dos series; preguntar
por la propiedad devolvería la renta mensual como referencia de una oferta de compra.

---

## 3 · El cierre de una venta (D-N-3)

`POST /captaciones/{id}/cierre-venta`, con la **oferta aceptada**. En una transacción:

1. nace `contrato_compraventa`;
2. se escribe el hito **`C`** con `id_captacion` **y** `id_oferta`;
3. la oportunidad pasa a **FINALIZADA EXITOSA**;
4. el encargo se cierra con `motivo_cierre = 'V'` —vendido—, que no es el `'A'` del alquiler ni el
   `'M'` del cierre manual: los tres son cierres y ninguno es el mismo hecho;
5. el inmueble sale del mercado y se dan de baja **sólo los anuncios de ese encargo**;
6. se resuelven las tareas abiertas de la operación y se emite el aviso.

### Tabla propia, y no un `ContratoAlquiler` con un tipo

`contrato_alquiler` no es genérico: tiene `renta_contractual`, `fecha_fin_contrato` e
`id_contrato_anterior` (la renovación), y de él cuelga `comision_liquidacion` por clave ajena. Una
compraventa **no se renueva, no tiene plazo y no tiene renta**. Meterla ahí obligaría a dejar esas
columnas en NULL, a debilitar sus `CHECK` y a que cada consulta del ciclo de alquiler preguntara «¿y
si es una venta?».

### Las dos cifras

```
monto_pactado    lo que de verdad se acordó. Por defecto el de la oferta aceptada.
monto_declarado  lo que consta en la escritura, SÓLO si difiere. OPCIONAL.
```

`monto_declarado` **nace FALTANTE y jamás se rellena igualándolo al pactado**: eso convertiría «nadie
ha leído la escritura» en «la escritura dice lo mismo», que es una afirmación registral que el
sistema no puede hacer. Si viene, **exige su propia moneda**: aquí se pacta en dólares y se escritura
en soles con frecuencia, y suponer que coinciden convertiría 700.000 soles en 700.000 dólares.

### Sin columna `estado`, a propósito

Una compraventa no tiene ciclo. Los hitos registrales —minuta, escritura, bloqueo, partida— ya viven
en `condicion_compraventa` (V51). Una columna de estado sin operación que la produzca sería
vocabulario huérfano, que es lo que el catálogo de productores existe para impedir.

### Lo que el cierre de venta NO hace

- **No cierra las demás oportunidades vivas del encargo.** Cada una se cae con **su** motivo y su
  fecha; cerrarlas en cascada inventaría once razones de caída que nadie declaró.
- **No transfiere la titularidad.** El comprador no pasa a ser `propiedad.id_rol_propietario`.
  **Decisión pendiente**, y está escrita como tal en el §7.
- **No liquida comisión.** `comision_liquidacion` cuelga de `contrato_alquiler` por FK. La comisión
  de una venta es un corte aparte.

### `RETIRADO` y no `VENDIDO`, y por qué se elige la verdad incompleta

`DisponibilidadComercial` tiene `DISPONIBLE`, `RESERVADO`, `ALQUILADO` y `RETIRADO`. **No tiene
`VENDIDO`**, y añadirlo cruza dos autoridades que este corte no gobierna: la matriz de códigos de
estado y el catálogo de productores. De las opciones reales:

- `DISPONIBLE` sería **falso**: es una afirmación sobre el presente que la venta desmiente;
- `ALQUILADO` sería falso y además contaminaría la estadística de rentas;
- `RETIRADO` —«retirado del mercado»— es **cierto y más débil**: no dice que se vendió, y no lo
  pretende.

Se elige la afirmación verdadera aunque sea incompleta, nunca la completa inventada. El *porqué* sí
queda escrito: en `contrato_compraventa`, en el hito `C` y en `captacion.motivo_cierre = 'V'`.
**Que `VENDIDO` entre en el vocabulario es una decisión pendiente** (§7).

---

## 4 · El hito `C` del alquiler también dice de qué vuelta salió (D-N-5)

**Decisión nueva de este corte.** Un alquiler también se negocia. Si la mesa llegó a tener una oferta
`ACEPTADA`, `ContratoServiceImpl.cerrarLocal` ata su hito `C` a esa oferta, igual que hace el cierre
de venta.

Sin esto la serie de una **venta** se podría leer por negociación y la de un **alquiler** no: dos
verdades distintas para el mismo dato, y la asimetría la habría introducido este corte. `NULL`
significa «no salió de ninguna oferta registrada» —hoy el caso normal, porque el alquiler se cierra
por solicitud aprobada y negociar es opcional—, **no** «se desconoce».

El cierre de un alquiler **sigue siendo** solicitud → evaluación → contrato. Esta decisión no abre
una segunda puerta: sólo hace que la fila que ya se escribía diga una cosa más.

---

## 5 · La caída: motivo obligatorio y de vocabulario cerrado (D-N-4)

**Una oportunidad no se cae «sin más».** El motivo es **obligatorio** y sale de un **vocabulario
cerrado**: sin él la petición es **400** («El motivo de no continuidad es obligatorio.») y con un
código que no está en el vocabulario también. Es la regla que hace que la pregunta *por qué se
pierden las ventas* tenga respuesta: un texto libre no se agrupa, y un motivo opcional se deja en
blanco justo en los casos que más interesan.

Las **dos puertas** a la caída —`POST /oportunidades/{id}/no-continuidad` y el cierre de una visita
con resultado negativo (`VisitaServiceImpl`)— validan contra el **mismo** vocabulario y escriben la
**misma** fila de `motivo_no_continuidad`. No hay una tercera.

`motivo_no_continuidad` nació con el vocabulario de un alquiler: precio, ubicación, condiciones del
contrato, local no adecuado, cliente no responde, encontró otra opción, otro. Las razones por las que
de verdad se cae una compraventa no estaban.

| Código | Razón | Por qué no es sinónimo de ninguna anterior |
|---|---|---|
| `F` | No consiguió financiamiento | No es «precio»: el precio estaba acordado |
| `T` | El propietario retiró el inmueble | La caída no viene del lado del interesado |
| `D` | No se resolvió la documentación | Cargas, saneamiento, partida. No es «condiciones del contrato» |
| `S` | El interesado desistió | **Distinto de `N` «no responde»**: uno decidió que no, del otro no se sabe nada |

Sin ellas el agente estaba obligado a marcar `O` (otro) y el análisis de por qué se pierden las
ventas no se podía hacer.

### El vocabulario vivía escrito seis veces, y ahora una

Hasta V90 la lista `P U C L N E O` y sus rótulos estaban copiados en `OportunidadServiceImpl`,
`VisitaServiceImpl`, `ReportePropietarioServiceImpl`, `Descripciones`, `Vocabulario` y el SPA. **No
divergían por descuido: divergían en cuanto alguien añadiera un código**, que es exactamente lo que
hizo falta aquí. Una razón nueva en el `CHECK` y en cuatro de los cinco mapas se habría leído como
`null` en el informe al propietario y como la letra cruda en la pantalla.

Ahora el vocabulario y sus rótulos viven **sólo** en `MotivoNoContinuidad` —la entidad que guarda la
razón— y todos los demás lo toman de ahí.

### La caída es un acto aparte, y rechazar no la produce

**Rechazar la última oferta NO cierra la oportunidad.** Que el propietario diga que no a una cifra no
significa que el interesado se haya ido, y convertir un rechazo en una caída borraría de la historia
las negociaciones que siguieron después de un no.

La caída sigue entrando por `POST /oportunidades/{id}/no-continuidad`, que **ya existía**. No se
añade un `/caida` como sinónimo: dos rutas para el mismo hecho son dos productores del mismo estado y
la segunda se olvida de algo. Lo que cambia es que el vocabulario que acepta ahora incluye las cuatro
razones de una venta.

---

## 6 · Autoridad: quién puede negociar y quién puede cerrar

| Acto | Quién | Guarda |
|---|---|---|
| **Leer** la mesa | Agente lo suyo · Bróker por **captación** supervisada · TENANT_ADMIN su tenant | `AccesoOportunidad.conAcceso` |
| **Escribir** en la mesa | El **agente de ESE encargo** | `AutoridadDePropiedad.exigirEdicionDelEncargo` |
| **Cerrar** la venta | El **agente de ESE encargo** | `CierreVentaServiceImpl.cargarConAcceso` + `exigirEdicionDelEncargo` |

**Escribir una oferta es operar el encargo, no editar la propiedad**: cada vuelta escribe la serie
económica del encargo. Por eso la autoridad es la del encargo —quien negoció es quien negocia— y
**ser responsable de la propiedad no basta**. **TENANT_ADMIN no opera**: `exigirEdicionDelEncargo`
exige `actor.esAgente()`, y gobernar no es operar.

El cierre lo firma el **AGENTE** y no el bróker, con la misma banda que `POST /contratos` y por la
misma razón: **el bróker decide, el agente registra**, y un cierre es el registro de un hecho
consumado.

### Las capacidades salen del mismo predicado que después deniega

`GET /oportunidades/{id}` publica `capacidades {puedeOfertar, puedeContraofertar, puedeAceptar,
puedeRechazar, puedeRetirar, puedeCerrarVenta, puedeRegistrarCaida}`, calculadas **llamando a la
misma guarda** dentro de un `try/catch`, no reescribiendo «soy el agente» en una segunda tabla de
decisión. Una copia de una regla de autoridad diverge siempre hacia el lado que concede de más, y un
botón activo que el backend rechaza cuando la persona ya escribió es el peor error de esta
superficie.

Los **listados no las traen**: su pregunta es «qué hay», no «qué puedo hacer con éste», y resolver la
mesa por fila serían dos consultas por elemento de la página. Con `NON_NULL` el campo no viaja, y su
ausencia significa «no calculado aquí» —**no** «no hay ofertas» ni «no puedes nada».

---

## 7 · Lo que queda pendiente, dicho con nombre

1. **La titularidad post-venta no se transfiere.** Vendido el inmueble, `id_rol_propietario` sigue
   apuntando al vendedor. Transferirla exige decidir qué pasa con el histórico del anterior titular,
   con sus encargos y con lo que el comprador puede ver de ellos. **No se hace por inercia.**
2. **`VENDIDO` no está en `DisponibilidadComercial`.** Mientras tanto el cierre marca `RETIRADO`
   (§3).
3. **La comisión de una venta no se liquida.** `comision_liquidacion` cuelga de `contrato_alquiler`
   por FK.
4. **`PropiedadUniversalServiceImpl` guarda su propia copia privada** de «qué hitos son lo que se
   pidió» (`PEDIDOS`). Desde WP-A la definición canónica es `PrecioPropiedad.HITOS_PEDIDOS`; esa
   copia debe apuntar a ella en el corte que sea dueño de ese fichero.
5. **El SPA no tiene pantalla de negociación.** El Core la publica entera; consumirla es WP-D/E.
6. **Dos ficheros siguen citando los códigos viejos** y no se han tocado porque están fuera de este
   corte: `docs/ai/README.md` (el aviso de que `D-E3-1` nombraba dos decisiones, y la fila de este
   documento) y el comentario del bloque de lista blanca de `.gitignore`. Los dos dicen
   `D-E3-1/2/3/4`; lo correcto hoy es `D-N-1…D-N-5`. **La colisión que el README avisa ya no
   existe**, así que ese aviso es la única frase del repositorio que este corte deja falsa.
7. **`A` (aceptado) sigue en el `CHECK` de `precio_propiedad.hito` sin productor.** No se le inventa
   uno: la aceptación vive en `oferta.estado = 'A'`, con su fecha, su actor y su ronda. Retirarlo del
   vocabulario es una decisión del corte que gobierne `matriz-codigos-estado.md`.
8. **`oferta.estado` declara su vocabulario en la entidad**, como `DocumentoSolicitud`, y no como un
   `enum` de `EstadosDominio`. Lo vigila el catálogo de productores (cinco filas) y `MaquinasEstado`;
   **no** aparece en `matriz-codigos-estado.md`, cuyo gate sólo recorre los `enum`.
