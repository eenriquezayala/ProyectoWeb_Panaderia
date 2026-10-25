
package com.panaderia.sancarlos.model;

public class GenerarProduccionRequest {

    private String fecha;
    private int idUsuario;

    public GenerarProduccionRequest() {
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }
}
