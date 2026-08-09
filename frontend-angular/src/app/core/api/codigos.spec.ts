import {
  CANAL_CONTACTO,
  describir,
  implicaNoContinuidad,
  RESULTADO_INTERACCION,
  resultadosDe,
} from './codigos';

/**
 * El catálogo de códigos es de PRESENTACIÓN, pero tiene un invariante que sí es
 * de contrato: si `resultadosDe()` ofrece un código, `RESULTADO_INTERACCION`
 * tiene que saber describirlo.
 *
 * Esta prueba existe porque el hueco apareció en pantalla: `DESCARTADO` estaba
 * en la allow-list de dos contextos y no en el catálogo, así que el selector lo
 * dibujaba en crudo y en mayúsculas. `describir()` devuelve el código tal cual a
 * propósito —para que un estado nuevo del backend se note en vez de esconderse—,
 * y eso convierte cada omisión en algo visible pero feo. Mejor detectarlo aquí.
 */
describe('codigos', () => {
  const CONTEXTOS = ['PROSPECCION', 'CAPTACION', 'CLIENTE', 'OPORTUNIDAD'];

  it('todo resultado ofrecido por contexto tiene descripción', () => {
    const sinDescripcion = CONTEXTOS.flatMap((contexto) =>
      resultadosDe(contexto)
        .filter((opcion) => opcion.etiqueta === opcion.valor)
        .map((opcion) => `${contexto}/${opcion.valor}`),
    );

    expect(sinDescripcion).toEqual([]);
  });

  /** Un contexto desconocido cae en OPORTUNIDAD, igual que el `switch` del service. */
  it('un contexto desconocido ofrece los de oportunidad', () => {
    expect(resultadosDe('INVENTADO').map((o) => o.valor)).toEqual(
      resultadosDe('OPORTUNIDAD').map((o) => o.valor),
    );
    expect(resultadosDe(undefined).map((o) => o.valor)).toEqual(
      resultadosDe('OPORTUNIDAD').map((o) => o.valor),
    );
  });

  /**
   * El backend reconoce la no continuidad en sus DOS formas: la corta heredada
   * y la palabra del contexto. Si esto se desalinea, la pantalla deja de pedir
   * la razón y el 400 llega del servidor.
   */
  it('reconoce la no continuidad en sus dos formas', () => {
    expect(implicaNoContinuidad('N')).toBeTrue();
    expect(implicaNoContinuidad('D')).toBeTrue();
    expect(implicaNoContinuidad('NO_INTERESADO')).toBeTrue();
    expect(implicaNoContinuidad('DESCARTADO')).toBeTrue();

    expect(implicaNoContinuidad('I')).toBeFalse();
    expect(implicaNoContinuidad('SEGUIMIENTO')).toBeFalse();
    expect(implicaNoContinuidad(undefined)).toBeFalse();
  });

  /** Un código desconocido se muestra en crudo: así se detecta que falta. */
  it('describir devuelve el código tal cual cuando no lo conoce', () => {
    expect(describir(CANAL_CONTACTO, 'L')).toBe('Llamada');
    expect(describir(CANAL_CONTACTO, 'Z')).toBe('Z');
    expect(describir(CANAL_CONTACTO, '')).toBe('');
    expect(describir(RESULTADO_INTERACCION, 'DESCARTADO')).toBe('Descartado');
  });
});
