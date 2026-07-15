package com.example.ragkb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 静态文件服务：头像等用户上传的图片。
 * 头像文件名不可猜（UUID），且浏览器 <img> 请求不携带 Bearer Token，
 * 故该端点放行匿名访问；其余需鉴权的文件仍走各自业务接口。
 */
@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileController {

    @Value("${app.upload-dir:./data/documents}")
    private String uploadDir;

    /**
     * 公开回传用户头像图片
     */
    @GetMapping("/avatar/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        // 路径穿越防护：仅允许安全文件名
        if (!filename.matches("[a-zA-Z0-9._-]+")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Path avatarDir = resolveAvatarDir();
        Path file = avatarDir.resolve(filename).normalize();
        // 二次防护：解析后必须仍在头像目录内
        if (!file.startsWith(avatarDir.normalize())
                || !Files.exists(file)
                || !Files.isRegularFile(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String lower = filename.toLowerCase();
        String contentType;
        if (lower.endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = MediaType.IMAGE_JPEG_VALUE;
        else if (lower.endsWith(".webp")) contentType = "image/webp";
        else if (lower.endsWith(".gif")) contentType = MediaType.IMAGE_GIF_VALUE;
        else contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        Resource resource = new PathResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .body(resource);
    }

    private Path resolveAvatarDir() {
        Path base = Paths.get(uploadDir);
        if (!base.isAbsolute()) {
            String rel = uploadDir.startsWith("./") ? uploadDir.substring(2) : uploadDir;
            base = Paths.get(System.getProperty("user.home"), rel);
        }
        return base.resolve("avatars");
    }
}
