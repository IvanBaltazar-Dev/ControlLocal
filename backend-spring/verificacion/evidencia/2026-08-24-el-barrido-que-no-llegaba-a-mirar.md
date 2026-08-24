# El barrido que no llegaba a mirar — `grep -iF` aborta en silencio

**Encontrado el 2026-08-24**, dentro del Corte 3, por el CONSTRUCTOR; reproducido
por el AUDITOR y por CONTROL de forma independiente.

**No es un defecto del Corte 3.** Afecta a cualquier búsqueda que se haga en esta
máquina desde Git Bash. Se registra aquí porque es donde se descubrió, y porque
es **el mismo modo de fallo que el corte venía a arreglar**.

---

## Cómo apareció

El AUDITOR rechazó el candidato por una afirmación falsa escrita en un comentario.
El CONSTRUCTOR la corrigió en los dos sitios citados; **sobrevivió una tercera
copia**, y el segundo rechazo fue por eso.

Al pedirle un **barrido del árbol** —no «arregla las líneas que te señalé», sino
«comprueba que no queda ninguna»— el barrido devolvió **cero coincidencias con la
tercera copia delante**. Estuvo a punto de contestarse «árbol limpio».

Lo detectó porque el cero contradecía una línea que acababa de leer.

---

## Lo medido

`GNU grep 3.0` · Git Bash · `bash 5.2.37(1)-release`

```
$ grep -iF "class" ConservacionDeLaEdicionIntegrationTest.java
rc=134        stdout vacío        stderr: 0 bytes
```

`134 = 128 + 6` → **SIGABRT**. `grep` no «devuelve cero coincidencias»: **se
muere**.

### Es la combinación, no cada opción

```
grep -i    rc=0        grep -iw   rc=0
grep -F    rc=0        grep -iE   rc=0
grep -iF   rc=134
```

### Es universal, no depende de la cadena

```
grep -iF 'a'                      rc=134
grep -iF 'class'                  rc=134
grep -iF 'NULL'                   rc=134
grep -iF 'TEXTO DE PRESENTACION'  rc=134
```

---

## Por qué es invisible — la parte que importa

`grep` **no escribe ni un byte en stderr**. El `Aborted` que a veces se ve lo
imprime **bash**, no `grep`, y **sólo en un comando simple**. En los dos idiomas
con los que se barre de verdad no aparece nada:

```bash
$ grep -riF "manda NULL" backend-spring/controllocal-app/src | wc -l
0                     # ningún "Aborted"
$ echo $?
0                     # el exit status es el de `wc`, no el de grep

$ n=$(grep -riF "manda NULL" ...); [ -z "$n" ] && echo "ARBOL LIMPIO"
ARBOL LIMPIO          # con la copia delante
```

**El falso negativo exacto** que dejó pasar la tercera copia, reproducido en las
tres sesiones.

---

## La regla

> **Un barrido cuyo cero no se ha contrastado con un control positivo no es un
> barrido.**

Buscar **primero** una cadena que se sabe presente y exigir encontrarla. Sólo
entonces significa algo un cero. En concreto, en esta máquina:

- **`grep -i` combinado con `-F` aborta con SIGABRT y sin salida de error.** Un
  `| wc -l` o un `$(…)` descartan el código 134 y devuelven «cero coincidencias».
- **`rg` no está afectado**: `rg -iF` devuelve 0 correctamente para una cadena
  ausente y encuentra el control positivo.
- Dentro de `grep`, sirven `-i` solo, `-iw` o `-iE`.

---

## Por qué se registra como hallazgo del corte

El Corte 3 existe, en su mitad `3.a`, porque el gate `.sql` llevaba **rojo desde
`V77` y sobrevivió a tres cortes cerrados y auditados**: nadie lo ejecutaba, y un
gate que sólo corre si alguien se acuerda no es un gate.

Esto es la misma forma, un nivel más abajo: **verde por no haberse ejecutado**. El
gate decía limpio porque no corría; el barrido decía limpio porque no llegaba a
mirar. En ambos casos la señal de «todo bien» y la de «no se comprobó» son
indistinguibles — y ésa es la propiedad que las hace peligrosas.

Anotado en `CLAUDE.md` para que no se vuelva a descubrir.
