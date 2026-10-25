package com.panaderia.sancarlos.model;

import java.sql.Timestamp;

public class MovimientoInventario {

    private int idMovimiento;
    private String producto;
    private String tipo;
    private double cantidad;
    private double saldoAnterior;
    private double saldoNuevo;
    private Timestamp fecha;

    public MovimientoInventario() {
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public double getCantidad() {
        return cantidad;
    }

    public void setCantidad(double cantidad) {
        this.cantidad = cantidad;
    }

    public double getSaldoAnterior() {
        return saldoAnterior;
    }

    public void setSaldoAnterior(double saldoAnterior) {
        this.saldoAnterior = saldoAnterior;
    }

    public double getSaldoNuevo() {
        return saldoNuevo;
    }

    public void setSaldoNuevo(double saldoNuevo) {
        this.saldoNuevo = saldoNuevo;
    }

    public Timestamp getFecha() {
        return fecha;
    }

    public void setFecha(Timestamp fecha) {
        this.fecha = fecha;
    }
}