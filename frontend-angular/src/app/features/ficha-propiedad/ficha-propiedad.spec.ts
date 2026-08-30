import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { DocumentosService } from '../../core/api/documentos.service';
import {
  FotoLocal,
  Local,
  LocalesService,
  PrecioLocal,
} from '../../core/api/locales.service';
import { Responsabilidad } from '../../core/api/propiedades.service';
import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { ArchivoPreparado } from '../../core/archivos/archivos.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { diasHasta, FichaPropiedad, MAXIMO_FOTOS } from './ficha-propiedad';

const CAPTACION: Captacion = {
  id: 1,
  codigoCaptacion: 'CAP-0001',
  fechaCaptacion: '2026-07-01',
  fechaInicioVigencia: '2026-07-01',
  fechaFinVigencia: '2026-12-31',
  // 100 = un mes de alquiler (ver `core/comision.ts`).
  comisionPactada: 100,
  tipoOperacion: 'A',
  importeReferencia: 8500,
  monedaReferencia: 'PEN',
  tipoComision: 'E',
  baseCalculo: 'R',
  valorComision: 1,
  monedaComision: 'PEN',
  tratamientoIgv: 'N',
  estado: 'A',
  urgencia: 4,
  exclusividad: true,
  idLocal: 77,
  direccionLocal: 'Av. Larco 812',
  distritoLocal: 'Miraflores',
  areaM2: 120,
  rubro: 'Restaurante',
  propietarioNombre: 'Inmobiliaria Pacifico SAC',
  idAgente: 9,
  agenteNombre: 'Valeria Mora',
};

const LOCAL: Local = {
  id: 77,
  codigoLocal: 'LOC-0001',
  direccion: 'Av. Larco 812',
  distrito: 'Miraflores',
  metraje: 120,
  precioReferencial: 8500,
  monedaReferencial: 'PEN',
  rubroPermitido: 'Restaurante / cafeteria',
  descripcion: 'Local comercial en esquina, primera linea de avenida.',
  idPropietario: 42,
  estado: 'D',
  tipoInmueble: 'L',
  uso: 'C',
  ambientes: 3,
};

const PROPIETARIO: Propietario = {
  id: 42,
  tipoPersona: 'J',
  tipoDocumento: 'RUC',
  numeroDocumento: '20512345678',
  nombre: 'Inmobiliaria Pacifico SAC',
  telefono: '987654321',
  correo: 'contacto@pacifico.test',
  estado: 'A',
  cantidadLocales: 3,
};

const PRECIO: PrecioLocal = {
  id: 1,
  idLocal: 77,
  hito: 'U',
  moneda: 'PEN',
  monto: 8500,
  fecha: '2026-02-01',
};

const FOTO: FotoLocal = {
  idFoto: 5,
  clave: 'locales/77/abc12345-fachada.png',
  nombre: 'fachada.png',
  proveedor: 'DISCO',
};

interface AccesoFicha {
  fotosElegidas(elegidas: readonly ArchivoPreparado[]): Promise<void>;
  eliminarFoto(idFoto: number): Promise<void>;
}

function archivo(nombre = 'foto.png'): ArchivoPreparado {
  return {
    archivo: new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], nombre, {
      type: 'image/png',
    }),
    nombreOriginal: nombre,
    nombreSeguro: nombre,
    extension: '.png',
    tipoContenido: 'image/png',
    tamano: 4,
  };
}

