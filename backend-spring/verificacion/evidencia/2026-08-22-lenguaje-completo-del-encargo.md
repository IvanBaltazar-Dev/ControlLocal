# El lenguaje completo del ENCARGO · V77

**Cerrado el 2026-08-22.** Migración `V77__completar_condiciones_del_encargo.sql`.

---

## El hueco

El Corte 0C construyó el segundo sujeto entero —`catalogo_atributo.sujeto`,
`catalogo_atributo_operacion`, `atributo_encargo`, los dos triggers que impiden
el cruce— y sembró **seis** condiciones para probar que funcionaba. Ahí se
quedó. De las veintiséis que el inventario declara faltaban veinte, y **VENTA no
tenía ninguna propia**: un encargo de venta no podía decir si se entrega
desocupado, si el propietario acepta crédito hipotecario ni si aceptaría una
permuta — tres preguntas que deciden si una operación avanza o se cae.

El mecanismo estaba; el idioma no. Y abrir el Corte 1 —profundizar la
PROPIEDAD— con el ENCARGO medio mudo habría empujado a resolver condiciones
comerciales metiendo más campos en `atributo_propiedad`, que es exactamente el
error que 0C vino a hacer imposible.

---

## Lo que hace V77

**Catálogo y pruebas.** Ni una columna nueva, ni un trigger tocado, ni una regla
del mecanismo redefinida.

| | Antes | Ahora |
|---|---|---|
| Condiciones del ENCARGO | 6 | **26** |
| Aplicables a VENTA | 1 (`disponible_desde`) | **7** |
| Aplicables a ALQUILER | 6 | **20** |
| Vocabularios sembrados | 0 | **22 opciones** en 5 listas |
| Filas de aplicabilidad (tipo × operación) | 40 | **112** |

### La pregunta que decidió cada una

> ¿Describe **cómo ES** el inmueble, o **cómo se acordó comercializarlo** en
> este encargo? Si al abrir otro encargo sobre la misma propiedad el dato puede
> cambiar sin que la propiedad haya cambiado, es del ENCARGO.

Las veintiséis la pasan. Lo que la revisión sí cambió, y se dice porque el
documento proponía otra cosa:

- **`igv_arrendamiento` pierde la opción `POR_DEFINIR`.** La ausencia del valor
  ya significa «todavía no se sabe»; con la opción habría **dos formas de decir
  lo mismo**, y esa duplicidad es la clase de problema que los cortes anteriores
  fueron retirando uno a uno. Quedan `GRAVADO_18` y `NO_GRAVADO`.
- **`acepta_mascotas` se llama `mascotas_aceptadas`** — es la clave que V74
  sembró y que ya tiene aplicabilidad. El catálogo es la autoridad; el documento
  se corrige.
- **Las veintiséis entran `OPC`**, sin excepción, por la razón que V74 escribió:
  que una garantía sea imprescindible para publicar puede ser cierto, pero es
  una decisión del negocio que nadie ha tomado, y tomarla dentro de la migración
  que introduce la clave dejaría fichas ya publicadas incompletas de golpe.

### La aplicabilidad tiene dos coordenadas

No «este atributo corresponde a departamentos», sino «corresponde a este tipo
**cuando existe este tipo de encargo**». El catálogo declara las dos, y hay
condiciones de cada clase:

| | Ejemplo |
|---|---|
| Sólo ALQUILER | `igv_arrendamiento` — en una venta el impuesto es otro |
| Sólo VENTA | `apto_credito_hipotecario` |
| Las dos | `disponible_desde` — un piso vendido también se entrega en una fecha |
| Sólo ciertos tipos | `racks_incluidos` (almacén), `acepta_venta_fraccionada` (terreno) |

Ninguna combinación se rellenó «por si acaso».

---

## Los pares semánticos, todos

El guard de V74 nombraba **uno solo** —`amoblado`— y eso lo dejaba como una
excepción artesanal. El par es el patrón, y V77 lo recorre entero:

| Hecho de la PROPIEDAD | Condición del ENCARGO |
|---|---|
| `amoblado` | `se_ofrece_amoblado` |
| `cuota_mantenimiento` | `mantenimiento_a_cargo_de` |
| `estacionamientos` | `estacionamientos_incluidos` |
| `rubro_permitido` | `rubros_excluidos_por_titular` |
| `mascotas_reglamento` *(falta, Corte 3)* | `mascotas_aceptadas` |
| `nivel_implementacion` *(falta, Corte 4)* | `se_entrega_implementado` |
| `estado_ocupacion` *(falta, Corte 5)* | `entrega_desocupado` |
| `lote_minimo_normativo` *(falta, Corte 5)* | `acepta_venta_fraccionada` |
| `uso` *(columna, no clave del catálogo)* | `uso_admitido_por_titular` |

