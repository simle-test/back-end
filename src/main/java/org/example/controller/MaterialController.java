package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/material")
public class MaterialController {

    @Value("${txt.local.path}")
    private String txtPath;

    @GetMapping("/{fileName}")
    public void getTxt(@PathVariable String fileName, HttpServletResponse response) {
        try {
            Path path = Path.of(txtPath + fileName);
            response.setContentType("text/plain;charset=UTF-8");
            byte[] bytes = Files.readAllBytes(path);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}