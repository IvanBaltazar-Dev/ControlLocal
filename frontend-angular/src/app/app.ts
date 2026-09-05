import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * El contenedor del `<router-outlet>`. No pinta nada: lo que se ve lo ponen el
 * cascarón (`layout/shell`) y las pantallas.
 *
 * Aquí vivía un `title = signal('controllocal-web')` del andamiaje de
 * `ng new`. No se leía en ninguna plantilla —`app.html` es una sola línea— y
 * era el último sitio del código de pantalla donde quedaba escrito el nombre
 * viejo del producto. El título de la pestaña lo pone `index.html`, y dice
 * BROX.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
