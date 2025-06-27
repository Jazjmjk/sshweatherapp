package com.data.sshweatherapp.model;

public class WeatherData {

    private String fecha;
    private String hora;
    private double temperatura;
    private double presion;
    private double humedad;
    private int aqi;
    private double viento;

    public String getFecha() {
        return fecha;
    }
    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }
    public void setHora(String hora) {
        this.hora = hora;
    }

    public double getTemperatura() {
        return temperatura;
    }
    public void setTemperatura(double temperatura) {
        this.temperatura = temperatura;
    }

    public double getPresion() {
        return presion;
    }
    public void setPresion(double presion) {
        this.presion = presion;
    }

    public double getHumedad() {
        return humedad;
    }
    public void setHumedad(double humedad) {
        this.humedad = humedad;
    }

    public int getAqi() {
        return aqi;
    }
    public void setAqi(int aqi) {
        this.aqi = aqi;
    }

    public double getViento() {
        return viento;
    }
    public void setViento(double viento) {
        this.viento = viento;
    }
}
