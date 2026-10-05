package com.example.cloudpicture.image.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.cloudpicture.image.client.AiServiceClient;
import com.example.cloudpicture.image.dto.request.AiModerationRequest;
import com.example.cloudpicture.image.dto.response.AiModerationVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * AI 自动审核：每日凌晨扫描待审图片，调 ai-service 得出三态结论后按阈值裁决回写。
 * 回写带 CAS 条件（待审且无 AI 结论），人工先审的图 AI 不触碰；管理员可随时人工覆盖 AI 结果
 */
@Slf4j
@Service
public class AiReviewService {

    /** AI 审核来源哨兵：reviewer_id=0 表示审核结论由 AI 写入 */
    private static final long AI_REVIEWER_ID = 0L;

    private final ImageMapper imageMapper;
    private final CosStorage cosStorage;
    private final AiServiceClient aiServiceClient;
    private final boolean enabled;
    private final int passThreshold;
    private final int blockThreshold;

    public AiReviewService(ImageMapper imageMapper, CosStorage cosStorage, AiServiceClient aiServiceClient,
                           @Value("${picture.ai-review.enabled:true}") boolean enabled,
                           @Value("${picture.ai-review.pass-threshold:80}") int passThreshold,
                           @Value("${picture.ai-review.block-threshold:90}") int blockThreshold) {
        this.imageMapper = imageMapper;
        this.cosStorage = cosStorage;
        this.aiServiceClient = aiServiceClient;
        this.enabled = enabled;
        this.passThreshold = passThreshold;
        this.blockThreshold = blockThreshold;
    }

    /** 每日凌晨 0 点批量审核；演示时可把 AI_REVIEW_CRON 临时调成高频轮询 */
    @Scheduled(cron = "${picture.ai-review.cron:0 0 0 * * ?}")
    public void runNightlyModeration() {
        if (!enabled) {
            return;
        }
        List<Image> images = imageMapper.selectList(Wrappers.lambdaQuery(Image.class)
                .eq(Image::getReviewStatus, Image.REVIEW_PENDING)
                .isNull(Image::getAiReviewVerdict)
                .orderByAsc(Image::getCreateTime));
        log.info("AI 自动审核任务开始: count={}", images.size());
        for (Image image : images) {
            moderateImage(image);
        }
        log.info("AI 自动审核任务结束");
    }

    void moderateImage(Image image) {
        AiModerationRequest request = new AiModerationRequest();
        request.setImageUrl(cosStorage.signedUrl(image.getCosKey()));
        request.setOwnerId(image.getOwnerId());
        try {
            AiModerationVO result = aiServiceClient.moderate(request).getData();
            if (result == null) {
                markError(image.getId());
                return;
            }
            applyVerdict(image, result);
        } catch (FeignException e) {
            if (e.status() == 429) {
                // 限流：本轮跳过，保持无 AI 结论，次日凌晨任务自动重试
                log.info("AI 审核限流跳过: imageId={}", image.getId());
                return;
            }
            markError(image.getId());
        } catch (Exception e) {
            log.warn("AI 审核异常转人工: imageId={}", image.getId(), e);
            markError(image.getId());
        }
    }

    void applyVerdict(Image image, AiModerationVO result) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Image> wrapper = Wrappers.lambdaUpdate(Image.class)
                .eq(Image::getId, image.getId())
                .eq(Image::getReviewStatus, Image.REVIEW_PENDING)
                .isNull(Image::getAiReviewVerdict)
                .set(Image::getAiReviewVerdict, result.getVerdict())
                .set(Image::getAiReviewConfidence, result.getConfidence())
                .set(Image::getAiReviewLabels, joinLabels(result.getLabels()))
                .set(Image::getAiReviewTime, now);
        if (Image.AI_VERDICT_PASS.equals(result.getVerdict()) && meets(result, passThreshold)) {
            wrapper.set(Image::getReviewStatus, Image.REVIEW_PASSED)
                    .set(Image::getReviewerId, AI_REVIEWER_ID)
                    .set(Image::getReviewMessage, "AI 自动审核通过")
                    .set(Image::getReviewTime, now);
        } else if (Image.AI_VERDICT_BLOCK.equals(result.getVerdict()) && meets(result, blockThreshold)) {
            wrapper.set(Image::getReviewStatus, Image.REVIEW_REJECTED)
                    .set(Image::getReviewerId, AI_REVIEWER_ID)
                    .set(Image::getReviewMessage, "AI 自动审核拒绝：" + joinLabels(result.getLabels()))
                    .set(Image::getReviewTime, now);
        }
        imageMapper.update(null, wrapper);
    }

    /** AI 失败：写 ERROR 不再自动重试，留待人工兜底 */
    private void markError(Long imageId) {
        imageMapper.update(null, Wrappers.lambdaUpdate(Image.class)
                .eq(Image::getId, imageId)
                .eq(Image::getReviewStatus, Image.REVIEW_PENDING)
                .isNull(Image::getAiReviewVerdict)
                .set(Image::getAiReviewVerdict, Image.AI_VERDICT_ERROR)
                .set(Image::getAiReviewTime, LocalDateTime.now()));
    }

    private static boolean meets(AiModerationVO result, int threshold) {
        return result.getConfidence() != null && result.getConfidence() >= threshold;
    }

    private static String joinLabels(List<String> labels) {
        return labels == null ? null : String.join(",", labels);
    }
}
