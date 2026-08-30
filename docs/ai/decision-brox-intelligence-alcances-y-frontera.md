# D-I-1 · BROX Intelligence: tres alcances, y una frontera de información

**Fecha:** 2026-08-30
**Estado:** **ARQUITECTURA REGISTRADA — NO IMPLEMENTADA.**
**Origen:** decisión del titular durante el corte **P0 · Autoridad de edición de
la propiedad**.
**Alcance de este documento:** ninguno ejecutable. **No hay código de
Intelligence en el repositorio**, no se abre ningún frente y nada de lo que aquí
se describe entra en P0.

---

## Por qué se escribe ahora si no se implementa ahora

Porque P0 está creando **la primera columna de gobierno interno de la
propiedad** —`propiedad.id_rol_responsable`, `V87`— y las decisiones sobre qué
información sale de la operación interna hay que tomarlas **antes** de que
existan consumidores, no después. Un dato que ya viaja es mucho más difícil de
retirar que uno que nunca viajó: es la misma razón por la que el North Star pide
que nada se retire antes de tener su reemplazo activo.

Este documento existe para que **P0 no tome decisiones incompatibles** con lo
que viene, y para que quien implemente Demanda, Matcher e Intelligence no tenga
que reconstruir el criterio desde cero.

---

## 1. Intelligence no depende de Network

**Un mismo motor de matching**, con **tres alcances progresivos**. El alcance
**AGENTE** es útil por sí solo y no necesita que exista la red entre tenants:

| Alcance | La pregunta que responde | Universo de búsqueda | Qué puede REVELAR |
|---|---|---|---|
| **AGENTE** | «¿qué propiedades **tengo** para este requerimiento?» | lo que ese agente opera de forma autorizada | toda la información que ese agente ya necesita para trabajar |
| **TENANT** | «¿qué tiene **mi oficina**?» | la cartera del tenant | más información interna para coordinar, **incluido el responsable correspondiente** |
| **NETWORK** | «¿existe una oportunidad **fuera**?» | cruza la frontera entre tenants | **información mínima suficiente**, política mucho más restrictiva |

**TENANT no abre automáticamente**: clientes y contactos de otros agentes, notas
privadas, conversaciones, histórico completo, procedencia granular ni documentos
privados. «Ver más de la cartera» no es «ver todo de la cartera».

**NETWORK, antes de una colaboración**, sirve para saber **que existe una
oportunidad y qué organización la gestiona**. No sale: propietario, contacto,
agente responsable, dirección exacta, documentación, históricos, datos internos
del tenant, **ni otras propiedades de esa organización**. Una colaboración
**explícitamente aceptada** puede habilitar una proyección adicional **sólo para
esa oportunidad**; **nunca** se concede a un tenant acceso a la cartera del otro.

---

## 2. La regla fundamental

> **BROX Intelligence puede usar más información para CALCULAR un match que la
> que está autorizado a REVELAR como resultado.**

De ahí la forma de la tabla de arriba, que conviene leer en las dos columnas a la
vez:

```
AGENTE  →  TENANT  →  NETWORK
   universo de búsqueda   ────────►  AUMENTA
   exposición del resultado ───────►  DISMINUYE
```

Son dos ejes independientes y se mueven en direcciones contrarias. Confundirlos
—«si busca más, puede enseñar más»— es el error que este documento existe para
prevenir.

---

## 3. La frontera de información, que **sí** obliga hoy

Esta parte no es futura: es la regla de construcción que P0 ya cumple y que
cualquier salida nueva tiene que cumplir.

- **No se usa «cliente» como actor de BROX.** Compradores, arrendatarios e
  interesados **no usan BROX directamente**. Son sujetos del dato, no actores del
  sistema.
- **Dentro del tenant**, Web y KAIROS pueden usar información operativa **según
  permisos** —responsable actual, agente del encargo, estado, procedencia cuando
  corresponda, gobierno, trazabilidad—. Eso **no** significa que todo agente del
  tenant pueda ver todo.
- **Fuera de la operación interna** —publicaciones, anuncios, fichas compartidas,
  exportaciones, colaboraciones entre tenants— sólo sale una **proyección
  expresamente autorizada**. Y la regla de construcción:

  > **Nunca serializar el modelo interno completo para después ocultar campos.**

- **No salen automáticamente de la frontera**: `id_rol_responsable`,
  `id_rol_incorporo`, permisos, tenant interno, linaje, auditoría, notas
  internas, procedencia granular e históricos privados.

### Cómo lo cumple P0, medido

`FichaPropiedadUniversal` y `PropiedadResponse` se construyen **campo a campo**,
como `record`s explícitos: no hay ninguna serialización de la entidad `Propiedad`
con exclusiones encima. Por eso «este campo no sale fuera» es una afirmación
comprobable y no una promesa.

La única proyección externa que existe hoy es `publicacion` —lo que el mercado
ve: canal, título, importe, moneda y URL—, y `AutoridadDeEdicionIntegrationTest`
comprueba que **no lleva ninguna columna de gobierno**: ni `id_rol_responsable`,
ni `id_rol_incorporo`, ni ninguna columna cuyo nombre contenga `responsable`.

---

## 4. Lo que este documento **no** congela

**La allowlist exacta de atributos para NETWORK no se fija aquí.** Se decide
cuando se implementen **Demanda + Matcher + Intelligence** y pueda auditarse el
riesgo de fuga competitiva con casos reales delante. Congelarla ahora sería
adivinar, y adivinar es exactamente lo que el North Star prohíbe.

---

## 5. Qué haría falta antes de implementar

No es un plan —no está autorizado— sino la lista de lo que **este documento deja
sin responder**, para que nadie lo lea como si estuviera cerrado:

1. Qué es exactamente «la operación autorizada» de un agente a efectos de
   búsqueda, y si coincide con `Alcances.de(actor)` o es más estrecha.
2. Cómo se representa una colaboración aceptada, y dónde vive su vigencia.
3. Qué proyección concreta habilita esa colaboración, y por cuánto tiempo.
4. Si el resultado de un match deja rastro, y de qué —calcular con datos que no
   se revelan es justo el caso donde la auditoría importa más.

---

## Relación con otras decisiones

- **P0 · Autoridad de edición** (`V87`): crea `id_rol_responsable` y lo declara
  dato de gobierno **interno**. Este documento es la razón por la que esa
  declaración se escribió en el comentario de la columna y se probó.
- `docs/ai/north-star-brox.md`: el marco. «Todo dato lleva su procedencia» y
  «nada se infiere» siguen mandando aquí.
- `docs/ai/arquitectura-multitenancy-colaboracion.md`: NETWORK es la superficie
  donde esta decisión y aquélla se tocan.
