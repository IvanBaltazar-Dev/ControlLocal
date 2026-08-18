# E2.4 — La capa de interpretación (2026-08-18)

**Qué cierra:** `ComoEsta`, el expediente de cuatro renglones y la `lectura` que
los sintetiza.

**Qué NO abre:** `contraste`. D-E2-1 §10.3.2 lo describe dentro del expediente,
pero el mapa lo pone en **E2.6 · «Contraste, pie y metas»** y necesita dos
agregados que no existen —el rango de renta por zona/metraje y las medias
propias—. El campo queda declarado en el contrato y viaja `null`.

---

## 1. Lo que faltaba, según la propia medición del diseño

El traspaso a Angular midió el 2026-08-11 y dejó tres pendientes que E2.4 cierra:

> **`ComoEsta` por asunto**: hoy nada clasifica un hecho como resuelto,
> pendiente, en plazo o freno.
>
> **El expediente de 4 renglones**: los datos existen repartidos; falta la vista
> que los junta, y con ella el `estado` y la `ventana` de cada renglón.
>
> **`lectura`**: hoy nada sintetiza el expediente en una frase.

(De su lista, `DEPENDE_DE_MI`, la política de despacho, `lado`/`paso` y
`hallazgo` ya se habían cerrado en E2.2 y E2.3.)

---

## 2. Cada hecho con su propio estado

El vocabulario es de cinco y **no crece** — es un `enum`, así que un sexto no se
cuela sin decidirlo:

```
HECHO ✓ verde   ya resuelto            FALTA ○ ámbar   lo accionable
PLAZO ⏱ rojo    corre el tiempo        FRENO ⊘ rojo    qué queda parado
DATO  – gris    contexto
```

**Lo decide el dominio.** Angular mapea estado → marca y color y nada más. Si lo
dedujera del tono del asunto volvería el problema que §10.1 arregló: un asunto en
rojo pintando de rojo también sus buenas noticias.

Comprobado en el navegador:

```
○  rgb(217,119,6)  Falta volver a contactar al propietario     <- ámbar
⏱  rgb(220,38,38)  vencio hace 48 dias                         <- rojo
```

**El orden es narrativo, no por gravedad**: lo que ya está → lo que falta → qué
queda parado. Hay un test que falla si la consecuencia adelanta a su causa.

**`avance` viaja `null`, y es correcto.** Ninguno de los seis disparadores trae
hoy un contador real —los documentos verificados viven en la solicitud, no en la
tarea—, y una barra de dos segmentos inventada para rellenar promete una
precisión que no existe.

---

## 3. El expediente: la vista que junta cinco tablas

`ExpedienteDeLaPropiedad`, una consulta por lote:

```
Encargo      [OJO]  Alta el 24 de febrero · vence en 5 dias   (175/180)
Renta               PEN 8500 · sin cambios desde hace 9 dias   ~serie(2)
Actividad    [OJO]  0 visitas realizadas de 2 agendadas
Propietario         Inmobiliaria Pacifico SAC · Av. Larco 812 · Miraflores
```

- La ventana lleva **sus dos números**, no el porcentaje: `175/180` se puede
  leer; un 97 % solo se puede pintar.
- La `serie` solo viaja con **más de un hito**: una chispa de un punto sugiere
  movimiento donde no lo hubo.
- **Cuatro renglones o ninguno**, nunca cuatro guiones. Un expediente vacío dice
  «no hay historial»; cuatro guiones dicen «lo hay y no lo cargué». Una
  prospección no cuelga de ningún local y por eso no tiene expediente.

### Coste

**Tres consultas por página**, no tres por asunto: de qué propiedad habla cada
asunto (una `union` que cubre los cinco tipos), los cuatro renglones de esas
propiedades, y las series.

---

## 4. La lectura, y lo que el test me enseñó

```
La exclusiva casi agotada, nadie lo ha visto todavia.
```

No recita ninguno de los cuatro renglones: los **relaciona**. Que la exclusiva se
agote MIENTRAS nadie ha visto el local es una conclusión; repetir «Alta el 24 de
febrero» es un eco.

> **El test de no-recitado cazó mi primera versión.** La lectura salía
> «Sin ninguna visita todavia» mientras el renglón Actividad decía «Ninguna
> visita todavia»: el mismo hecho, dos veces, dos centímetros más arriba.
>
> Y la corrección no fue reescribir esa frase, sino la regla que la producía:
> **una sola parte no es una síntesis, es un eco.** Sintetizar es relacionar, y
> con un único hecho lo único que se puede hacer es repetirlo. Ahora hacen falta
> dos, y sin ellas la lectura viaja `null` — una lectura de relleno enseña a no
> leerla.

---

## 5. Verificación

```
backend  880 pruebas · 0 fallos · 0 SKIPPED
Angular  565 / 565
```

| Comprobación | Dónde |
|---|---|
| la lectura no recita ningún renglón | unitario + **contra datos reales** |
| ningún código técnico en el texto visible | unitario + contra datos reales |
| ninguna palabra de sector, mercado, industria o benchmark | contra datos reales |
| el vocabulario de estados es de cinco y no crece | unitario |
| como máximo tres hechos | unitario + contra datos reales |
| el orden es narrativo: la causa antes que la consecuencia | contra datos reales |
| un hecho resuelto sale verde aunque el asunto esté en rojo | contra datos reales |
| el expediente son cuatro renglones o ninguno | contra datos reales |

Los unitarios blindan las reglas sobre frases de laboratorio; los de integración
comprueban que **lo que de verdad sale de la base** las cumple. Una regla que
solo se verifica sobre un fixture escrito a mano no ha visto nunca un expediente
real — y fue el de integración el que encontró el recitado.

---

## 6. Un fallo de infraestructura que este test destapó

```
FATAL: sorry, too many clients already
```

Cada `@SpringBootTest` levanta su contexto, Spring los cachea todos, y cada uno
trae un pool de HikariCP de hasta **10 conexiones**. Doce contextos reservan 120
contra un PostgreSQL que admite 100, y el número doce falla al arrancar.

**El fallo no salía donde estaba la causa**: reventaba
`VocabularioPersistidoIntegrationTest`, que no tiene nada que ver — le tocó pedir
contexto el último.

Los tests son de un solo hilo y les bastan dos conexiones. Subir
`max_connections` habría escondido el problema hasta E2.5, que trae más
contextos; el `application.properties` de test capa el pool en 2.

`GateDeCierreTest` también hizo su trabajo: exigió inventariar el test nuevo y
comprobar que `Verificar-Cierre.ps1` sigue exigiendo que **todos** se ejecuten.
