import { ArchivosService } from './archivos.service';

describe('ArchivosService', () => {
  const servicio = new ArchivosService();

  it('valida firma, tipo y normaliza el nombre de un PDF', async () => {
    const archivo = new File(
      [new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31])],
      'Mi DNI (final).PDF',
      { type: 'application/pdf' },
    );

    const resultado = await servicio.validar(archivo, { extensiones: ['.pdf'] });

    expect(resultado.valido).toBeTrue();
    if (resultado.valido) {
      expect(resultado.archivo.nombreSeguro).toBe('Mi_DNI__final_.pdf');
      expect(resultado.archivo.tipoContenido).toBe('application/pdf');
    }
  });

  it('rechaza un archivo renombrado cuya firma no corresponde', async () => {
    const archivo = new File(['<script>'], 'dni.pdf', { type: 'application/pdf' });

    const resultado = await servicio.validar(archivo, { extensiones: ['.pdf'] });

    expect(resultado).toEqual({
      valido: false,
      error: 'El contenido del archivo no corresponde a su extensión.',
    });
  });

  it('rechaza un MIME incompatible aunque la firma sea válida', async () => {
    const archivo = new File(
      [new Uint8Array([0x89, 0x50, 0x4e, 0x47])],
      'foto.png',
      { type: 'text/plain' },
    );

    const resultado = await servicio.validar(archivo, { extensiones: ['.png'] });

    expect(resultado).toEqual({
      valido: false,
      error: 'El tipo de contenido no coincide con la extensión del archivo.',
    });
  });

  it('convierte a base64 solo cuando el endpoint congelado lo necesita', async () => {
    const archivo = new File(
      [new Uint8Array([0x25, 0x50, 0x44, 0x46])],
      'documento.pdf',
      { type: 'application/pdf' },
    );

    expect(await servicio.base64(archivo)).toBe('JVBERg==');
  });
});
