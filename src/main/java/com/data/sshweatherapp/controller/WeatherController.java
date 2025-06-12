package com.data.sshweatherapp.controller;

import com.data.sshweatherapp.model.WeatherData;
import com.data.sshweatherapp.service.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class WeatherController {

    @Autowired
    private SshService sshService;

    @GetMapping("/")
    public String showWeather(Model model) {
        Map<String, Map<String, List<WeatherData>>> datosAgrupados = sshService.getWeatherDataGroupedByMonthAndDay();
        model.addAttribute("datosAgrupados", datosAgrupados);
        return "weather";
    }
}