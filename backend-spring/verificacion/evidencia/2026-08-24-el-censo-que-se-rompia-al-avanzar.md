# Evidencia — Corte 3.a · «el censo que se rompía al avanzar»

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA del código:** `099a723` (V79)
**Punto de partida del trabajo:** `ea993b2` (sólo documentación)
**Encargo que gobierna:** `docs/ai/encargo-corte-3-vivienda-reconstruido.md` §4
**Migración:** ninguna. Este commit no toca el esquema.

---

## 1. El hallazgo, medido antes de tocar nada

`backend-spring/verificacion/gate-modelo-universal.sql` exigía, en su bloque M2:

```sql
SELECT pg_temp.comprobar('M2 el catalogo del sistema tiene 25 atributos',
    (SELECT count(*) = 25 FROM catalogo_atributo WHERE del_sistema));
```

Medido contra `controllocal_dev` el 2026-08-24, antes de escribir una línea:

```
  sujeto   | count
-----------+-------
 PROPIEDAD |    25
 ENCARGO   |    26
```

**51, no 25.** Que PROPIEDAD valga exactamente 25 es la coincidencia que hacía
que el número siguiera *pareciendo* correcto al leerlo.

### Desde cuándo, y por qué nadie lo vio

El censo se actualizó por última vez en `a07a594` (V76), de 19 a 25. Después:

| Migración | Qué sembró | Catálogo del sistema resultante |
|---|---|---|
| V76 (`a07a594`) | — (última vez que se actualizó el censo) | 25 |
| V77 | +20 condiciones del ENCARGO | 45 — **rojo desde aquí** |
| V79 | +6 claves registrales de la PROPIEDAD | 51 |
| V80 (Corte 3.b) | +30 claves de vivienda | 81 |

El censo llevaba **rojo desde V77** y **sobrevivió a tres cortes cerrados y
auditados**. La razón es la fila 7 del precheck de CONTROL, confirmada aquí:

```
$ grep -c "modelo-universal" backend-spring/verificacion/Verificar-Cierre.ps1
0
```

`Verificar-Cierre.ps1` **no ejecutaba el gate `.sql`**. Sólo corría si alguien se
acordaba, y no se acordó nadie durante tres cortes.

---

## 2. La corrida completa del `.sql` en `BASE_SHA` — la primera de verdad

Ejecutado tal cual estaba en `099a723`, sin ninguna modificación:

```
docker cp gate-modelo-universal.sql controllocal-postgres-v2:/tmp/
docker exec controllocal-postgres-v2 psql -U controllocal -d controllocal_dev \
        -v ON_ERROR_STOP=1 -f /tmp/gate-modelo-universal.sql
```

**Resultado:**

```
 en verde | en rojo | total
----------+---------+-------
       67 |       1 |    68

psql:/tmp/gate-modelo-universal.sql:557: ERROR:  GATE EN ROJO: 1 comprobaciones fallaron
(codigo de salida 3)
```

La única en rojo es la 16:

```
  16  M2 el catalogo del sistema tiene 25 atributos                 FALLO
```

**Las 67 restantes estaban en verde.** Este es el dato que el encargo pedía dejar
escrito, y sale mejor de lo que cabía temer: el deterioro era **una sola
comprobación**, no un fichero podrido entero. Lo que estaba podrido era el hábito
de no ejecutarlo.

---

## 3. Qué se cambió

### 3.1 · `gate-modelo-universal.sql` — el censo se sustituye por dos

**No se escribió `= 81`.** El comentario que justificaba el censo decía *«el
catálogo del sistema es una constante del producto, no cartera que crece con el
uso»*. Era cierto cuando el catálogo estaba congelado. **El bloque 3e entero es
un programa cuyo propósito explícito es hacerlo crecer, corte a corte**
(V74 → V77 → V79 → V80 → cortes 4-7). Con eso, el número mide el avance del
*roadmap* y no una invariante: se pone rojo **cada vez que el producto avanza
según lo planeado**.

Es el mismo modo de fallo que el propio fichero ya había diagnosticado más abajo,
al convertir dos cifras hermanas en suelos: *«un gate que se rompe al usar el
producto deja de leerse»*. Aquí se rompió al **construir** el producto, y en
efecto dejó de leerse durante tres cortes. Escribir `= 81` dentro de `V80` habría
metido la deuda de V77 y V79 en una migración de siembra, volviéndola
inatribuible, y habría repetido la trampa en los cortes 4 a 7.

En su lugar, **dos** comprobaciones:

```sql
SELECT pg_temp.comprobar('M2 no se retiro ninguna clave del catalogo del sistema',
    (SELECT count(*) >= 51 FROM catalogo_atributo WHERE del_sistema));

SELECT pg_temp.comprobar('M2 ninguna clave del sistema se quedo sin aplicabilidad',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.del_sistema AND c.activo AND NOT c.aplica_todos
           AND ((c.sujeto = 'PROPIEDAD'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                                  WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
             OR (c.sujeto = 'ENCARGO'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                                  WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)))));
```

