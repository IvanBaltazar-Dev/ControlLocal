import { ComandoIdempotente } from './comando-idempotente';

/**
 * La clave pertenece a la OPERACIÓN económica, no al request. Estas pruebas
 * fijan justo eso, que es lo que hace útil al índice único del backend: si la
 * clave cambiara en cada envío, el `Idempotency-Key` sería decorativo.
 */
describe('ComandoIdempotente', () => {
  interface Datos {
    tipo: string;
    monto: number;
  }

  const huella = (d: Datos) => `${d.tipo}|${d.monto}`;

  /** Registra con qué clave se envió cada intento. */
  function espia(resultado: () => Promise<string>) {
    const claves: string[] = [];
    const comando = new ComandoIdempotente<Datos, string>((_, clave) => {
      claves.push(clave);
      return resultado();
    }, huella);
    return { comando, claves };
  }

  it('un comando nuevo genera una clave', async () => {
    const { comando, claves } = espia(() => Promise.resolve('ok'));

    await comando.enviar({ tipo: 'C', monto: 100 });

    expect(claves.length).toBe(1);
    expect(claves[0]).toBeTruthy();
  });

  it('el reintento del MISMO comando conserva la clave', async () => {
    // Un timeout deja el resultado en duda: es exactamente el caso para el que
    // existe la clave, y por eso el reintento tiene que reenviar la misma.
    const { comando, claves } = espia(() => Promise.reject(new Error('timeout')));

    await expectAsync(comando.enviar({ tipo: 'C', monto: 100 })).toBeRejected();
    await expectAsync(comando.enviar({ tipo: 'C', monto: 100 })).toBeRejected();

    expect(claves.length).toBe(2);
    expect(claves[0]).toBe(claves[1]);
  });

  it('cambiar los datos tras un error funcional estrena clave', async () => {
    // Corregir el importe es OTRA operación. Sin esto el backend respondería
    // 409 por reutilizar la clave con un comando distinto, y con razón.
    const { comando, claves } = espia(() => Promise.reject(new Error('400')));

    await expectAsync(comando.enviar({ tipo: 'C', monto: 100 })).toBeRejected();
    await expectAsync(comando.enviar({ tipo: 'C', monto: 250 })).toBeRejected();

    expect(claves[0]).not.toBe(claves[1]);
  });

  it('tras el exito, la siguiente operacion estrena clave', async () => {
    const { comando, claves } = espia(() => Promise.resolve('ok'));

    await comando.enviar({ tipo: 'C', monto: 100 });
    await comando.enviar({ tipo: 'C', monto: 100 });

    expect(claves.length).toBe(2);
    expect(claves[0]).not.toBe(claves[1]);
  });

  it('el doble submit no produce dos comandos independientes', async () => {
    // Dos clics mientras la petición está en vuelo: una sola salida y la MISMA
    // promesa para los dos, no dos operaciones que compitan.
    let resolver: (valor: string) => void = () => undefined;
    const { comando, claves } = espia(() => new Promise<string>((ok) => (resolver = ok)));

    const primera = comando.enviar({ tipo: 'C', monto: 100 });
    const segunda = comando.enviar({ tipo: 'C', monto: 100 });

    expect(claves.length).toBe(1);
    expect(primera).toBe(segunda);

    resolver('ok');
    await primera;
  });

  it('tras terminar el envio en vuelo, el siguiente si sale', async () => {
    const { comando, claves } = espia(() => Promise.resolve('ok'));

    await comando.enviar({ tipo: 'C', monto: 100 });
    await comando.enviar({ tipo: 'P', monto: 40 });

    expect(claves.length).toBe(2);
  });
});
