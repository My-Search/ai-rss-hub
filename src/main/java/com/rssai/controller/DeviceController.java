package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.LoginDevice;
import com.rssai.model.User;
import com.rssai.security.JdbcTokenRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DeviceController {

    private final JdbcTokenRepositoryImpl tokenRepository;
    private final UserMapper userMapper;
    
    public DeviceController(JdbcTokenRepositoryImpl tokenRepository, UserMapper userMapper) {
        this.tokenRepository = tokenRepository;
        this.userMapper = userMapper;
    }

    @GetMapping("/devices")
    public String devicesPage(Authentication auth, Model model, HttpServletRequest request) {
        User user = userMapper.findByUsername(auth.getName());
        String currentSeries = extractSeriesFromCookie(request);

        List<LoginDevice> devices = tokenRepository.getUserDevices(user.getUsername());
        for (LoginDevice device : devices) {
            device.setCurrentDevice(device.getSeries().equals(currentSeries));
        }

        model.addAttribute("user", user);
        model.addAttribute("devices", devices);
        model.addAttribute("deviceCount", devices.size());
        return "devices";
    }

    @PostMapping("/devices/revoke")
    @ResponseBody
    public Map<String, Object> revokeDevice(@RequestParam String series, Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userMapper.findByUsername(auth.getName());
            List<LoginDevice> devices = tokenRepository.getUserDevices(user.getUsername());

            boolean deviceExists = devices.stream().anyMatch(d -> d.getSeries().equals(series));
            if (!deviceExists) {
                result.put("success", false);
                result.put("message", "设备不存在");
                return result;
            }

            tokenRepository.removeTokenBySeries(series);
            result.put("success", true);
            result.put("message", "设备已成功移除");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "移除设备失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/devices/revoke-all")
    @ResponseBody
    public Map<String, Object> revokeAllDevices(Authentication auth, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userMapper.findByUsername(auth.getName());
            String currentSeries = extractSeriesFromCookie(request);

            List<LoginDevice> devices = tokenRepository.getUserDevices(user.getUsername());
            for (LoginDevice device : devices) {
                if (!device.getSeries().equals(currentSeries)) {
                    tokenRepository.removeTokenBySeries(device.getSeries());
                }
            }

            result.put("success", true);
            result.put("message", "其他设备已成功移除");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "移除设备失败: " + e.getMessage());
        }
        return result;
    }

    private String extractSeriesFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("remember-me".equals(cookie.getName())) {
                    String[] parts = cookie.getValue().split(":");
                    if (parts.length >= 2) {
                        return parts[0];
                    }
                }
            }
        }
        return null;
    }
}
