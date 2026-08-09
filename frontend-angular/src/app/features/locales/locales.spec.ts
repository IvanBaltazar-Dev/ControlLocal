import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscriber } from 'rxjs';
import { PageResponse } from '../../core/api/api.types';
import {
  FiltrosLocales,
  Local,
  LocalesService,
  ResumenLocales,
} from '../../core/api/locales.service';
import { AuthService } from '../../core/auth/auth.service';
import { filtrosLocalesDesdeUrl, Locales } from './locales';

describe('filtrosLocalesDesdeUrl', () => {
  it('restaura texto, estado y pagina desde un enlace compartido', () => {
    const filtros = filtrosLocalesDesdeUrl(
      convertToParamMap({ texto: '  camana ', estado: 'n', page: '7' }),
    );

    expect(filtros).toEqual({ texto: 'camana', estado: 'N', page: 7, mios: false });
  });

  it('normaliza parametros invalidos sin pedir pagina cero', () => {
    const filtros = filtrosLocalesDesdeUrl(
      convertToParamMap({ estado: 'X', page: 'NaN' }),
    );

    expect(filtros).toEqual({ texto: '', estado: '', page: 1, mios: false });
  });

  it('conserva la pagina en "solo mis captaciones" pero descarta texto y estado', () => {
    // `GET /locales/mis-locales` solo acepta pagina y tamano. Arrastrar un
    // texto que el endpoint ignora dejaria la barra pintada con un filtro que
    // no filtra, que es peor que no ofrecerlo.
    const filtros = filtrosLocalesDesdeUrl(
      convertToParamMap({ mios: '1', texto: 'camana', estado: 'D', page: '3' }),
    );

    expect(filtros).toEqual({ texto: '', estado: '', page: 3, mios: true });
  });

  it('solo acepta el "1" exacto como activacion del alcance', () => {
    const filtros = filtrosLocalesDesdeUrl(convertToParamMap({ mios: 'true' }));

    expect(filtros.mios).toBeFalse();
  });
});

describe('Locales reactivo', () => {
  let params$: BehaviorSubject<ParamMap>;
  let service: jasmine.SpyObj<LocalesService>;
  let router: jasmine.SpyObj<Router>;
  let paginas: Map<string, Subscriber<PageResponse<Local>>>;
  let resumenes: Map<string, Subscriber<ResumenLocales>>;
  let canceladas: string[];

  beforeEach(() => {
    params$ = new BehaviorSubject(convertToParamMap({ texto: 'larco', page: '1' }));
    paginas = new Map();
    resumenes = new Map();
    canceladas = [];

    service = jasmine.createSpyObj<LocalesService>('LocalesService', ['pagina$', 'resumen$']);
    service.pagina$.and.callFake(
      (filtros: FiltrosLocales) =>
        new Observable<PageResponse<Local>>((suscriptor) => {
          const clave = filtros.texto ?? '';
          paginas.set(clave, suscriptor);
          return () => canceladas.push(`pagina:${clave}`);
        }),
    );
    service.resumen$.and.callFake(
      (texto?: string) =>
        new Observable<ResumenLocales>((suscriptor) => {
          const clave = texto ?? '';
          resumenes.set(clave, suscriptor);
          return () => canceladas.push(`resumen:${clave}`);
        }),
    );
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);

    TestBed.configureTestingModule({
      providers: [
        { provide: LocalesService, useValue: service },
        {
          provide: AuthService,
          useValue: { sesion: () => ({ rol: 'AGENTE' }) },
        },
        { provide: ActivatedRoute, useValue: { queryParamMap: params$.asObservable() } },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('cancela pagina y KPI anteriores al cambiar filtros', () => {
    const componente = TestBed.runInInjectionContext(() => new Locales());
    componente.ngOnInit();

    params$.next(convertToParamMap({ texto: 'camana', estado: 'D', page: '1' }));

    expect(canceladas).toContain('pagina:larco');
    expect(canceladas).toContain('resumen:larco');

    paginas.get('camana')!.next({
      items: [{ id: 77, codigoLocal: 'LOC-0077' }],
      totalRecords: 1,
      page: 1,
      pageSize: 10,
    });
    paginas.get('camana')!.complete();
    resumenes.get('camana')!.next({
      total: 1,
      disponibles: 1,
      noDisponibles: 0,
      inactivos: 0,
    });
    resumenes.get('camana')!.complete();

    const estado = componente as unknown as {
      paginaDatos: () => PageResponse<Local>;
      resumen: () => ResumenLocales;
    };
    expect(estado.paginaDatos().items[0].id).toBe(77);
    expect(estado.resumen().disponibles).toBe(1);
  });

  it('no repite solicitudes si la URL emite los mismos filtros', () => {
    const componente = TestBed.runInInjectionContext(() => new Locales());
    componente.ngOnInit();
    expect(service.pagina$).toHaveBeenCalledTimes(1);

    params$.next(convertToParamMap({ texto: 'larco', page: '1' }));

    expect(service.pagina$).toHaveBeenCalledTimes(1);
    expect(service.resumen$).toHaveBeenCalledTimes(1);
  });
});
