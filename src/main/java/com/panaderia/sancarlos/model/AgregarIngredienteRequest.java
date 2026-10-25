package com.panaderia.sancarlos.model;

public class AgregarIngredienteRequest {

    private int idInsumo;
    private double cantidad;

    public AgregarIngredienteRequest() {
    }

    public int getIdInsumo() {
        return idInsumo;
    }

    public void setIdInsumo(int idInsumo) {
        this.idInsumo = idInsumo;
    }

    public double getCantidad() {
        return cantidad;
    }

    public void setCantidad(double cantidad) {
        this.cantidad = cantidad;
    }
}