Que falte el lado PROPIEDAD **no impide sembrar el del ENCARGO**: la condición
es cierta por sí sola —el propietario acepta o no acepta vender por partes— y
esperar al hecho estructural dejaría el encargo mudo por algo que no le
pertenece. El guard no exige que existan los dos: exige que **si existen, no
compartan sujeto**. Así, el día que llegue el hecho, la protección ya está
puesta.

Y **que dos claves estén emparejadas no permite sustituir una por otra**: el
enrutamiento rechaza el cruce en las dos direcciones, y después del doble
rechazo ninguno de los dos queda escrito.

---

## La ausencia no es un «no»

Se sostiene en los tres sitios, y hay una prueba por cada uno:

- **la base** — ninguna columna de valor de `atributo_encargo` lleva `DEFAULT`,
  y una guarda de la migración lo comprueba;
- **el Core** — una condición que nadie declaró no está en el bloque; declarar
  una no rellena las vecinas;
- **la pantalla** — y aquí apareció un defecto real.

### El booleano tenía dos estados y necesita tres

`cl-campo-gobernado` dibujaba un `BOOLEANO` con una casilla, y **una casilla sin
marcar se lee igual que un «no»**: «acepta mascotas» sin declarar y «no acepta
mascotas» eran píxeles idénticos. El dato no se perdía —el editor sólo manda lo
tocado— pero la persona leía una respuesta que nadie dio, que es exactamente el
defecto inventado que este corte prohíbe.

```
''       Sin declarar   ←  todavía no se sabe
'true'   Sí
'false'  No             ←  alguien lo preguntó y la respuesta fue no
```

El arreglo es **de tipo de control, no de clave**: todos los `BOOLEANO`, sin que
la pantalla conozca ninguno por su nombre.

---

## El cable se ensanchó, y era necesario

Dos condiciones no cabían: `precio_estacionamiento_adicional` es un `IMPORTE`
—cifra **y** moneda— y `equipamiento_incluido` es un `LISTA_MULTIPLE`.
`AtributoRequest` llevaba `(clave, valor)`, así que habrían quedado **sembradas
y mudas**: visibles en el catálogo e imposibles de escribir.

El servicio ya lo modelaba (`ValorAtributo(clave, valor, moneda, valores)`); era
el DTO web el que iba estrecho. Se ensanchó en las dos direcciones:

- **al leer**, `AtributoFicha` gana `moneda` y `valores` **crudos** al lado del
  texto compuesto. No es duplicar la verdad: es la misma, una vez para leer y
  otra para poder corregirla. Partir `"PEN 350"` o `"Cocina, Lavadora"` de
  vuelta sería inferir, y un elemento con una coma dentro lo haría imposible;
- **al escribir**, `AtributoRequest` gana los dos huecos.

Y una consecuencia que costó un test: un multivalor **no puede viajar también
como escalar**. El renderizador emitía la lista y además el texto unido por
comas; el trigger del Core rechaza eso —«sus valores van en su tabla, no en la
fila»—. Ahora la lista es la única salida de un `SELECTOR_MULTIPLE`, y quien
necesite la cadena la compone.

Eso arregló al **emisor**. El gate de cierre encontró después que el
**receptor** tampoco lo comprobaba: ver «Lo que el gate encontró», punto 3.

---

## Verificación

```
backend   reactor completo · 0 skipped · 22/22 suites de integración
angular   668/668
build     producción sin errores
E2E       cierre por defecto, con editor-universal ampliado a VENTA
```

`SujetoDelDatoIntegrationTest` pasa de 21 a **28**, con siete casos nuevos:

| Caso | Qué demuestra |
|---|---|
| la VENTA tiene vocabulario propio | ≥ 6 condiciones aplicables a `V`, y ninguna lista sin opciones |
| ningún par comparte sujeto | los **nueve** pares, contra el catálogo real |
| el par no se puede cruzar | el hecho rechazado como condición, la condición rechazada como atributo, y **ninguno de los dos escrito** |
| **dos encargos conservan condiciones distintas** | ver abajo |
| lo que nadie declaró no es un «no» | bloque vacío, declarar una no rellena las vecinas, y cero `DEFAULT` en la base |
| un IMPORTE y un multivalor se pactan | la moneda y los elementos viajan crudos |
| el enrutamiento no se cruza en ninguna dirección | cada sujeto declara su aplicabilidad en **su** tabla, y sólo en ella |

### La prueba que da sentido al sujeto entero

```
Propiedad P — el inmueble TIENE muebles (hecho, una sola vez)

  Encargo alquiler 2026:   se_ofrece_amoblado = true    mascotas = false
  ── se cierra ──
  Encargo alquiler 2027:   se_ofrece_amoblado = false   mascotas = true
```

Y lo que se afirma: el episodio nuevo **nace sin condiciones** (no hereda), las
dos versiones sobreviven y son contrarias, el retrato completo del primero no se
movió ni un campo al escribir el segundo, el hecho físico sigue en `true` —los
muebles están aunque el segundo alquiler no los ofrezca—, y la propiedad sigue
siendo **una** con sus dos episodios.

