package com.panaderia.sancarlos.model;

public class PlanInsumo {

    private int idInsumo;
    private String codigo;
    private String nombre;
    private String unidadMedida;
    private double cantidadNecesaria;
    private double existenciaActual;
    private double cantidadFaltante;
    private String estadoInsumo;

    public PlanInsumo() {
    }

    public int getIdInsumo() {
        return idInsumo;
    }

    public void setIdInsumo(int idInsumo) {
        this.idInsumo = idInsumo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public double getCantidadNecesaria() {
        return cantidadNecesaria;
    }

    public void setCantidadNecesaria(double cantidadNecesaria) {
        this.cantidadNecesaria = cantidadNecesaria;
    }

    public double getExistenciaActual() {
        return existenciaActual;
    }

    public void setExistenciaActual(double existenciaActual) {
        this.existenciaActual = existenciaActual;
    }

    public double getCantidadFaltante() {
        return cantidadFaltante;
    }

    public void setCantidadFaltante(double cantidadFaltante) {
        this.cantidadFaltante = cantidadFaltante;
    }

    public String getEstadoInsumo() {
        return estadoInsumo;
    }

    public void setEstadoInsumo(String estadoInsumo) {
        this.estadoInsumo = estadoInsumo;
    }

}
