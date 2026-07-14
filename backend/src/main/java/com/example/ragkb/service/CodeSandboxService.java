package com.example.ragkb.service;

import com.example.ragkb.model.dto.CodeRunRequest;
import com.example.ragkb.model.dto.CodeRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 代码沙箱服务：以独立 JVM 子进程运行用户提交的 Java 代码。
 * <p>
 * 隔离与安全防护：
 *  - 进程隔离：通过 ProcessBuilder 启动全新 java 进程执行用户代码，与主应用 JVM 完全分离；
 *  - 超时强杀：waitFor 超时后 destroyForcibly()，防止死循环/无限挂起；
 *  - 临时目录：每次执行使用独立临时目录，结束后递归清理；
 *  - 输出重定向：子进程仅向 stdout 输出一行 JSON 结果，用户 System.out 被捕获进 logs 字段，
 *    避免污染结果；编译/异常信息走 stderr 单独捕获。
 * </p>
 * <p>说明：本实现依赖本地 JDK（需 javac）。生产环境若需更强隔离（网络/文件系统限制），
 * 建议进一步引入 SecurityManager 替代方案或容器级沙箱（如 gVisor / Docker seccomp）。</p>
 */
@Service
public class CodeSandboxService {

    /** 单次执行超时（秒），防止死循环占用资源 */
    private static final long TIMEOUT_SECONDS = 15;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeRunResult run(CodeRunRequest req) {
        Path base = null;
        try {
            Path tmpRoot = Paths.get(System.getProperty("java.io.tmpdir"), "codesandbox");
            Files.createDirectories(tmpRoot);
            base = Files.createTempDirectory(tmpRoot, "run-");

            // 0) 编译缓存：相同代码直接复用已编译的 Solution.class，跳过 javac，显著提速
            String codeHash = sha256(req.getCode());
            Path cacheClass = cacheRoot().resolve(codeHash).resolve("Solution.class");
            boolean cached = Files.exists(cacheClass);
            if (cached) {
                try {
                    Files.copy(cacheClass, base.resolve("Solution.class"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    cached = false; // 复制失败则回退为重新编译
                }
            }

            // 1) 包一层 Solution 类，写出 .java
            String wrapped = "public class Solution {\n" + req.getCode() + "\n}\n";
            Path solFile = base.resolve("Solution.java");
            Files.writeString(solFile, wrapped, StandardCharsets.UTF_8);

            // 2) 写出测试用例（functionName + cases）
            String casesJson = objectMapper.writeValueAsString(Map.of(
                    "functionName", req.getFunctionName(),
                    "cases", req.getTestCases()
            ));
            Path casesFile = base.resolve("cases.json");
            Files.writeString(casesFile, casesJson, StandardCharsets.UTF_8);

            // 3) classpath：临时目录（Solution.class）+ 应用 classes 根（含 Harness）
            String cp = base + File.pathSeparator + classesRoot();
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            List<String> cmd = new ArrayList<>(Arrays.asList(
                    javaBin, "-cp", cp,
                    "com.example.ragkb.sandbox.Harness",
                    casesFile.toString(), solFile.toString()));
            if (cached) cmd.add("skip"); // 命中缓存：子进程跳过编译，直接加载 Solution.class
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(base.toFile());
            pb.redirectErrorStream(false);
            Process p = pb.start();

            // 并发读取 stdout / stderr，避免管道写满导致死锁
            String[] out = { "" }, err = { "" };
            Thread tOut = new Thread(() -> { try { out[0] = readStream(p.getInputStream()); } catch (IOException ignored) {} });
            Thread tErr = new Thread(() -> { try { err[0] = readStream(p.getErrorStream()); } catch (IOException ignored) {} });
            tOut.start(); tErr.start();

            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return errorResult("执行超时（已超过 " + TIMEOUT_SECONDS + " 秒，可能存在死循环），已强制终止。");
            }
            tOut.join(); tErr.join();

            int exit = p.exitValue();
            if (exit != 0) {
                return errorResult("沙箱进程异常退出（exit=" + exit + "）：\n" + err[0]);
            }
            // 回填编译缓存：本次编译产物供相同代码后续复用
            if (!cached && Files.exists(base.resolve("Solution.class"))) {
                try {
                    Files.createDirectories(cacheClass.getParent());
                    Files.copy(base.resolve("Solution.class"), cacheClass, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
            // 解析子进程输出的 JSON 结果
            return objectMapper.readValue(out[0], CodeRunResult.class);
        } catch (Exception e) {
            return errorResult("沙箱运行失败：" + e.getMessage());
        } finally {
            if (base != null) deleteRecursively(base);
        }
    }

    /** 定位 Harness.class 所在的应用 classes 根目录（用于子进程 classpath） */
    private String classesRoot() {
        URL url = CodeSandboxService.class.getClassLoader()
                .getResource("com/example/ragkb/sandbox/Harness.class");
        if (url != null && "file".equals(url.getProtocol())) {
            String path = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
            int idx = path.indexOf("/com/example/ragkb/sandbox/Harness.class");
            if (idx >= 0) return path.substring(0, idx);
        }
        // 兜底：开发态 target/classes
        return System.getProperty("user.dir") + "/target/classes";
    }

    /** 编译缓存根目录（跨多次运行持久，相同代码无需重复 javac） */
    private Path cacheRoot() {
        Path p = Paths.get(System.getProperty("java.io.tmpdir"), "codesandbox-cache");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    /** 计算代码内容的 SHA-256 摘要，作为编译缓存键（强哈希避免碰撞导致错类） */
    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            // 极端兜底：哈希不可用时退化为哈希码（仅影响缓存命中率，不影响正确性）
            return "fallback-" + Integer.toHexString(s.hashCode());
        }
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8);
    }

    private void deleteRecursively(Path path) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private CodeRunResult errorResult(String msg) {
        CodeRunResult r = new CodeRunResult();
        r.setSuccess(false);
        r.setError(msg);
        r.setResults(Collections.emptyList());
        r.setLogs(Collections.emptyList());
        return r;
    }
}
