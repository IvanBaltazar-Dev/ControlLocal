import {
  calcularCondicionComision,
  comisionPactadaCompatible,
  comisionSobreRenta,
  desembolsoInicial,
  descripcionCondicionComision,
  equivalenciaComision,
  esEquivalenciaEnMeses,
  importeTexto,
  UN_MES,
} from './comision';
import { SIN_DATO } from './formato';

const RENTA = { valor: 4250, moneda: 'PEN' };

describe('comisión', () => {
  describe('condición económica normalizada', () => {
    it('calcula mensualidades y porcentaje sin perder la moneda de la base', () => {
      expect(calcularCondicionComision({
        tipoComision: 'E', baseCalculo: 'R', valorComision: 1.5,
        importeReferencia: 4000, monedaReferencia: 'USD', monedaComision: 'USD',
      })).toEqual({ valor: 6000, moneda: 'USD' });
      expect(calcularCondicionComision({
        tipoComision: 'P', baseCalculo: 'R', valorComision: 25,
        importeReferencia: 4000, monedaReferencia: 'PEN', monedaComision: 'PEN',
      })).toEqual({ valor: 1000, moneda: 'PEN' });
    });

    it('presenta monto fijo y comisión cero con su significado explícito', () => {
      expect(descripcionCondicionComision({
        tipoComision: 'F', baseCalculo: 'N', valorComision: 750,
        monedaComision: 'PEN',
      })).toBe('Monto fijo de PEN 750');
      expect(descripcionCondicionComision({
        tipoComision: 'F', baseCalculo: 'N', valorComision: 0,
        monedaComision: 'PEN', motivoSinComision: 'Campaña institucional',
      })).toBe('Sin comisión · Campaña institucional');
    });

    it('genera solo el adaptador histórico, no una segunda regla editable', () => {
      expect(comisionPactadaCompatible('E', 1.5)).toBe(150);
      expect(comisionPactadaCompatible('P', 12.5)).toBe(12.5);
      expect(comisionPactadaCompatible('F', 800)).toBe(800);
    });
  });

  describe('cálculo sobre la renta', () => {
    it('100 % es una renta mensual completa', () => {
      expect(comisionSobreRenta(UN_MES, RENTA)).toEqual({ valor: 4250, moneda: 'PEN' });
    });

    it('50 % es media renta', () => {
      expect(comisionSobreRenta(50, RENTA)?.valor).toBe(2125);
    });

    it('150 % es una renta y media', () => {
      expect(comisionSobreRenta(150, RENTA)?.valor).toBe(6375);
    });

    it('200 % son dos rentas', () => {
      expect(comisionSobreRenta(200, RENTA)?.valor).toBe(8500);
    });

    it('conserva la moneda de la renta, no una propia', () => {
      expect(comisionSobreRenta(100, { valor: 900, moneda: 'USD' })?.moneda).toBe('USD');
      expect(comisionSobreRenta(100, { valor: 900, moneda: 'PEN' })?.moneda).toBe('PEN');
    });

    it('redondea a dos decimales como el backend', () => {
      expect(comisionSobreRenta(33.33, { valor: 1000, moneda: 'PEN' })?.valor).toBe(333.3);
      expect(comisionSobreRenta(7.5, { valor: 1234.56, moneda: 'PEN' })?.valor).toBe(92.59);
    });

    it('sin porcentaje o sin renta no inventa un importe', () => {
      expect(comisionSobreRenta(null, RENTA)).toBeNull();
      expect(comisionSobreRenta(100, null)).toBeNull();
      expect(comisionSobreRenta(Number.NaN, RENTA)).toBeNull();
    });

    it('4250 NO es "S/ 4.250": es 4250 % de la renta', () => {
      // El valor sembrado de CAP-0001 antes de la migración V12.
      const comision = comisionSobreRenta(4250, RENTA);

      expect(comision?.valor).toBe(180625); // 4250 × 42,5 meses
      expect(comision?.valor).not.toBe(4250);
      expect(importeTexto(comision)).not.toBe('PEN 4,250');
    });
  });

  describe('cómo se dice', () => {
    it('usa lenguaje natural, nunca "0.5 meses"', () => {
      expect(equivalenciaComision(50)).toBe('Medio mes de alquiler');
      expect(equivalenciaComision(100)).toBe('Un mes de alquiler');
      expect(equivalenciaComision(150)).toBe('Un mes y medio de alquiler');
      expect(equivalenciaComision(200)).toBe('Dos meses de alquiler');
    });

    it('ninguna equivalencia contiene jerga de cálculo', () => {
      for (const pactada of [50, 100, 150, 200]) {
        const texto = equivalenciaComision(pactada);
        expect(texto).not.toMatch(/\d+([.,]\d+)?\s*meses/i);
        expect(texto).not.toContain('%');
      }
    });

    it('un porcentaje no convencional se dice como porcentaje sobre la renta', () => {
      expect(equivalenciaComision(25)).toBe('25 % de la renta mensual');
      expect(equivalenciaComision(37.5)).toBe('37.5 % de la renta mensual');
    });

    it('sin valor no dice nada raro', () => {
      expect(equivalenciaComision(undefined)).toBe(SIN_DATO);
      expect(equivalenciaComision(Number.NaN)).toBe(SIN_DATO);
    });

    it('distingue las equivalencias con nombre de las que no lo tienen', () => {
      expect(esEquivalenciaEnMeses(100)).toBeTrue();
      expect(esEquivalenciaEnMeses(4250)).toBeFalse();
      expect(esEquivalenciaEnMeses(undefined)).toBeFalse();
    });
  });

  describe('desembolso inicial', () => {
    const entrada = {
      renta: RENTA,
      mesesGarantia: 2,
      mesesAdelanto: 1,
      comisionPactada: UN_MES,
    };

    it('separa garantía, adelanto y comisión', () => {
      const d = desembolsoInicial(entrada);

      expect(d.garantia.valor).toBe(8500); // 2 meses, del propietario
      expect(d.adelanto.valor).toBe(4250); // 1 mes, del propietario
      expect(d.comision.valor).toBe(4250); // 1 mes, de la inmobiliaria
    });

    it('el total es la suma de los tres y no los sustituye', () => {
      const d = desembolsoInicial(entrada);

      expect(d.total.valor).toBe(17000);
      expect(d.total.valor).toBe(d.garantia.valor + d.adelanto.valor + d.comision.valor);
      // El desglose sigue disponible: el total no mezcla ni oculta conceptos.
      expect(d.garantia.valor).not.toBe(d.total.valor);
      expect(d.comision.valor).not.toBe(d.total.valor);
    });

    it('no confunde la comisión con los meses del propietario', () => {
      // Misma garantía y adelanto, comisión distinta: solo cambia la comisión.
      const a = desembolsoInicial({ ...entrada, comisionPactada: 50 });
      const b = desembolsoInicial({ ...entrada, comisionPactada: 100 });

      expect(a.garantia).toEqual(b.garantia);
      expect(a.adelanto).toEqual(b.adelanto);
      expect(a.comision.valor).toBe(2125);
      expect(b.comision.valor).toBe(4250);
    });

    it('los cuatro conceptos comparten la moneda de la renta', () => {
      const d = desembolsoInicial({ ...entrada, renta: { valor: 1000, moneda: 'USD' } });

      expect([d.garantia.moneda, d.adelanto.moneda, d.comision.moneda, d.total.moneda]).toEqual([
        'USD',
        'USD',
        'USD',
        'USD',
      ]);
    });

    it('lo que falta cuenta como cero, no como renta entera', () => {
      const d = desembolsoInicial({ renta: RENTA });

      expect(d.garantia.valor).toBe(0);
      expect(d.adelanto.valor).toBe(0);
      expect(d.comision.valor).toBe(0);
      expect(d.total.valor).toBe(0);
    });
  });

  describe('importeTexto', () => {
    it('lleva la moneda pegada al valor', () => {
      expect(importeTexto({ valor: 4250, moneda: 'PEN' })).toBe('PEN 4,250');
      expect(importeTexto(null)).toBe(SIN_DATO);
    });
  });
});
