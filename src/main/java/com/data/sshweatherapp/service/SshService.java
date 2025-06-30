package com.data.sshweatherapp.service;

import com.data.sshweatherapp.model.WeatherData;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SshService {

    @Value("${ssh.host}")
    private String host;

    @Value("${ssh.user}")
    private String user;

    @Value("${ssh.password}")
    private String password;

    @Value("${ssh.remoteFile}")
    private String remoteFile;

    private String ajustarHora(String horaOriginal) {
        try {
            LocalTime hora = LocalTime.parse(horaOriginal);
            LocalTime horaAjustada = hora.minusHours(6);
            return horaAjustada.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return horaOriginal;
        }
    }

    private boolean esCada15Minutos(String hora) {
        try {
            LocalTime t = LocalTime.parse(hora);
            int minuto = t.getMinute();
            return minuto % 15 == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String convertirAqiATexto(int aqiNumerico) {
        if (aqiNumerico <= 50) {
            return "Bueno";
        } else if (aqiNumerico <= 100) {
            return "Regular";
        } else {
            return "Malo";
        }
    }

    public WeatherData getUltimoDato() {
        WeatherData dato = new WeatherData();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            String cmdArchivo = "ls " + remoteFile + "/*.csv | sort -V";
            channel.setCommand(cmdArchivo);
            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            List<String> archivos = new ArrayList<>();
            String linea;
            while ((linea = reader.readLine()) != null) {
                archivos.add(linea.trim());
            }
            channel.disconnect();

            Collections.reverse(archivos);
            for (String archivoReciente : archivos) {
                channel = (ChannelExec) session.openChannel("exec");
                String cmdTail = "tail -n 1 " + archivoReciente;
                channel.setCommand(cmdTail);
                InputStream in2 = channel.getInputStream();
                channel.connect();

                BufferedReader reader2 = new BufferedReader(new InputStreamReader(in2));
                String ultimaLinea = reader2.readLine();
                channel.disconnect();

                if (ultimaLinea != null && !ultimaLinea.trim().isEmpty() && !ultimaLinea.startsWith("DATE")) {
                    String[] partes = ultimaLinea.trim().split(",");
                    if (partes.length >= 7) {
                        String horaAjustada = ajustarHora(partes[1]);
                        if (esCada15Minutos(horaAjustada)) {
                            dato.setFecha(partes[0]);
                            dato.setHora(horaAjustada);
                            dato.setTemperatura(Double.parseDouble(partes[2]));
                            dato.setPresion(Double.parseDouble(partes[3]));
                            dato.setHumedad(Double.parseDouble(partes[4]));
                            dato.setAqi(Integer.parseInt(partes[5]));
                            dato.setViento(Double.parseDouble(partes[6]));
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return dato;
    }

    public List<WeatherData> getDatosMesActual() {
        return getDatosPorRango(LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    public List<WeatherData> getDatosPorRango(LocalDate fechaInicio, LocalDate fechaFin) {
        List<WeatherData> datosRango = new ArrayList<>();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            String cmdList = "ls " + remoteFile + "/*.csv";
            channel.setCommand(cmdList);
            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            List<String> archivos = new ArrayList<>();
            String archivo;
            while ((archivo = reader.readLine()) != null) {
                archivos.add(archivo.trim());
            }
            channel.disconnect();

            for (String archivoPath : archivos) {
                String nombreArchivo = archivoPath.substring(archivoPath.lastIndexOf("/") + 1);
                String diaStr = nombreArchivo.replace(".csv", "");
                int dia = Integer.parseInt(diaStr);
                LocalDate fechaArchivo = LocalDate.of(fechaInicio.getYear(), fechaInicio.getMonth(), dia);

                if ((fechaArchivo.isEqual(fechaInicio) || fechaArchivo.isAfter(fechaInicio)) &&
                        (fechaArchivo.isEqual(fechaFin) || fechaArchivo.isBefore(fechaFin))) {

                    channel = (ChannelExec) session.openChannel("exec");
                    String cmdCat = "tail -n +2 " + archivoPath;
                    channel.setCommand(cmdCat);
                    InputStream in2 = channel.getInputStream();
                    channel.connect();

                    BufferedReader reader2 = new BufferedReader(new InputStreamReader(in2));
                    String linea;
                    while ((linea = reader2.readLine()) != null) {
                        if (!linea.trim().isEmpty()) {
                            String[] partes = linea.trim().split(",");
                            if (partes.length >= 7) {
                                String horaAjustada = ajustarHora(partes[1]);
                                if (esCada15Minutos(horaAjustada)) {
                                    WeatherData dato = new WeatherData();
                                    dato.setFecha(partes[0]);
                                    dato.setHora(horaAjustada);
                                    dato.setTemperatura(Double.parseDouble(partes[2]));
                                    dato.setPresion(Double.parseDouble(partes[3]));
                                    dato.setHumedad(Double.parseDouble(partes[4]));
                                    dato.setAqi(Integer.parseInt(partes[5]));
                                    dato.setViento(Double.parseDouble(partes[6]));
                                    datosRango.add(dato);
                                }
                            }
                        }
                    }
                    channel.disconnect();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return datosRango;
    }

    public void keepAlive() {
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("echo ping");
            channel.connect(3000);

            System.out.println("Ping SSH exitoso");

        } catch (Exception e) {
            System.err.println("Ping SSH falló: " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
