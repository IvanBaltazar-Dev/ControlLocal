import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';

import {
  CANAL_CONTACTO,
  CANAL_PUBLICACION,
  Catalogo,
  ESTADO_CAPTACION,
  ESTADO_COMISION,
  ESTADO_CONTRATO,
  ESTADO_DOCUMENTO,
  ESTADO_LOCAL,
  ESTADO_OPORTUNIDAD,
  ESTADO_PROSPECCION,
  ESTADO_PUBLICACION,
  ESTADO_REQUERIMIENTO,
  ESTADO_SOLICITUD,
  ESTADO_VISITA,
  FORMA_PAGO,
  HITO_PRECIO,
  MOTIVO_NO_CONTINUIDAD,
  OBJECION_VISITA,
  RESULTADO_EVALUACION,
  RESULTADO_PROPUESTA,
  TIPO_DOCUMENTO,
  TIPO_DOCUMENTO_SOLICITUD,
  TIPO_INMUEBLE,
  TIPO_INMUEBLE_COMERCIAL,
  TIPO_PERSONA,
} from '../../core/api/codigos';

interface Grupo {
  readonly clave: string;
  readonly titulo: string;
  readonly donde: string;
  readonly catalogos: readonly { readonly titulo: string; readonly valores: Catalogo }[];
}

/**
 * Los catálogos agrupados por dónde se usan, no por orden alfabético: quien
 * abre esta pantalla viene de una pantalla concreta y busca "qué significa esta
 * letra aquí".
 */
const GRUPOS: readonly Grupo[] = [
  {
    clave: 'identidad',
    titulo: 'Personas',
    donde: 'Propietarios, clientes, agentes y brokers',
    catalogos: [
      { titulo: 'Tipo de persona', valores: TIPO_PERSONA },
      { titulo: 'Tipo de documento', valores: TIPO_DOCUMENTO },
    ],
  },
  {
    clave: 'oferta',
    titulo: 'Oferta',
    donde: 'Locales, precios y publicaciones',
    catalogos: [
      { titulo: 'Disponibilidad del local', valores: ESTADO_LOCAL },
      { titulo: 'Tipo de inmueble', valores: TIPO_INMUEBLE },
      { titulo: 'Hito de precio', valores: HITO_PRECIO },
      { titulo: 'Estado de publicación', valores: ESTADO_PUBLICACION },
      { titulo: 'Canal de publicación', valores: CANAL_PUBLICACION },
    ],
  },
  {
    clave: 'proceso',
    titulo: 'Proceso',
    donde: 'Prospecciones y captaciones',
    catalogos: [
      { titulo: 'Estado de prospección', valores: ESTADO_PROSPECCION },
      { titulo: 'Resultado de la propuesta', valores: RESULTADO_PROPUESTA },
      { titulo: 'Estado de captación', valores: ESTADO_CAPTACION },
    ],
  },
  {
    clave: 'demanda',
    titulo: 'Demanda',
    donde: 'Requerimientos, oportunidades, visitas e interacciones',
    catalogos: [
      { titulo: 'Estado de requerimiento', valores: ESTADO_REQUERIMIENTO },
      { titulo: 'Tipo de inmueble buscado', valores: TIPO_INMUEBLE_COMERCIAL },
      { titulo: 'Estado de oportunidad', valores: ESTADO_OPORTUNIDAD },
      { titulo: 'Estado de visita', valores: ESTADO_VISITA },
      { titulo: 'Objeción de visita', valores: OBJECION_VISITA },
      { titulo: 'Canal de contacto', valores: CANAL_CONTACTO },
      { titulo: 'Motivo de no continuidad', valores: MOTIVO_NO_CONTINUIDAD },
    ],
  },
  {
    clave: 'cierre',
    titulo: 'Cierre',
    donde: 'Solicitudes, documentos, evaluación, contratos y comisiones',
    catalogos: [
      { titulo: 'Estado de solicitud', valores: ESTADO_SOLICITUD },
      { titulo: 'Tipo de documento requerido', valores: TIPO_DOCUMENTO_SOLICITUD },
      { titulo: 'Estado de documento', valores: ESTADO_DOCUMENTO },
      { titulo: 'Resultado de evaluación', valores: RESULTADO_EVALUACION },
      { titulo: 'Estado de contrato', valores: ESTADO_CONTRATO },
      { titulo: 'Estado de comisión', valores: ESTADO_COMISION },
      { titulo: 'Forma de pago', valores: FORMA_PAGO },
    ],
  },
];

/**
 * Catálogos del sistema: qué significa cada código del cable y dónde se usa.
 *
 * **Se genera de `core/api/codigos.ts`, que es de donde los leen las demás
 * pantallas.** El Blazor tenía esta misma tabla escrita a mano en el propio
 * componente, así que podía —y llegó a— desviarse de lo que el sistema
 * realmente usaba. Aquí no puede: si un catálogo cambia, esta pantalla cambia
 * con él, porque es la misma fuente.
 *
 * Es de **solo consulta** para los tres roles: no hay endpoint que edite estos
 * valores y no debería haberlo mientras sean códigos del contrato congelado —
 * cambiarlos no es configurar, es cambiar el significado de datos ya grabados.
 */
@Component({
  selector: 'cl-catalogos',
  imports: [],
  templateUrl: './catalogos.html',
  styleUrl: './catalogos.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Catalogos {
  protected readonly grupos = GRUPOS;
  protected readonly activo = signal<string>(GRUPOS[0].clave);

  protected readonly grupoActivo = computed(
    () => this.grupos.find((g) => g.clave === this.activo()) ?? this.grupos[0],
  );

  protected readonly totalCodigos = computed(() =>
    this.grupos.reduce(
      (total, grupo) =>
        total + grupo.catalogos.reduce((n, c) => n + Object.keys(c.valores).length, 0),
      0,
    ),
  );

  protected abrir(clave: string): void {
    this.activo.set(clave);
  }

  protected entradas(valores: Catalogo): { codigo: string; descripcion: string }[] {
    return Object.entries(valores).map(([codigo, descripcion]) => ({ codigo, descripcion }));
  }
}
