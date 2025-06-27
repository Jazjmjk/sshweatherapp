package com.data.sshweatherapp.controller;

import com.data.sshweatherapp.model.WeatherData;
import com.data.sshweatherapp.service.SshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class WeatherController {

    @Autowired
    private SshService sshService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/")
    public String showPrincipal(Model model) throws Exception {
        WeatherData ultimo = sshService.getUltimoDato();
        model.addAttribute("ultimo", ultimo);

        model.addAttribute("aqiTexto", sshService.convertirAqiATexto(ultimo.getAqi()));

        List<WeatherData> datosMes = sshService.getDatosMesActual();
        model.addAttribute("datosMes", datosMes);

        String datosMesJson = objectMapper.writeValueAsString(datosMes).replaceAll("</", "<\\/");
        model.addAttribute("datosMesJson", datosMesJson);

        if (!datosMes.isEmpty()) {
            model.addAttribute("fechaMin", datosMes.get(0).getFecha());
            model.addAttribute("fechaMax", datosMes.get(datosMes.size() - 1).getFecha());
        }

        boolean activo = false;
        if (ultimo.getFecha() != null && ultimo.getHora() != null) {
            try {
                String fechaHoraStr = ultimo.getFecha() + " " + ultimo.getHora();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm");
                LocalDateTime fechaHoraDato = LocalDateTime.parse(fechaHoraStr, formatter);
                activo = fechaHoraDato.isAfter(LocalDateTime.now().minusMinutes(30));
            } catch (Exception e) {
                activo = false;
            }
        }
        model.addAttribute("activo", activo);

        return "principal";
    }

    @GetMapping("/descargarCSV")
    @ResponseBody
    public ResponseEntity<byte[]> descargarCSV(
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        List<WeatherData> datos = sshService.getDatosPorRango(fechaInicio, fechaFin);

        StringBuilder csv = new StringBuilder("Fecha,Hora,Temperatura,Presion,Humedad,AQI,Viento\n");
        for (WeatherData dato : datos) {
            csv.append(String.format("%s,%s,%.2f,%.4f,%.2f,%d,%.3f\n",
                    dato.getFecha(),
                    dato.getHora(),
                    dato.getTemperatura(),
                    dato.getPresion(),
                    dato.getHumedad(),
                    dato.getAqi(),
                    dato.getViento()));
        }

        byte[] contenido = csv.toString().getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=datos_estacion.csv");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .headers(headers)
                .body(contenido);
    }
}
