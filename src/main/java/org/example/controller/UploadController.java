package org.example.controller;

import org.example.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class UploadController {

    @PostMapping("/uploadAvatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 固定路径
            String path = "D:/uploads/";
            File dir = new File(path);
            if (!dir.exists()) dir.mkdirs();

            String fileName = UUID.randomUUID() + ".jpg";
            File dest = new File(path + fileName);
            file.transferTo(dest);

            // 直接返回完整URL！！！！！！（关键）
            return Result.success("http://localhost:8080/uploads/" + fileName);
        } catch (Exception e) {
            return Result.error("上传失败");
        }
    }
}