El comentario de cabecera se reescribió entero: desde cuándo estuvo rojo, por qué
el argumento original caducó, y por qué esto **no relaja** el gate.

### 3.2 · La invariante de aplicabilidad **no existía**

Es lo que hace que el conjunto quede **más fuerte, no más laxo**. Hasta hoy,
sembrar una clave del sistema sin decir a qué aplica pasaba el gate en verde: el
alta no la pinta, el editor no la ofrece, el dato no se captura, y nadie se entera
hasta echarlo en falta. Ahora se caza. Y a diferencia del censo, **se rompe cuando
alguien siembra mal y no cuando el producto avanza**.

Mira la tabla que le toca por sujeto —`catalogo_atributo_tipo` para PROPIEDAD,
`catalogo_atributo_operacion` para ENCARGO—, que es la misma regla que la guarda
2.5 de V78 vigila en la dirección contraria.

### 3.3 · Se comprobó que las tres comprobaciones **muerden**

Un gate que no se ha visto fallar no se sabe si funciona. Las tres se rompieron a
propósito dentro de transacciones que terminan en `ROLLBACK` (la base quedó
intacta):

| Rotura simulada | Predicado devuelve | Esperado |
|---|---|---|
| Clave del sistema `PROPIEDAD` sin fila en `catalogo_atributo_tipo` | `f` | `f` ✅ |
| Clave del sistema `ENCARGO` sin fila en `catalogo_atributo_operacion` | `f` | `f` ✅ |
| Retirar una clave del catálogo del sistema (51 → 50) | `f` | `f` ✅ |

**Límite honesto del suelo, escrito para que nadie lo dé por más de lo que es:**
`>= 51` es el valor congelado por el encargo, medido antes de V80. Con 51 claves
detecta la retirada de una sola. Con 81 —después de V80— sólo detectaría la
retirada de treinta y una de golpe. **El suelo no es el guardián de la retirada a
partir de aquí; es un piso que impide el vaciado.** Quien de verdad vigila la
salud de cada clave es la invariante de aplicabilidad, que no depende del tamaño.
Subir el suelo corte a corte reintroduciría exactamente el problema que este
commit arregla, así que **no se sube**. Queda registrado como consecuencia
aceptada, no como hueco silencioso.

### 3.4 · `Verificar-Cierre.ps1` — el gate entra en la corrida

Nuevo paso propio, **antes del reactor**:

```
== 1. Requisitos de la corrida de cierre ==
== 2. Gate del modelo universal contra la base real ==   <-- nuevo
== 3. Reactor completo contra PostgreSQL real ==
== 4. Los tests de integracion se EJECUTARON, no se saltaron ==
== 5. Suites E2E del cierre ==
```

Va antes del reactor a propósito: es la comprobación **más barata** de la corrida
—segundos contra los minutos de `mvn clean install`— y la que más veces ha tenido
razón. Si el esquema o la siembra están mal, saberlo antes de compilar.

**Aborta en rojo**, no avisa: si `docker cp` falla o si `psql` sale con código
distinto de cero, la corrida termina con `CIERRE ABORTADO`. No hay rama que salte
el gate «si la base no está levantada» — eso es justamente lo que lo dejó sin
correr tres cortes.

Dos parámetros nuevos, con los valores documentados en la cabecera del `.sql`:
`-ContenedorPostgres controllocal-postgres-v2` y `-BaseDelGate controllocal_dev`.
Se corre contra la base de **desarrollo** porque es el corpus real y es para la
que el gate está escrito (elige propiedad, titular y encargo vivos). No la ensucia:
todo ocurre dentro de una transacción que termina en `ROLLBACK`.

El fichero sigue siendo **ASCII puro y sin BOM** (`file` dice `ASCII text`), que es
la condición para que PowerShell 5.1 lo parsee.

---

## 4. Verificación

Gate `.sql` con los cambios, contra `controllocal_dev`, **antes de V80**:

```
 en verde | en rojo | total
----------+---------+-------
       69 |       0 |    69

DO
ROLLBACK
(codigo de salida 0)
```

**69 de 69 en verde.** Sube de 68 a 69 comprobaciones porque una pasó a ser dos, y
la que se fue en rojo se va sustituida por dos que están en verde **y dicen algo
que antes nadie decía**.

---

## 5. Alcance — qué NO entra en `3.a`

Acotado por §4 del encargo, y se respetó:

- **Ninguna otra comprobación del `.sql`** se tocó. Las 67 que estaban en verde
  siguen escritas exactamente igual.
- **Ninguna suite, ningún test de Java, ninguna migración.** El esquema no cambia.
- No se tocó el inventario de las 20 clases de integración: no hay fichero de
  test nuevo, así que `GateDeCierreTest` y la lista de `Verificar-Cierre.ps1`
  siguen coincidiendo sin tocarlas.
- Las 30 claves de vivienda son el commit siguiente (`3.b`, `V80`).
