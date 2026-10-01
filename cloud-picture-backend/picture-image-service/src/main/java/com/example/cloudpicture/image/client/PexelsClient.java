package com.example.cloudpicture.image.client;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Pexels 图源客户端：搜索与图片下载；API Key 只在请求头中使用，不记录到日志
 */
@Slf4j
@Component
public class PexelsClient {

    private static final int CONNECT_TIMEOUT_SECONDS = 3;
    private static final int READ_TIMEOUT_SECONDS = 20;
    /** 下载上限 10 MB，多读 1 字节用于识别超限 */
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;
    /** images.pexels.com 前置 Cloudflare 会拦截 Java 默认 UA（403），下载需带浏览器 UA */
    private static final String DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String apiKey;

    @Autowired
    public PexelsClient(RestTemplateBuilder restTemplateBuilder,
                        @Value("${picture.pexels.api-base-url:https://api.pexels.com/v1}") String apiBaseUrl,
                        @Value("${picture.pexels.api-key:}") String apiKey) {
        this(restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS)).build(), apiBaseUrl, apiKey);
    }

    /** 供测试注入自定义 RestTemplate（如 MockRestServiceServer） */
    public PexelsClient(RestTemplate restTemplate, String apiBaseUrl, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.apiKey = apiKey;
    }

    /**
     * 按关键词搜索一次，per_page 使用实际张数；未配置 Key、401/403/429/5xx/超时网络异常统一抛 PEXELS_ERROR
     */
    public List<Photo> search(String keyword, int count) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "未配置 Pexels API Key");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/search")
                .queryParam("query", keyword)
                .queryParam("per_page", count)
                .queryParam("page", 1)
                .queryParam("locale", "zh-CN")
                .build()
                .encode()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, apiKey);
        try {
            ResponseEntity<SearchResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), SearchResponse.class);
            logRateLimit(response.getHeaders());
            SearchResponse body = response.getBody();
            return body == null || body.getPhotos() == null ? List.of() : body.getPhotos();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            // 只记录状态码，不记录请求头，避免 API Key 进入日志
            log.warn("Pexels 搜索失败, status={}", status);
            throw new BusinessException(ErrorCode.PEXELS_ERROR, searchFailureMessage(status));
        } catch (RestClientException e) {
            log.warn("Pexels 搜索请求异常: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "Pexels 服务访问失败，请稍后重试");
        }
    }

    /** 下载 Pexels API 返回的图片地址，内容读取上限 10 MB + 1 字节 */
    public DownloadedImage download(URI uri) {
        try {
            return restTemplate.execute(uri, HttpMethod.GET,
                    request -> request.getHeaders().set(HttpHeaders.USER_AGENT, DOWNLOAD_USER_AGENT),
                    response -> new DownloadedImage(
                            response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                            response.getBody().readNBytes(MAX_DOWNLOAD_BYTES + 1)));
        } catch (RestClientException e) {
            log.warn("下载 Pexels 图片失败, host={}, message={}", uri.getHost(), e.getMessage());
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "下载 Pexels 图片失败");
        }
    }

    private void logRateLimit(HttpHeaders headers) {
        log.info("Pexels 配额: 剩余={}, 重置时间={}", headers.getFirst("X-Ratelimit-Remaining"),
                headers.getFirst("X-Ratelimit-Reset"));
    }

    private static String searchFailureMessage(int status) {
        if (status == 429) {
            return "Pexels 接口调用配额已用尽或请求过于频繁，请稍后重试";
        }
        if (status == 401 || status == 403) {
            return "Pexels API Key 无效或无权访问";
        }
        return "Pexels 服务暂时不可用，请稍后重试";
    }

    /** 下载结果：响应声明的类型与图片字节 */
    public record DownloadedImage(String contentType, byte[] content) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Photo {
        private Long id;
        /** Pexels 图片详情页 */
        private String url;
        private String photographer;
        @JsonProperty("photographer_url")
        private String photographerUrl;
        private String alt;
        private Integer width;
        private Integer height;
        private Src src;

        public String getLarge2xUrl() {
            return src == null ? null : src.getLarge2x();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Src {
        @JsonProperty("large2x")
        private String large2x;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SearchResponse {
        private List<Photo> photos;
    }
}
