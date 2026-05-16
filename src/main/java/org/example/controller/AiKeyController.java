package org.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiKeyController {

    private final String AI_API_KEY = "pat_kEyy2Exmzk0ZRXTCXuLrwiVbw5kDGG2Gg32hktDOMcEPAkFhVdVI4kztBRbZtcIh";
    @GetMapping("/ai-key")
    public Map<String, String> getAiKey() {
        Map<String, String> result = new HashMap<>();
        result.put("aiKey", AI_API_KEY);
        return result;
    }
}