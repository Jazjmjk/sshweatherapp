package com.data.sshweatherapp.model;

public class WeatherData {
    private String temperatura;
    private String presion;
    private String fecha;

    public WeatherData(String temperatura, String presion, String fecha) {
        this.temperatura = temperatura;
        this.presion = presion;
        this.fecha = fecha;
    }

    public String getTemperatura() {
        return temperatura;
    }

    public String getPresion() {
        return presion;
    }

    public String getFecha() {
        return fecha;
    }

    public String getEstado() {
        int temp = Integer.parseInt(temperatura);
        return temp >= 25 ? "soleado" : "nublado";
    }
}