Con un solo sujeto esto era irrepresentable: el segundo valor sobrescribía al
primero y nadie se enteraba.

---

---

## El gate de cierre, corrido contra PostgreSQL real

Los cinco conteos, exactos:

```
condiciones ENCARGO       26 = 26
aplicables a VENTA          7 =  7
aplicables a ALQUILER      20 = 20
filas tipo x operacion    112 = 112
opciones sembradas         22 = 22
```

Los cuatro ceros, en cero: ninguna clave del ENCARGO con aplicabilidad de
PROPIEDAD, **ninguna de PROPIEDAD con aplicabilidad de ENCARGO**, ningún
`DEFAULT` sobre los valores de `atributo_encargo`, ninguna `LISTA` o
`LISTA_MULTIPLE` sin vocabulario.

Y los **nueve** pares, todos con la condición en ENCARGO y el hecho —cuando
existe— en PROPIEDAD.

### Lo que el gate encontró, y no era poco

**1 · La comprobación de enrutamiento sólo miraba una dirección.** La guarda de
la migración exigía que ninguna clave del ENCARGO declarara aplicabilidad en la
tabla de la PROPIEDAD, y **la contraria faltaba**: una clave física con una fila
en `catalogo_atributo_operacion` pasaría a preguntarse dentro del bloque de un
encargo — el mismo desorden visto del otro lado. Hoy no ocurre en ninguna de las
dos, y ahora las dos se comprueban.

**2 · El par `uso ↔ uso_admitido_por_titular` no estaba en la lista.** `uso` vive
como **columna** de `propiedad`, no como clave del catálogo, así que el par no
puede colisionar por construcción — pero eso es una propiedad del esquema de hoy,
no una garantía. Entra en la lista: el día que alguien lo gobierne, la
comprobación ya está puesta.

**3 · Un multivalor se podía mandar como lista Y como escalar, y se aceptaba en
silencio.** El escritor hacía `if (valores != null) escribirMultivalor(...)` y
**descartaba el escalar sin decir nada** — la clase exacta de elección que este
proyecto no hace. Es la misma regla que ya regía para una clave que llega con
valor y en `atributosABorrar`: *entre dos intenciones contrarias no se elige, se
avisa*.

El arreglo va en el **constructor de `ValorAtributo`**, y no en cada escritor,
porque hay cuatro —propiedad y encargo, alta y edición— y con la regla repetida
cuatro veces bastaría olvidarla en una. Ahí el estado ambiguo **no se puede
construir**, ni desde el cable, ni desde un test, ni desde KAIROS.

**4 · Y una que resultó ser mía, no del código.** La E2E exigía que un multivalor
volviera en el **orden de envío**. El Core lo devuelve en orden estable
(`order by valor`), que es lo correcto: un multivalor es un **conjunto**, el
orden no es semántico, y que el mismo conjunto se lea siempre igual es lo que
hace comparables dos retratos. La prueba pasó a comparar como conjunto.

> Nota para quien lea el cable: al **leer**, `valor` trae el texto ya compuesto
> —«Cocina, Lavadora»— y `valores` la lista cruda. No es contradicción con la
> regla de arriba: la misma verdad, una vez para pintar y otra para poder
> corregirla. La regla «lista o escalar, nunca ambos» es del **request**.

---

## Lo que NO se hizo, a propósito

- **Ninguna pantalla nueva.** El editor universal ya existía; sólo dejó de
  marcar `IMPORTE` y `LISTA_MULTIPLE` como no editables, porque el motivo por el
  que lo estaban desapareció.
- **Ningún atributo de PROPIEDAD movido** para desbloquear el Corte 1.
- **Nada de KAIROS.** Ni conversación, ni prompts, ni WhatsApp. Lo único que V77
  garantiza es que la verdad que KAIROS consumirá está bien expresada: cada
  condición dice qué significa (`ayuda`), cuándo aplica (tipo × operación), qué
  respuesta espera (`tipo_dato`, `control`), qué opciones admite y **qué falta
  por conocer** — porque la ausencia sigue significando ausencia.

## Deuda registrada

**No hay caso de uso que reabra un encargo cerrado.** `editar` con una operación
actualiza el encargo **vivo** y responde «esta propiedad no tiene ningún encargo
vivo de ALQUILER» cuando no lo hay; el segundo episodio de la prueba de
temporalidad se abre por SQL. Es una capacidad que falta, no un defecto de este
corte, y ya estaba anotada desde 0C — V77 la vuelve a encontrar desde el otro
lado y la deja dicha otra vez.

**Los niveles PUB que el inventario propone siguen sin aplicarse.** Las 26 son
`OPC`. Subir una es una línea de SQL, y es una decisión de negocio.
