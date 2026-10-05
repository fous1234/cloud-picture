package com.example.cloudpicture.ai.client;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 下载内部调用方传入的图片 URL 并转为模型所需的 data URL；
 * 限制读取上限、校验文件头魔数；日志不记录 URL（可能携带签名参数）
 */
@Component
public class ImageUrlDownloader {

    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String toDataUrl(String imageUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        byte[] bytes;
        try {
            // 流式限量读取，避免超大响应整体载入内存
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw unavailable();
                }
                bytes = body.readNBytes(MAX_DOWNLOAD_BYTES + 1);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception e) {
            // URI 非法、连接失败、读取异常等
            throw unavailable();
        }
        if (bytes.length == 0 || bytes.length > MAX_DOWNLOAD_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容为空或大小超出限制");
        }
        return buildDataUrl(bytes);
    }

    static String buildDataUrl(byte[] bytes) {
        String format = sniffFormat(bytes);
        if (format == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不是有效图片");
        }
        String mimeType = "image/" + ("jpg".equals(format) ? "jpeg" : format);
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** 文件头魔数嗅探格式，避免把非图片内容传给模型 */
    static String sniffFormat(byte[] bytes) {
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        return null;
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE, "图片下载失败");
    }
}
