package com.example.cloudpicture.ai.service;

import com.example.cloudpicture.ai.client.BailianVisionClient;
import com.example.cloudpicture.ai.client.ImageUrlDownloader;
import com.example.cloudpicture.ai.dto.response.ImageModerationVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 图片审核：限流 → 下载 URL 内容 → 调用百炼 → 严格解析为三态结论。
 * 无状态：不写数据库、不访问 COS；由 image-service 的凌晨审核任务经内部端点调用
 */
@Slf4j
@Service
public class ImageModerationService {

    private static final String RATE_KEY_PREFIX = "picture:ai:moderation:rate:";
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final String PROMPT = """
            你是图片安全审核员。请判断图片是否包含违规内容。
            违规类别：色情低俗、暴力血腥、毒品、赌博、武器危爆、政治敏感、广告二维码、未成年人风险。
            要求：
            1. 只返回一个 JSON 对象，不要输出 Markdown 代码围栏、解释或其他文字。
            2. JSON 结构固定为 {"verdict": "PASS|REVIEW|BLOCK", "confidence": 0-100, "labels": ["类别1", "类别2"]}。
            3. verdict 规则：确信违规→BLOCK；疑似或拿不准→REVIEW；正常→PASS。
            4. confidence 为你对该判断的确信度，是 0-100 的整数。
            5. labels 填命中的违规类别；verdict 为 PASS 时返回空数组。
            """;

    private final BailianVisionClient visionClient;
    private final ImageUrlDownloader imageUrlDownloader;
    private final StringRedisTemplate redisTemplate;
    private final int rateLimitPerMinute;

    public ImageModerationService(BailianVisionClient visionClient, ImageUrlDownloader imageUrlDownloader,
                                  StringRedisTemplate redisTemplate,
                                  @Value("${picture.ai.rate-limit-per-minute:10}") int rateLimitPerMinute) {
        this.visionClient = visionClient;
        this.imageUrlDownloader = imageUrlDownloader;
        this.redisTemplate = redisTemplate;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public ImageModerationVO moderate(String imageUrl, Long ownerId) {
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传者 id 不能为空");
        }
        checkRateLimit(ownerId);
        String dataUrl = imageUrlDownloader.toDataUrl(imageUrl);
        long start = System.currentTimeMillis();
        ImageModerationVO verdict = ModelResponseParser.parseModeration(visionClient.complete(dataUrl, PROMPT));
        log.info("AI 审核完成: ownerId={}, verdict={}, confidence={}, costMs={}",
                ownerId, verdict.getVerdict(), verdict.getConfidence(), System.currentTimeMillis() - start);
        return verdict;
    }

    /** 每个上传者每分钟最多 rateLimitPerMinute 次（与元数据接口同一限流模式），超出返回 AI_RATE_LIMIT */
    // ponytail: INCR 与 EXPIRE 非原子，进程在两步之间挂掉会留下无 TTL 计数；如需严格可换 Lua 脚本
    private void checkRateLimit(Long ownerId) {
        String key = RATE_KEY_PREFIX + ownerId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (count != null && count > rateLimitPerMinute) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT);
        }
    }
}
