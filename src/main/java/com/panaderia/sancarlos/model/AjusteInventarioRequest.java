package com.panaderia.sancarlos.model;

public class AjusteInventarioRequest {

    private int idProducto;
    private double nuevaExistencia;
    private int idUsuario;

    public AjusteInventarioRequest() {
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public double getNuevaExistencia() {
        return nuevaExistencia;
    }

    public void setNuevaExistencia(double nuevaExistencia) {
        this.nuevaExistencia = nuevaExistencia;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }
}