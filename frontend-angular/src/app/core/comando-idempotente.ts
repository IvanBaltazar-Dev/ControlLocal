/**
 * Identidad de una OPERACIÓN económica, no de un request HTTP.
 *
 * <p>El backend deduplica los movimientos de comisión por `Idempotency-Key`
 * (índice único `uq_movimiento_idempotencia`). Para que esa garantía sirva de
 * algo, la clave tiene que durar exactamente lo que dura la operación desde el
 * punto de vista del usuario:
 *
 * - un cobro nuevo estrena clave;
 * - reintentar **ese** cobro reenvía la misma;
 * - si el usuario corrige el importe tras un error, ya es otra operación y
 *   estrena clave —de lo contrario el backend respondería 409—;
 * - un timeout deja el resultado en duda: se conserva la clave, que es
 *   justamente el caso para el que existe.
 *
 * <p><b>Por qué no vive en un interceptor.</b> Un interceptor genérico ve
 * requests, no operaciones: un retry automático generaría una clave nueva y
 * destruiría la garantía, que es el único escenario que la clave viene a
 * cubrir. Por eso la crea el comando, aquí.
 *
 * <p>El doble clic se corta antes de salir: mientras hay una petición en
 * vuelo, `enviar` devuelve **la misma promesa** en vez de lanzar otra. La UI
 * deshabilita el botón además, pero eso es comodidad; la garantía final sigue
 * siendo el índice único de la base.
 */
export class ComandoIdempotente<D, R> {
  private clave: string = nuevaClave();
  private huellaUltimoIntento: string | null = null;
  private enVuelo: Promise<R> | null = null;

  /**
   * @param ejecutar  envía el comando con la clave que le corresponde.
   * @param huella    resume los datos para detectar que el usuario los cambió.
   *                  Se pasa explícita —no `JSON.stringify`— porque el orden de
   *                  las claves de un objeto no es parte del contrato.
   */
  constructor(
    private readonly ejecutar: (datos: D, clave: string) => Promise<R>,
    private readonly huella: (datos: D) => string,
  ) {}

  /** La clave vigente. Existe para poder afirmarla en las pruebas. */
  claveActual(): string {
    return this.clave;
  }

  enviar(datos: D): Promise<R> {
    // Doble clic: la operación ya está en curso, no se lanza otra.
    if (this.enVuelo) {
      return this.enVuelo;
    }
    const huella = this.huella(datos);
    if (this.huellaUltimoIntento !== null && this.huellaUltimoIntento !== huella) {
      // Otros datos son otra operación económica. Sin esto el backend
      // respondería 409 por reutilización de clave, y con razón.
      this.clave = nuevaClave();
    }
    this.huellaUltimoIntento = huella;

    const envio = this.ejecutar(datos, this.clave)
      .then((resultado) => {
        // La operación terminó: la siguiente es otra y estrena clave.
        this.clave = nuevaClave();
        this.huellaUltimoIntento = null;
        return resultado;
      })
      .finally(() => {
        this.enVuelo = null;
      });
    this.enVuelo = envio;
    return envio;
  }
}

/**
 * `crypto.randomUUID` exige contexto seguro (https o localhost). El respaldo
 * no pretende ser criptográfico: solo tiene que ser único dentro de la sesión
 * del navegador, porque la unicidad real la impone la base.
 */
function nuevaClave(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
