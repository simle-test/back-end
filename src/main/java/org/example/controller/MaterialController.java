package org.example.controller;

import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/material")
public class MaterialController {

    // 直接写死，再也不读配置文件
    private static final String TXT_LOCAL_PATH = "/home/ubuntu/back-front/";

    @GetMapping("/{fileName}")
    public String getMaterial(@PathVariable String fileName) {
        try {
            Path path = Path.of(TXT_LOCAL_PATH + fileName);
            return Files.readString(path);
        } catch (Exception e) {
            e.printStackTrace();
            return "加载失败";
        }
    }
}