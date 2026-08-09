import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { DocumentoSolicitud, Solicitud, SolicitudesService } from '../../core/api/solicitudes.service';
import { ArchivoPreparado } from '../../core/archivos/archivos.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { DocumentosSolicitud } from './documentos-solicitud';

const OBSERVADA: Solicitud = {
  id: 4,
  codigoSolicitud: 'SOL-260715103000',
  estado: 'O',
  documentosEntregados: 5,
  documentosRequeridos: 6,
};

/** Los seis tipos requeridos, todos entregados y conformes. */
const COMPLETOS: DocumentoSolicitud[] = ['I', 'R', 'V', 'E', 'G', 'D'].map((tipo, i) => ({
  id: i + 1,
  tipoDocumento: tipo,
  nombreArchivo: `${tipo}.pdf`,
  rutaArchivo: `SOL-1/${tipo}.pdf`,
  estado: 'V',
}));

describe('DocumentosSolicitud', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', [
      'porCodigo',
      'documentos',
      'subirDocumento',
      'reenviar',
    ]);
    api.porCodigo.and.resolveTo(OBSERVADA);
    api.documentos.and.resolveTo(COMPLETOS);
    api.subirDocumento.and.resolveTo(COMPLETOS[0]);
    api.reenviar.and.resolveTo({ ...OBSERVADA, estado: 'E' });
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /** El checklist tiene SEIS filas: poder y "otro" no cuentan al avance. */
  it('el checklist dibuja los seis tipos requeridos, no los ocho del cable', async () => {
    const fixture = await montar();

    expect(acceder(fixture).filas().length).toBe(6);
    expect(texto(fixture)).not.toContain('Poder de representacion');
  });

  it('un tipo sin archivo no muestra estado del cable sino "pendiente de carga"', async () => {
    api.documentos.and.resolveTo(COMPLETOS.slice(0, 5));
    const fixture = await montar();

    expect(texto(fixture)).toContain('Pendiente de carga');
    expect(acceder(fixture).entregados()).toBe(5);
  });

  it('si hay varios del mismo tipo gana el último cargado', async () => {
    api.documentos.and.resolveTo([
      { id: 1, tipoDocumento: 'I', estado: 'O', rutaArchivo: 'a.pdf' },
      { id: 2, tipoDocumento: 'I', estado: 'R', rutaArchivo: 'b.pdf' },
    ]);
    const fixture = await montar();

    expect(acceder(fixture).filas()[0].documento?.id).toBe(2);
  });

  it('no reenvía con documentos por cargar u observados', async () => {
    api.documentos.and.resolveTo([{ ...COMPLETOS[0], estado: 'O' }]);
    const fixture = await montar();

    expect(acceder(fixture).puedeReenviar()).toBeFalse();
    expect(acceder(fixture).motivoBloqueo()).toContain('por cargar o subsanar');
  });

  it('reenvía cuando el expediente está completo y el estado lo permite', async () => {
    const fixture = await montar();

    expect(acceder(fixture).puedeReenviar()).toBeTrue();
    await acceder(fixture).reenviar();

    expect(api.reenviar).toHaveBeenCalledWith(4);
    expect(router.navigate).toHaveBeenCalledWith(['/solicitudes', 'SOL-260715103000']);
  });

  /**
   * En revisión el reenvío se bloquea, pero **cargar sigue permitido**: el
   * broker puede observar un documento suelto sin devolver la solicitud entera.
   */
  it('en revisión bloquea el reenvío pero deja subsanar documentos', async () => {
    api.porCodigo.and.resolveTo({ ...OBSERVADA, estado: 'E' });
    const fixture = await montar();

    expect(acceder(fixture).puedeReenviar()).toBeFalse();
    expect(acceder(fixture).motivoBloqueo()).toContain('ya está en evaluación');
    expect(acceder(fixture).puedeCargar()).toBeTrue();
  });

  it('una solicitud resuelta es solo lectura', async () => {
    api.porCodigo.and.resolveTo({ ...OBSERVADA, estado: 'C' });
    const fixture = await montar();

    expect(acceder(fixture).puedeCargar()).toBeFalse();
    expect(acceder(fixture).puedeReenviar()).toBeFalse();
  });

  it('el broker no carga ni reenvía', async () => {
    const fixture = await montar('BROKER');

    expect(acceder(fixture).puedeCargar()).toBeFalse();
    expect(acceder(fixture).puedeReenviar()).toBeFalse();
  });

  it('la subida manda el tipo seleccionado y el archivo ya validado', async () => {
    const fixture = await montar();
    const pantalla = acceder(fixture);
    const archivo = new File(['x'], 'dni.pdf', { type: 'application/pdf' });

    pantalla.seleccionarTipo('E');
    await pantalla.subir([preparado(archivo)]);

    expect(api.subirDocumento).toHaveBeenCalledWith(4, 'E', archivo);
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
  ): Promise<ComponentFixture<DocumentosSolicitud>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 30,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [DocumentosSolicitud],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'SOL-260715103000' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(DocumentosSolicitud);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function preparado(archivo: File): ArchivoPreparado {
  return {
    archivo,
    nombreOriginal: archivo.name,
    nombreSeguro: archivo.name,
    extension: '.pdf',
    tipoContenido: 'application/pdf',
    tamano: archivo.size,
  };
}

interface AccesoExpediente {
  filas(): { tipo: string; documento: DocumentoSolicitud | null }[];
  entregados(): number;
  puedeReenviar(): boolean;
  puedeCargar(): boolean;
  motivoBloqueo(): string;
  reenviar(): Promise<void>;
  seleccionarTipo(tipo: string): void;
  subir(preparados: readonly ArchivoPreparado[]): Promise<void>;
}

function acceder(fixture: ComponentFixture<DocumentosSolicitud>): AccesoExpediente {
  return fixture.componentInstance as unknown as AccesoExpediente;
}

function texto(fixture: ComponentFixture<DocumentosSolicitud>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
