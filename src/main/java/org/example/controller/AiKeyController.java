package com.xxx.xxx.controller; // 改成你自己的包名

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiKeyController {

    // 你的 AI 密钥（填写你自己真实的密钥）
    private final String AI_API_KEY = "pat_kEyy2Exmzk0ZRXTCXuLrwiVbw5kDGG2Gg32hktDOMcEPAkFhVdVI4kztBRbZtcIh";
    @GetMapping("/ai-key")
    public Map<String, String> getAiKey() {
        Map<String, String> result = new HashMap<>();
        result.put("aiKey", AI_API_KEY);
        return result;
    }
}