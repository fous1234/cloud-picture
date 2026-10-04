package com.example.cloudpicture.ai.service;

import com.example.cloudpicture.ai.client.BailianVisionClient;
import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 图片元数据建议：限流 → 文件校验（大小/扩展名/魔数）→ 调用百炼 → 严格解析结果。
 * 无状态：不写数据库、不访问 COS、不持久化图片
 */
@Slf4j
@Service
public class ImageMetadataService {

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    private static final int HEADER_LENGTH = 12;
    private static final String RATE_KEY_PREFIX = "picture:ai:metadata:rate:";
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final String PROMPT = """
            你是图片元数据助手。请根据图片内容生成中文简介和自由标签。
            要求：
            1. 只返回一个 JSON 对象，不要输出 Markdown 代码围栏、解释或其他文字。
            2. JSON 结构固定为 {"introduction": "简介", "tags": ["标签1", "标签2"]}。
            3. introduction 为对图片内容的简短中文描述，不超过 512 个字符。
            4. tags 为字符串数组，最多 10 个，每个标签不超过 32 个字符。
            5. 标签根据图片内容自由生成，不限于任何已有标签库。
            """;

    private final BailianVisionClient visionClient;
    private final StringRedisTemplate redisTemplate;
    private final long maxFileSizeBytes;
    private final String maxFileSizeText;
    private final int rateLimitPerMinute;

    public ImageMetadataService(BailianVisionClient visionClient,
                                StringRedisTemplate redisTemplate,
                                @Value("${picture.ai.max-file-size:10MB}") String maxFileSize,
                                @Value("${picture.ai.rate-limit-per-minute:10}") int rateLimitPerMinute) {
        this.visionClient = visionClient;
        this.redisTemplate = redisTemplate;
        this.maxFileSizeText = maxFileSize;
        this.maxFileSizeBytes = DataSize.parse(maxFileSize).toBytes();
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public ImageMetadataVO suggest(MultipartFile file) {
        CurrentUser currentUser = CurrentUser.get();
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        checkRateLimit(currentUser.getId());
        String format = validateAndGetFormat(file);
        String dataUrl = buildDataUrl(file, format);
        long start = System.currentTimeMillis();
        ImageMetadataVO metadata = ModelResponseParser.parse(visionClient.complete(dataUrl, PROMPT));
        log.info("AI 元数据生成完成: userId={}, format={}, tagCount={}, costMs={}",
                currentUser.getId(), format, metadata.getTags().size(), System.currentTimeMillis() - start);
        return metadata;
    }

    /** 每个用户每分钟最多 rateLimitPerMinute 次，超出返回 AI_RATE_LIMIT */
    // ponytail: INCR 与 EXPIRE 非原子，进程在两步之间挂掉会留下无 TTL 计数；如需严格可换 Lua 脚本
    private void checkRateLimit(Long userId) {
        String key = RATE_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (count != null && count > rateLimitPerMinute) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT);
        }
    }

    private String validateAndGetFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 " + maxFileSizeText);
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件名不合法");
        }
        String format = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/jpeg/png/webp 格式");
        }
        if (!matchesMagic(readHeader(file), format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不是有效图片");
        }
        return format;
    }

    private String buildDataUrl(MultipartFile file, String format) {
        try {
            String mimeType = "image/" + ("jpg".equals(format) ? "jpeg" : format);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取上传文件失败");
        }
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[HEADER_LENGTH];
            int read = inputStream.readNBytes(header, 0, HEADER_LENGTH);
            return read == HEADER_LENGTH ? header : Arrays.copyOf(header, Math.max(read, 0));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取上传文件失败");
        }
    }

    /** 文件头魔数校验，避免只改扩展名把非图片内容传给模型 */
    private static boolean matchesMagic(byte[] header, String format) {
        return switch (format) {
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A;
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }
}