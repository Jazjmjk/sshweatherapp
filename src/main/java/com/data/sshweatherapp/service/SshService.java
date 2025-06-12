package com.data.sshweatherapp.service;

import com.data.sshweatherapp.model.WeatherData;
import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SshService {

    @Value("${ssh.user}")
    private String user;

    @Value("${ssh.host}")
    private String host;

    @Value("${ssh.password}")
    private String password;

    @Value("${ssh.filepath}")
    private String filePath;

    public Map<String, Map<String, List<WeatherData>>> getWeatherDataGroupedByMonthAndDay() {
        Map<String, Map<String, List<WeatherData>>> groupedData = new TreeMap<>(Collections.reverseOrder());
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand("cat " + filePath + "*.dat");
            channelExec.setInputStream(null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String line;
            DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM-yyyy");

            while ((line = reader.readLine()) != null) {
                String[] partes = line.trim().split("\\s+");
                if (partes.length >= 4) {
                    try {
                        String fechaStr = partes[0] + " " + partes[1];
                        LocalDateTime fecha = LocalDateTime.parse(fechaStr, fullFormatter).minusHours(6);
                        if (fecha.getMinute() % 15 != 0) continue;

                        String fechaAjustada = fecha.format(fullFormatter);
                        String dia = fecha.format(dayFormatter);
                        String mes = fecha.format(monthFormatter);

                        int temp = (int) Math.round(Double.parseDouble(partes[2]));
                        int presion = (int) Math.round(Double.parseDouble(partes[3]));

                        WeatherData data = new WeatherData(String.valueOf(temp), String.valueOf(presion), fechaAjustada);
                        groupedData.computeIfAbsent(mes, m -> new TreeMap<>(Collections.reverseOrder()))
                                .computeIfAbsent(dia, d -> new ArrayList<>())
                                .add(data);

                    } catch (Exception e) {
                        System.out.println("Error procesando línea: " + line);
                    }
                }
            }

            channelExec.disconnect();
            session.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return groupedData;
    }
}