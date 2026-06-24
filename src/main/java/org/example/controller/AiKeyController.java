package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiKeyController {

    @Value("${coze.api.key}")
    private String aiApiKey;

    @GetMapping("/ai-key")
    public Map<String, String> getAiKey() {
        Map<String, String> result = new HashMap<>();
        result.put("aiKey", aiApiKey);
        return result;
    }
}