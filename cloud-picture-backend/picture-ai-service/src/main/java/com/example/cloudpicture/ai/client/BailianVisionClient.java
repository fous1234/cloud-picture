package com.example.cloudpicture.ai.client;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 阿里云百炼 qwen3-vl-flash 兼容模式客户端：图片以 Base64 内联传入，
 * 不自动重试；超时、限流、额度耗尽、非成功响应统一映射为 AI 暂不可用，
 * 日志只记录状态、耗时与脱敏错误类型，不记录密钥、请求体或图片内容
 */
@Slf4j
@Component
public class BailianVisionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHAT_PATH = "/chat/completions";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;

    public BailianVisionClient(
            @Value("${picture.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${picture.ai.api-key:}") String apiKey,
            @Value("${picture.ai.model:qwen3-vl-flash}") String model,
            @Value("${picture.ai.timeout-seconds:60}") long timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** 返回模型原始文本内容；任何失败都抛 AI_UNAVAILABLE */
    public String complete(String imageDataUrl, String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("AI 调用跳过: 未配置 DASHSCOPE_API_KEY");
            throw unavailable();
        }
        long start = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + CHAT_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(imageDataUrl, prompt)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long costMs = System.currentTimeMillis() - start;
            if (response.statusCode() != 200) {
                log.warn("AI 调用失败: status={}, errorType={}, costMs={}",
                        response.statusCode(), errorType(response.body()), costMs);
                throw unavailable();
            }
            log.info("AI 调用完成: status={}, costMs={}", response.statusCode(), costMs);
            return extractContent(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 调用被中断: costMs={}", System.currentTimeMillis() - start);
            throw unavailable();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 调用异常: type={}, costMs={}", e.getClass().getSimpleName(), System.currentTimeMillis() - start);
            throw unavailable();
        }
    }

    private String buildRequestBody(String imageDataUrl, String prompt) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        ObjectNode message = root.putArray("messages").addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "image_url").putObject("image_url").put("url", imageDataUrl);
        content.addObject().put("type", "text").put("text", prompt);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private String extractContent(String body) {
        try {
            JsonNode content = MAPPER.readTree(body).path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
        } catch (Exception ignored) {
            // 无法解析响应体，按不可用处理
        }
        throw unavailable();
    }

    /** 只取供应商错误码作为脱敏错误类型，不记录原始响应体 */
    private String errorType(String body) {
        try {
            String code = MAPPER.readTree(body).path("error").path("code").asText("");
            return code.isBlank() ? "unknown" : code;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
}