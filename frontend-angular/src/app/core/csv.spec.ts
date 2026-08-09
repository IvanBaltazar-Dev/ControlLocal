import { generarCsv } from './csv';

describe('csv', () => {
  it('escapa las comas: una dirección con coma es la norma, no la excepción', () => {
    const csv = generarCsv(['Local', 'Distrito'], [['Av. Larco 812, piso 2', 'Miraflores']]);

    expect(csv).toContain('"Av. Larco 812, piso 2"');
    // Sin comillas, esa fila tendría 3 columnas y el archivo quedaría roto.
    expect(csv.split('\r\n')[1].split('","').length).toBe(2);
  });

  it('duplica las comillas internas, como manda RFC 4180', () => {
    const csv = generarCsv(['Nota'], [['El local "esquina" del jirón']]);

    expect(csv).toContain('"El local ""esquina"" del jirón"');
  });

  it('separa filas con CRLF y respeta el orden de las columnas', () => {
    const csv = generarCsv(['A', 'B'], [['1', '2'], ['3', '4']]);

    expect(csv.split('\r\n')).toEqual(['"A","B"', '"1","2"', '"3","4"']);
  });

  it('nulos y ausentes salen como campo vacío, no como "null"', () => {
    const csv = generarCsv(['A', 'B'], [[null, undefined]]);

    expect(csv).toContain('"",""');
    expect(csv).not.toContain('null');
    expect(csv).not.toContain('undefined');
  });

  it('conserva los números crudos: una hoja de cálculo necesita números', () => {
    const csv = generarCsv(['Renta'], [[1200.5]]);

    expect(csv).toContain('"1200.5"');
  });
});