describe('FichaPropiedad', () => {
  let captaciones: jasmine.SpyObj<CaptacionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    captaciones = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'obtenerPorCodigo$',
    ]);
    captaciones.obtenerPorCodigo$.and.returnValue(of(CAPTACION));

    locales = jasmine.createSpyObj<LocalesService>('LocalesService', [
      'obtener$',
      'precios$',
      'fotos$',
      'fotos',
      'subirFoto',
      'eliminarFoto',
    ]);
    // Por defecto, quien mira ES el responsable: es el caso normal y deja que
    // el resto de pruebas hablen de otra cosa. Los demás casos de autoridad se
    // piden con `conAutoridad(...)`, que sustituye este stub.
    locales.obtener$.and.returnValue(of({ ...LOCAL, responsabilidad: responde() }));
    locales.precios$.and.returnValue(of([PRECIO]));
    locales.fotos$.and.returnValue(of([FOTO]));
    locales.fotos.and.resolveTo([FOTO]);
    locales.subirFoto.and.resolveTo(FOTO);
    locales.eliminarFoto.and.resolveTo(undefined);

    propietarios = jasmine.createSpyObj<PropietariosService>('PropietariosService', [
      'obtener$',
    ]);
    propietarios.obtener$.and.returnValue(of(PROPIETARIO));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('resuelve la ficha encadenando ids, sin descargar bandejas ni emparejar por texto', async () => {
    await montar();

    expect(captaciones.obtenerPorCodigo$).toHaveBeenCalledOnceWith('CAP-0001');
    // El local sale de `idLocal` de la captación...
    expect(locales.obtener$).toHaveBeenCalledOnceWith(77);
    // ...y el propietario de `idPropietario` del local.
    expect(propietarios.obtener$).toHaveBeenCalledOnceWith(42);
  });

  it('muestra captación, ficha técnica, propietario y agente', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('CAP-0001');
    expect(html).toContain('Av. Larco 812');
    expect(html).toContain('Restaurante / cafeteria');
    expect(html).toContain('Persona jurídica');
    // El cable manda la letra del tipo de documento, no su nombre.
    expect(html).toContain('RUC 20512345678');
    expect(html).not.toContain('J 20512345678');
    expect(html).toContain('contacto@pacifico.test');
    expect(html).toContain('Valeria Mora');
    expect(html).toContain('Encargo exclusivo');
    expect(html).toContain('4 / 5');
  });

  describe('comisión', () => {
    it('la dice en lenguaje natural y con su importe, no como porcentaje suelto', async () => {
      // 100 % sobre una renta de 8.500 = un mes de alquiler.
      const fixture = await montar();
      const html = texto(fixture);

      expect(html).toContain('Comisión inmobiliaria pactada');
      expect(html).toContain('Un mes de alquiler');
      expect(html).toContain('PEN 8,500');
      // El porcentaje aparece, pero como dato secundario.
      expect(html).toContain('100 % de la renta mensual');
    });

    it('nunca escribe "0.5 meses": medio mes se dice con palabras', async () => {
      captaciones.obtenerPorCodigo$.and.returnValue(of({
        ...CAPTACION, comisionPactada: 50, valorComision: 0.5,
      }));

      const fixture = await montar();
      const html = texto(fixture);

      expect(html).toContain('Medio mes de alquiler');
      expect(html).not.toMatch(/0[.,]5\s*meses/i);
      expect(html).toContain('PEN 4,250'); // media renta
    });

    it('una renta y media se dice "un mes y medio"', async () => {
      captaciones.obtenerPorCodigo$.and.returnValue(of({
        ...CAPTACION, comisionPactada: 150, valorComision: 1.5,
      }));

      const fixture = await montar();

      expect(texto(fixture)).toContain('Un mes y medio de alquiler');
      expect(texto(fixture)).toContain('PEN 12,750');
    });

    it('un porcentaje no convencional se muestra sobre la renta mensual', async () => {
      captaciones.obtenerPorCodigo$.and.returnValue(of({
        ...CAPTACION, comisionPactada: 42.5, tipoComision: 'P', valorComision: 42.5,
      }));

      const fixture = await montar();
      const html = texto(fixture);

      expect(html).toContain('42.5 % de la renta mensual');
      expect(html).not.toContain('PEN 4,250');
      expect(html).toContain('PEN 3,612.5'); // 8.500 × 42,5 %
    });

    it('el importe de la comisión usa la misma moneda que la renta', async () => {
      // Se compara bloque contra bloque, no contra el texto de la página: el
      // histórico de precios lleva su propia moneda por fila (y hace bien).
      const fixture = await montar();
      const elemento = fixture.nativeElement as HTMLElement;
      const monedaDe = (selector: string) =>
        /\b(USD|PEN)\b/.exec(elemento.querySelector(selector)?.textContent ?? '')?.[1];

      expect(monedaDe('.precio')).toBe('PEN');
      expect(monedaDe('.comision')).toBe(monedaDe('.precio'));
    });
  });

  it('muestra la descripción del local, no un párrafo inventado', async () => {
    const fixture = await montar();

    expect(texto(fixture)).toContain('Local comercial en esquina');
    expect(texto(fixture)).not.toContain('afluencia peatonal');
  });

  it('NO ofrece exportar a PDF (D-F5-1)', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).not.toContain('PDF');
    expect(html).not.toContain('Exportar');
  });

  it('no incrusta el mapa de un tercero: lo abre el usuario', async () => {
    const fixture = await montar();
    const elemento = fixture.nativeElement as HTMLElement;

    expect(elemento.querySelector('iframe')).toBeNull();
    const mapa = elemento.querySelector<HTMLAnchorElement>('.enlace a');
    expect(mapa?.href).toContain('google.com/maps');
    expect(mapa?.rel).toContain('noopener');
  });

  it('sigue dibujando la ficha aunque falle un bloque complementario', async () => {
    locales.precios$.and.returnValue(throwError(() => new ApiError(500, 'Histórico caído.')));

    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Av. Larco 812');
    expect(html).toContain('Histórico caído.');
  });

  it('si falla el local, la ficha se mantiene y el propietario no se pide', async () => {
    locales.obtener$.and.returnValue(throwError(() => new ApiError(500, 'Local caído.')));

    const fixture = await montar();

    expect(texto(fixture)).toContain('CAP-0001');
    expect(texto(fixture)).toContain('Local caído.');
    expect(propietarios.obtener$).not.toHaveBeenCalled();
  });

  it('el 403 de alcance se explica como alcance, no como "no encontrada"', async () => {
    captaciones.obtenerPorCodigo$.and.returnValue(
      throwError(() => new ApiError(403, 'Sin permiso.')),
    );

    const fixture = await montar();

    expect(texto(fixture)).toContain('fuera de tu alcance');
  });

  /**
   * **Quién escribe las fotos** (P0).
   *
   * Este bloque decía otra cosa, y su verde era el problema: afirmaba
   * `it('el agente puede agregar y eliminar fotos')` y comprobaba que un agente
   * cualquiera veía «Agregar fotos». Ese invariante quedó **derogado por V87**
   * —las fotos son la ficha del inmueble, así que las escribe **su
   * responsable**—, pero el test seguía verde y por eso el defecto sobrevivió a
   * un corte entero: la pantalla ofrecía los dos botones a todo agente sobre
   * toda propiedad y el backend respondía **403 siempre**.
   *
   * La lección no es «faltaba un caso»: es que **un test puede fijar una regla
   * que ya no existe**, y entonces protege el defecto en vez del invariante.
   * Por eso ahora los casos se nombran por la **autoridad**, que es lo que
   * decide, y no por el rol, que ya no decide nada aquí.
   *
   * El rol de la sesión sigue viajando al montar porque otras partes de la
   * ficha lo usan; para la galería es **irrelevante a propósito** — hay un caso
   * que lo comprueba.
   */
  describe('quién escribe las fotos (P0)', () => {
    it('el responsable del inmueble agrega y elimina', async () => {
      const fixture = await montar('AGENTE');
      const html = texto(fixture);
      expect(html).toContain('Agregar fotos');
      expect(html).toContain('Eliminar');

      const acceso = fixture.componentInstance as unknown as AccesoFicha;
      await acceso.fotosElegidas([archivo()]);
      expect(locales.subirFoto).toHaveBeenCalledTimes(1);
      expect(locales.subirFoto.calls.mostRecent().args[0]).toBe(77);

      await acceso.eliminarFoto(FOTO.idFoto);
      expect(locales.eliminarFoto).toHaveBeenCalledOnceWith(77, FOTO.idFoto);
    });

    it('OTRO responsable: ni botones ni escritura, y se dice por qué', async () => {
      // El caso que el test viejo no tenía: un AGENTE de pleno derecho, con su
      // sesión válida, sobre una propiedad de otro. Antes veía los dos botones.
      conAutoridad(deOtro());
      const fixture = await montar('AGENTE');
      const html = texto(fixture);

      expect(html).toContain('Galería del local');
      expect(html).not.toContain('Agregar fotos');
      expect(html).not.toContain('Eliminar');
      // Y no desaparecen en silencio: el motivo lo escribe el Core.
      expect(html).toContain('responde otro agente');
      expect(html).toContain('Ana Responsable');

      const acceso = fixture.componentInstance as unknown as AccesoFicha;
      await acceso.fotosElegidas([archivo()]);
      await acceso.eliminarFoto(FOTO.idFoto);
      expect(locales.subirFoto).not.toHaveBeenCalled();
      expect(locales.eliminarFoto).not.toHaveBeenCalled();
    });

    it('propiedad FALTANTE: nadie escribe, y el motivo lo dice', async () => {
      // Es el caso que hoy manda: las 26 propiedades de `dev` están FALTANTE.
      conAutoridad(faltante());
      const fixture = await montar('AGENTE');
      const html = texto(fixture);

      expect(html).not.toContain('Agregar fotos');
      expect(html).toContain('no tiene agente responsable');

      const acceso = fixture.componentInstance as unknown as AccesoFicha;
      await acceso.fotosElegidas([archivo()]);
      expect(locales.subirFoto).not.toHaveBeenCalled();
    });

    it('sin bloque de autoridad en el cable, no se ofrece nada', async () => {
      // Jackson va NON_NULL: un `responsabilidad` ausente llega `undefined`, no
      // `null`. Se cae del lado que NO ofrece el botón, igual que la ficha
      // universal y el editor — antes cada pantalla elegía su propio defecto.
      conAutoridad(undefined);
      const fixture = await montar('AGENTE');

      expect(texto(fixture)).not.toContain('Agregar fotos');
      const acceso = fixture.componentInstance as unknown as AccesoFicha;
      await acceso.fotosElegidas([archivo()]);
      expect(locales.subirFoto).not.toHaveBeenCalled();
    });

    // Dos pruebas y no un bucle: `TestBed.configureTestingModule` no se puede
    // llamar dos veces en el mismo `it` -- el segundo montaje falla con
    // «the test module has already been instantiated».
    it('el BROKER ve la galería y no la escribe', async () => {
      conAutoridad(noOpera());
      const fixture = await montar('BROKER');
      const html = texto(fixture);

      expect(html).toContain('Galería del local');
      expect(html).not.toContain('Agregar fotos');
      expect(html).toContain('Supervisar y gobernar no es registrar');

      const acceso = fixture.componentInstance as unknown as AccesoFicha;
      await acceso.fotosElegidas([archivo()]);
      expect(locales.subirFoto).not.toHaveBeenCalled();
    });

    it('el gobierno del tenant tampoco escribe: las fotos son del responsable', async () => {
      conAutoridad(noOpera());
      const fixture = await montar('TENANT_ADMIN');
      const acceso = fixture.componentInstance as unknown as AccesoFicha;

      await acceso.fotosElegidas([archivo()]);

      expect(locales.subirFoto).not.toHaveBeenCalled();
      expect(texto(fixture)).not.toContain('Agregar fotos');
    });

    it('la galería NO mira el rol de la sesión: manda la autoridad del cable', async () => {
      // El caso que cierra la puerta a que vuelva la copia de la regla. Un
      // BROKER cuya respuesta dijera `puedeEditar` escribiría — porque quien
      // decide es el Core, no esta pantalla. Si alguien reintrodujera un
      // `sesion()?.rol === 'AGENTE'`, esto se pone rojo.
      conAutoridad(responde());
      const fixture = await montar('BROKER');

      expect(texto(fixture)).toContain('Agregar fotos');
    });
  });

  it('no intenta pasarse del tope de fotos del backend', async () => {
    const llena = Array.from({ length: MAXIMO_FOTOS }, (_, indice) => ({
      ...FOTO,
      idFoto: indice + 1,
    }));
    locales.fotos$.and.returnValue(of(llena));

    const fixture = await montar('AGENTE');
    const acceso = fixture.componentInstance as unknown as AccesoFicha;
    await acceso.fotosElegidas([archivo()]);

    expect(locales.subirFoto).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('Límite de 6 fotos alcanzado');
  });

  it('relee la galería aunque la subida falle a mitad de tanda', async () => {
    locales.subirFoto.and.rejectWith(new ApiError(500, 'Almacén caído.'));

    const fixture = await montar('AGENTE');
    const acceso = fixture.componentInstance as unknown as AccesoFicha;
    await acceso.fotosElegidas([archivo('a.png'), archivo('b.png')]);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Almacén caído.');
    expect(locales.fotos).toHaveBeenCalledWith(77);
  });

  it('una captación sin local lo dice en vez de fingir ficha técnica', async () => {
    captaciones.obtenerPorCodigo$.and.returnValue(of({ ...CAPTACION, idLocal: undefined }));

    const fixture = await montar();

    expect(texto(fixture)).toContain('no tiene un local asociado');
    expect(locales.obtener$).not.toHaveBeenCalled();
  });

  describe('diasHasta', () => {
    const hoy = new Date(2026, 6, 31);

    it('cuenta días completos hacia adelante y hacia atrás', () => {
      expect(diasHasta('2026-08-10', hoy)).toBe(10);
      expect(diasHasta('2026-07-21', hoy)).toBe(-10);
      expect(diasHasta('2026-07-31', hoy)).toBe(0);
    });

    it('no depende de la hora a la que se abra la ficha', () => {
      const tarde = new Date(2026, 6, 31, 23, 59, 59);
      expect(diasHasta('2026-08-10', tarde)).toBe(10);
    });

    it('sin fecha no inventa un número', () => {
      expect(diasHasta(undefined, hoy)).toBeNull();
      expect(diasHasta('', hoy)).toBeNull();
    });
  });

  /**
   * La autoridad tal como la resuelve el Core, con los textos que él escribe.
   *
   * Se declaran los cuatro casos del cable —responde, otro responde, FALTANTE y
   * «gobernar no es registrar»— porque son los que la pantalla tiene que saber
   * distinguir. El quinto, **ausente**, no necesita fábrica: es `undefined`.
   */
  /** Sustituye la ficha del local por una con OTRA autoridad. */
  function conAutoridad(responsabilidad: Responsabilidad | undefined): void {
    locales.obtener$.and.returnValue(of({ ...LOCAL, responsabilidad }));
  }

  function responde(): Responsabilidad {
    return { idResponsable: 2, nombre: 'Prueba', puedeEditar: true };
  }

  function deOtro(): Responsabilidad {
    return {
      idResponsable: 99,
      nombre: 'Ana Responsable',
      puedeEditar: false,
      motivo: 'OTRO_RESPONSABLE',
      motivoTexto:
        'De esta propiedad responde otro agente. Puedes consultarla, y puedes operar los ' +
        'encargos que sean tuyos, pero sus datos los escribe su responsable.',
    };
  }

  function faltante(): Responsabilidad {
    return {
      puedeEditar: false,
      motivo: 'FALTA_RESPONSABLE',
      motivoTexto:
        'Esta propiedad no tiene agente responsable asignado, asi que todavia no la edita ' +
        'nadie. Un broker tiene que asignarlo antes.',
    };
  }

  function noOpera(): Responsabilidad {
    return {
      idResponsable: 2,
      nombre: 'Prueba',
      puedeEditar: false,
      motivo: 'NO_OPERA',
      motivoTexto:
        'Supervisar y gobernar no es registrar: los hechos de la propiedad los escribe el ' +
        'agente que responde por ella.',
      puedeTraspasar: true,
    };
  }

  async function montar(
    rol: RolSesion = 'AGENTE',
    codigo = 'CAP-0001',
  ): Promise<ComponentFixture<FichaPropiedad>> {
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 2,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });

    TestBed.configureTestingModule({
      imports: [FichaPropiedad],
      providers: [
        { provide: CaptacionesService, useValue: captaciones },
        { provide: LocalesService, useValue: locales },
        { provide: PropietariosService, useValue: propietarios },
        { provide: AuthService, useValue: { sesion } },
        // La miniatura pide su binario con token; aquí basta con que no falle.
        {
          provide: DocumentosService,
          useValue: {
            contenido$: () => of(new Blob([new Uint8Array([1])], { type: 'image/png' })),
            liberar: () => undefined,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ codigo })) },
        },
        { provide: Router, useValue: router },
      ],
    });

    const fixture = TestBed.createComponent(FichaPropiedad);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<FichaPropiedad>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
