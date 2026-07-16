package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 多模态文档解析：图片 / 音频 / 视频 → 文本
 *
 * 策略（混合）：在线 DashScope 优先（中文 OCR/ASR 质量好、即开即用），
 * 失败或离线时回退本地 Tesseract（图片）+ ffmpeg抽音轨 + faster-whisper（音视频）。
 */
@Slf4j
@Service
public class MultimodalExtractor {

    @Value("${app.dashscope.api-key:}")
    private String apiKey;

    @Value("${app.dashscope.vision-model:qwen-vl-max}")
    private String visionModel;

    @Value("${app.dashscope.asr-model:paraformer-realtime-v2}")
    private String asrModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient restClient;

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    // fileType 仅为扩展名（无点），如 "png" / "mp3" / "mp4"
    private static final java.util.List<String> IMAGE_EXT = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final java.util.List<String> AUDIO_EXT = List.of("mp3", "wav", "m4a", "flac", "aac", "ogg");
    private static final java.util.List<String> VIDEO_EXT = List.of("mp4", "mov", "avi", "mkv", "webm", "flv");

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 入口：根据文件类型分派解析
     * @return 提取出的纯文本（图片OCR文字 / 表格Markdown / 音视频转写文本）
     */
    public String extract(Path filePath, String fileType) {
        if (fileType == null) fileType = "";
        String lower = fileType.toLowerCase();
        boolean isImage = IMAGE_EXT.contains(lower) || "image".equalsIgnoreCase(fileType);
        boolean isAudio = AUDIO_EXT.contains(lower) || "audio".equalsIgnoreCase(fileType);
        boolean isVideo = VIDEO_EXT.contains(lower) || "video".equalsIgnoreCase(fileType);

        try {
            if (isImage) return extractImage(filePath);
            if (isAudio) return extractAudio(filePath);
            if (isVideo) return extractAudio(extractAudioFromVideo(filePath));
            throw new BusinessException("不支持的多模态文件类型: " + fileType);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("多模态解析失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    // 图片：在线视觉模型优先，本地 Tesseract 兜底
    // ═══════════════════════════════════════════
    private String extractImage(Path imagePath) throws Exception {
        try {
            return extractImageOnline(imagePath);
        } catch (Exception e) {
            log.warn("在线图片OCR失败，回退本地 Tesseract: {}", e.getMessage());
            return extractImageLocal(imagePath);
        }
    }

    private String extractImageOnline(Path imagePath) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        String mime = detectMime(imagePath);
        String dataUri = "data:" + mime + ";base64," + base64;

        String system = "你是OCR与表格识别助手。请提取图片中的全部文字；" +
                "若图片含表格，请还原为 Markdown 表格（保留表头与数据）。只输出提取内容，不要解释。";
        Map<String, Object> userContent = Map.of(
                "type", "text", "text", "请识别这张图片的内容");
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUri));
        Map<String, Object> body = Map.of(
                "model", visionModel,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", List.of(userContent, imageContent))),
                "max_tokens", 2048);

        String resp = restClient.post().uri("/chat/completions")
                .body(body).retrieve().body(String.class);
        JsonNode root = objectMapper.readTree(resp);
        return root.path("choices").get(0).path("message").path("content").asText("").trim();
    }

    private String extractImageLocal(Path imagePath) throws Exception {
        Path out = Files.createTempFile("tess_", ".txt");
        ProcessBuilder pb = new ProcessBuilder("tesseract", imagePath.toString(),
                out.toString().replace(".txt", ""), "-l", "chi_sim+eng");
        pb.redirectErrorStream(true);
        int code = pb.start().waitFor();
        if (code != 0) throw new BusinessException("Tesseract 解析失败");
        return Files.readString(out).trim();
    }

    // ═══════════════════════════════════════════
    // 音频/视频：在线 ASR 优先，本地 whisper 兜底
    // ═══════════════════════════════════════════
    private String extractAudio(Path audioPath) throws Exception {
        try {
            return extractAudioOnline(audioPath);
        } catch (Exception e) {
            log.warn("在线ASR失败，回退本地 whisper: {}", e.getMessage());
            return extractAudioLocal(audioPath);
        }
    }

    private String extractAudioOnline(Path audioPath) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", asrModel);
        form.add("file", new FileSystemResource(audioPath.toFile()));

        String resp = restClient.post().uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form).retrieve().body(String.class);

        JsonNode root = objectMapper.readTree(resp);
        // 兼容模式返回 {text:"..."} 或 {choices:[{text:"..."}]}
        if (root.has("text")) return root.path("text").asText("").trim();
        if (root.has("choices") && root.path("choices").size() > 0)
            return root.path("choices").get(0).path("text").asText("").trim();
        throw new BusinessException("ASR 返回无法解析: " + resp);
    }

    private String extractAudioLocal(Path audioPath) throws Exception {
        // 抽成 16k 单声道 wav 供 whisper
        Path wav = Files.createTempFile("whisper_", ".wav");
        runProcess(List.of("ffmpeg", "-y", "-i", audioPath.toString(),
                "-vn", "-acodec", "pcm_s16le", "-ar", "16000", "-ac", "1",
                wav.toString()));
        // 调用本地 faster-whisper（managed venv python）
        String python = System.getProperty("os.name").toLowerCase().contains("mac")
                ? "/Users/beiluo/.workbuddy/binaries/python/envs/default/bin/python"
                : "python3";
        Path script = writeWhisperScript();
        ProcessBuilder pb = new ProcessBuilder(python, script.toString(), wav.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new BusinessException("本地 whisper 失败: " + out);
        return out.trim();
    }

    /** 视频抽音轨，返回音频文件 */
    private Path extractAudioFromVideo(Path videoPath) throws Exception {
        Path audio = Files.createTempFile("video_audio_", ".wav");
        runProcess(List.of("ffmpeg", "-y", "-i", videoPath.toString(),
                "-vn", "-acodec", "pcm_s16le", "-ar", "16000", "-ac", "1",
                audio.toString()));
        return audio;
    }

    // ═══════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════
    private void runProcess(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new BusinessException("命令执行失败: " + cmd.get(0) + " -> " + out);
    }

    private String detectMime(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private Path writeWhisperScript() throws IOException {
        Path script = Files.createTempFile("whisper_run_", ".py");
        String code = "import sys, json\n" +
                "from faster_whisper import WhisperModel\n" +
                "model = WhisperModel('base', device='cpu', compute_type='int8')\n" +
                "segs, _ = model.transcribe(sys.argv[1], language='zh')\n" +
                "print('\\n'.join(s.text for s in segs))\n";
        Files.writeString(script, code);
        return script;
    }
}
