import {
  comoFecha,
  fechaCorta,
  monto,
  numero,
  porcentaje,
  siNo,
  SIN_DATO,
  texto,
} from './formato';

describe('formato', () => {
  it('no corre un día las fechas sin hora', () => {
    // `new Date('2026-07-30')` es medianoche UTC: en Lima (UTC-5) caería el 29.
    const fecha = comoFecha('2026-07-30')!;

    expect(fecha.getFullYear()).toBe(2026);
    expect(fecha.getMonth()).toBe(6);
    expect(fecha.getDate()).toBe(30);
  });

  it('respeta la hora local cuando el cable la manda', () => {
    const fecha = comoFecha('2026-07-30T09:15:00')!;

    expect(fecha.getDate()).toBe(30);
    expect(fecha.getHours()).toBe(9);
  });

  it('devuelve el placeholder para valores ausentes o inválidos', () => {
    expect(fechaCorta(undefined)).toBe(SIN_DATO);
    expect(fechaCorta('')).toBe(SIN_DATO);
    expect(fechaCorta('no-es-fecha')).toBe(SIN_DATO);
    expect(numero(undefined)).toBe(SIN_DATO);
    expect(monto(null)).toBe(SIN_DATO);
    expect(texto('   ')).toBe(SIN_DATO);
    expect(siNo(undefined)).toBe(SIN_DATO);
  });

  it('antepone el código de moneda, nunca el símbolo', () => {
    expect(monto(2500, 'USD')).toContain('USD');
    expect(monto(2500, 'USD')).not.toContain('$');
    // Sin moneda declarada no se inventa PEN ni USD.
    expect(monto(1200, undefined)).toBe('—');
  });

  it('formatea números sin forzar decimales que no existen', () => {
    expect(numero(85)).toBe('85');
    expect(numero(85.5)).toBe('85.5');
    expect(numero(3.14159)).toBe('3.14');
    expect(numero(4.7, 0)).toBe('5');
  });

  it('traduce el booleano del cable a sí/no', () => {
    expect(siNo(true)).toBe('Sí');
    expect(siNo(false)).toBe('No');
  });

  it('conserva el cero como dato y no lo confunde con ausente', () => {
    expect(numero(0)).toBe('0');
    expect(monto(0, 'PEN')).toBe('PEN 0');
  });

  describe('porcentaje', () => {
    it('no agrupa millares: "4,250 %" se leería como 4,25 %', () => {
      expect(porcentaje(4250)).toBe('4250 %');
      expect(porcentaje(4250)).not.toContain(',');
    });

    it('conserva los decimales que tiene', () => {
      expect(porcentaje(4.25)).toBe('4.25 %');
      expect(porcentaje(5)).toBe('5 %');
    });

    it('ausente o no finito da el placeholder, no "NaN %"', () => {
      expect(porcentaje(undefined)).toBe(SIN_DATO);
      expect(porcentaje(Number.NaN)).toBe(SIN_DATO);
    });
  });
});
