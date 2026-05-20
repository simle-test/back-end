package org.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiKeyController {

    // 从环境变量读取密钥，代码里不再暴露真实密钥
    private final String AI_API_KEY = System.getenv("COZE_API_KEY");

    @GetMapping("/ai-key")
    public Map<String, String> getAiKey() {
        Map<String, String> result = new HashMap<>();
        result.put("aiKey", AI_API_KEY == null ? "" : AI_API_KEY);
        return result;
    }
}