package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/file")
public class FileController {

    // 自动读取你配置的本地路径
    @Value("${img.local.path}")
    private String SAVE_PATH;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        try {
            File dir = new File(SAVE_PATH);
            if (!dir.exists()) dir.mkdirs();

            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = UUID.randomUUID() + suffix;

            File dest = new File(SAVE_PATH + fileName);
            file.transferTo(dest);

            // 🔥 只改这里：返回 完整线上地址，前端直接显示
            return "https://www.civilservant.cloud/file/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败";
        }
    }

    @GetMapping("/{fileName}")
    public void getImage(@PathVariable String fileName, HttpServletResponse response) {
        try {
            Path path = Path.of(SAVE_PATH + fileName);
            byte[] bytes = Files.readAllBytes(path);
            response.setContentType("image/jpeg");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}