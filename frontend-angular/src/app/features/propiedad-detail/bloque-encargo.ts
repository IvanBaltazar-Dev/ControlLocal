import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../core/api/api.types';
import { EncargosService, Publicacion } from '../../core/api/encargos.service';
import { EncargoPropiedad, HitoEncargo } from '../../core/api/propiedades.service';
import { fechaCorta, monto, SIN_DATO, texto } from '../../core/formato';
import { EditorPublicacion } from '../../shared/publicaciones/editor-publicacion';

/**
 * **Un encargo: la relación comercial, con su precio, su histórico y sus
 * anuncios.**
 *
 * Existe como componente propio para que el bloque sea **uno**: el mismo que
 * pinta el encargo vigente pinta el cerrado. Con el marcado duplicado —una
 * copia para los vivos y otra para la historia— las dos empiezan a separarse en
 * el primer cambio, y la copia de la historia es justo la que nadie mira.
 *
 * ## La publicación se gestiona desde aquí, y no desde la cabecera
 *
 * No existe un botón «Publicar propiedad»: con venta y alquiler simultáneos no
 * diría qué se está publicando. La entrada vive **dentro del bloque de su
 * encargo**, que es lo único que sabe si el importe es un precio de venta o una
 * renta mensual.
 *
 * ## Lo que este componente no decide
 *
 * Nada. Ni cómo se llama la operación, ni cómo se llama el importe, ni cómo se
 * llama el estado, ni cómo se llama un hito, ni **si se puede publicar** — eso
 * llega como capacidad (`publicacionGestionable`) desde BROX Core, con su motivo
 * cuando no se puede. Aquí sólo se ponen comas, puntos y jerarquía visual
 * (D-A-1 §5).
 *
 * ## Y el histórico es el suyo
 *
 * `encargo.historico` viene filtrado por encargo, no por operación. Es la
 * diferencia que aparece en cuanto hay historia: tres alquileres sucesivos
 * comparten la operación, y filtrar por ella le daría al de 2026 los precios de
 * 2024.
 */
@Component({
  selector: 'cl-bloque-encargo',
  imports: [EditorPublicacion],
  templateUrl: './bloque-encargo.html',
  styleUrl: './bloque-encargo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BloqueEncargo {
  private readonly encargos = inject(EncargosService);

  readonly encargo = input.required<EncargoPropiedad>();
  /** Sólo el AGENTE escribe. Es el mismo gate que el backend impone. */
  readonly puedeEditar = input(false);

  /** La ficha vuelve a leerse: los anuncios cambian el histórico económico. */
  readonly cambio = output<void>();

  protected readonly SIN_DATO = SIN_DATO;

  protected readonly editorAbierto = signal(false);
  protected readonly enEdicion = signal<Publicacion | null>(null);
  protected readonly cambiandoEstado = signal<number | null>(null);
  protected readonly errorAccion = signal<string | null>(null);

  /**
   * Los anuncios que llegaron con la ficha, o los recargados tras un cambio.
   *
   * Se recargan sólo este encargo y no la ficha entera: es lo único que se ha
   * movido, y volver a pedirlo todo haría parpadear tres bloques por editar uno.
   */
  protected readonly anuncios = signal<readonly Publicacion[] | null>(null);

  protected readonly publicaciones = computed(
    () => this.anuncios() ?? this.encargo().publicaciones ?? [],
  );

  /** Cuántos anuncios están efectivamente publicados. */
  protected readonly publicados = computed(
    () => this.publicaciones().filter((anuncio) => anuncio.estado === 'P').length,
  );

  protected readonly gestionable = computed(
    () => this.puedeEditar() && (this.encargo().publicacionGestionable?.permitida ?? false),
  );

  /** El motivo lo da el Core; aquí sólo se enseña cuando hay algo que decir. */
  protected readonly motivoNoPublicable = computed(() => {
    const gestion = this.encargo().publicacionGestionable;
    return gestion && !gestion.permitida ? (gestion.motivo ?? null) : null;
  });

  protected importe(): string {
    const actual = this.encargo();
    return monto(actual.importe, actual.moneda);
  }

  protected hitoMonto(hito: HitoEncargo): string {
    return monto(hito.monto, hito.moneda);
  }

  protected anuncioMonto(anuncio: Publicacion): string {
    return monto(anuncio.importePublicado, anuncio.moneda);
  }

  protected fecha(valor: string | null | undefined): string {
    return fechaCorta(valor);
  }

  protected valor(valor: string | null | undefined): string {
    return texto(valor);
  }

  /**
   * La vigencia en una línea. Sin fecha de inicio no se escribe nada: un rango
   * a medias («hasta el 3 de marzo») se lee como si empezara hoy.
   */
  protected vigencia(): string {
    const { inicio, fin } = this.encargo();
    if (!inicio) {
      return SIN_DATO;
    }
    return fin ? `${fechaCorta(inicio)} — ${fechaCorta(fin)}` : `Desde ${fechaCorta(inicio)}`;
  }

  /**
   * La exclusividad tiene **tres** estados y no dos: sí, no, y «no se pactó».
   * Pintar el tercero como «No» afirmaría algo que nadie escribió.
   */
  protected exclusividad(): string {
    const valor = this.encargo().exclusividad;
    if (valor === null || valor === undefined) {
      return SIN_DATO;
    }
    return valor ? 'Sí' : 'No';
  }

  // ------------------------------------------------------------------
  // Publicación
  // ------------------------------------------------------------------

  protected nuevoAnuncio(): void {
    this.enEdicion.set(null);
    this.editorAbierto.set(true);
  }

  protected editarAnuncio(anuncio: Publicacion): void {
    this.enEdicion.set(anuncio);
    this.editorAbierto.set(true);
  }

  protected cerrarEditor(): void {
    this.editorAbierto.set(false);
  }

  protected async anuncioGuardado(): Promise<void> {
    this.editorAbierto.set(false);
    await this.recargar();
  }

  /** Publicar (`P`), pausar (`S`) o cerrar (`C`) el anuncio. */
  protected async cambiarEstado(anuncio: Publicacion, estado: string): Promise<void> {
    if (this.cambiandoEstado() !== null) {
      return;
    }
    this.cambiandoEstado.set(anuncio.id);
    this.errorAccion.set(null);
    try {
      await this.encargos.cambiarEstadoPublicacion(this.encargo().idEncargo, anuncio.id, estado);
      await this.recargar();
    } catch (error) {
      this.errorAccion.set(
        error instanceof ApiError ? error.message : 'No se pudo cambiar el estado del anuncio.',
      );
    } finally {
      this.cambiandoEstado.set(null);
    }
  }

  private async recargar(): Promise<void> {
    try {
      this.anuncios.set(await this.encargos.publicaciones(this.encargo().idEncargo));
      // Publicar escribe un hito en el histórico económico del encargo, así que
      // la ficha que lo pinta se quedó vieja.
      this.cambio.emit();
    } catch (error) {
      // Se conserva la lista visible: el cambio ya se aplicó en el servidor y
      // vaciarla haría creer que se perdió.
      this.errorAccion.set(
        error instanceof ApiError ? error.message : 'No se pudo actualizar la lista de anuncios.',
      );
    }
  }
}
