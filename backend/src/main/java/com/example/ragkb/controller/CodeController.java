package com.example.ragkb.controller;

import com.example.ragkb.model.dto.CodeRunRequest;
import com.example.ragkb.model.dto.CodeRunResult;
import com.example.ragkb.service.CodeSandboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码练习沙箱接口：以后端 JVM 子进程执行用户提交的 Java 代码。
 * 需要登录（Spring Security 默认 anyRequest().authenticated() 已覆盖 /api/code/**）。
 */
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeSandboxService codeSandboxService;

    @PostMapping("/run")
    public CodeRunResult run(@RequestBody CodeRunRequest req) {
        return codeSandboxService.run(req);
    }
}
