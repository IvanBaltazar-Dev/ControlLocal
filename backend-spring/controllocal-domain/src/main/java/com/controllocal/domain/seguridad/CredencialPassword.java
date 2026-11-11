package com.controllocal.domain.seguridad;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Hash de una contrasena ya abandonada (Plan S0 §4.5, V31). Existe para una
 * sola cosa: impedir que "cambia tu contrasena" se resuelva volviendo a poner
 * la de siempre.
 *
 * <p>Guarda <b>hashes</b>, no contrasenas — un hash no se puede volver a leer.
 * Comprobar si una candidata se reutiliza se hace verificandola contra cada
 * hash guardado, exactamente igual que un login.
 */
@Entity
@Table(name = "credencial_password")
public class CredencialPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_password")
    private Long id;

    @Column(name = "id_cuenta_acceso", nullable = false)
    private Long idCuenta;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "algoritmo_hash", nullable = false, length = 20)
    private String algoritmoHash = "pbkdf2";

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    public Long getId() {
        return id;
    }

    public Long getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(Long idCuenta) {
        this.idCuenta = idCuenta;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public String getAlgoritmoHash() {
        return algoritmoHash;
    }

    public void setAlgoritmoHash(String algoritmoHash) {
        this.algoritmoHash = algoritmoHash;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
