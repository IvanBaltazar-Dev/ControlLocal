package com.controllocal.domain.matching;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** D4: metadata técnica privada; no conserva ficha, atributos ni resultados de otro tenant. */
@Entity
@Table(name = "matching_cursor")
public class CursorMatching extends EntidadDeOrganizacion {
    @Id
    @Column(name = "referencia", nullable = false, length = 43)
    private String referencia;
    @Column(name = "id_recorrido", nullable = false)
    private UUID idRecorrido;
    @Column(name = "posicion", nullable = false)
    private int posicion;

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public UUID getIdRecorrido() { return idRecorrido; }
    public void setIdRecorrido(UUID idRecorrido) { this.idRecorrido = idRecorrido; }

    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }
}

