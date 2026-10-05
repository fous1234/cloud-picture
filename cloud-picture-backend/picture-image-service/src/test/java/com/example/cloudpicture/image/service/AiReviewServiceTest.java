package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.image.client.AiServiceClient;
import com.example.cloudpicture.image.dto.request.AiModerationRequest;
import com.example.cloudpicture.image.dto.response.AiModerationVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import feign.FeignException;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiReviewServiceTest {

    private ImageMapper imageMapper;
    private CosStorage cosStorage;
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Image.class);
        imageMapper = mock(ImageMapper.class);
        cosStorage = mock(CosStorage.class);
        aiServiceClient = mock(AiServiceClient.class);
        when(cosStorage.signedUrl("picture/1.png")).thenReturn("https://signed");
    }

    @Test
    void passAboveThresholdAutoApproves() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(vo("PASS", 90, List.of())));

        service(true).moderateImage(image());

        LambdaUpdateWrapper<Image> wrapper = capturedUpdate();
        assertTrue(wrapper.getSqlSet().contains("review_status"));
        assertTrue(wrapper.getSqlSet().contains("reviewer_id"));
        assertTrue(wrapper.getSqlSegment().contains("review_status"));
        assertTrue(wrapper.getSqlSegment().contains("ai_review_verdict IS NULL"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(Image.REVIEW_PASSED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PASS"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(90));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("AI 自动审核通过"));
    }

    @Test
    void blockAboveThresholdAutoRejects() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(vo("BLOCK", 95, List.of("色情低俗"))));

        service(true).moderateImage(image());

        LambdaUpdateWrapper<Image> wrapper = capturedUpdate();
        assertTrue(wrapper.getSqlSet().contains("review_status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(Image.REVIEW_REJECTED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("AI 自动审核拒绝：色情低俗"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("色情低俗"));
    }

    @Test
    void passBelowThresholdOnlyRecordsAiResult() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(vo("PASS", 60, List.of())));

        service(true).moderateImage(image());

        LambdaUpdateWrapper<Image> wrapper = capturedUpdate();
        assertFalse(wrapper.getSqlSet().contains("review_status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PASS"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(60));
    }

    @Test
    void reviewVerdictStaysPending() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(vo("REVIEW", 95, List.of("广告二维码"))));

        service(true).moderateImage(image());

        assertFalse(capturedUpdate().getSqlSet().contains("review_status"));
        assertTrue(capturedUpdate().getParamNameValuePairs().containsValue("REVIEW"));
    }

    @Test
    void blockBelowThresholdStaysPending() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(vo("BLOCK", 85, List.of("色情低俗"))));

        service(true).moderateImage(image());

        assertFalse(capturedUpdate().getSqlSet().contains("review_status"));
        assertTrue(capturedUpdate().getParamNameValuePairs().containsValue("BLOCK"));
    }

    @Test
    void rateLimitedWritesNothingAndRetriesNextNight() {
        FeignException limited = feignException(429);
        when(aiServiceClient.moderate(any(AiModerationRequest.class))).thenThrow(limited);

        service(true).moderateImage(image());

        verify(imageMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void unavailableMarksErrorForManualReview() {
        FeignException unavailable = feignException(503);
        when(aiServiceClient.moderate(any(AiModerationRequest.class))).thenThrow(unavailable);

        service(true).moderateImage(image());

        LambdaUpdateWrapper<Image> wrapper = capturedUpdate();
        assertFalse(wrapper.getSqlSet().contains("review_status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(Image.AI_VERDICT_ERROR));
    }

    @Test
    void nullDataMarksError() {
        when(aiServiceClient.moderate(any(AiModerationRequest.class)))
                .thenReturn(ApiResponse.success(null));

        service(true).moderateImage(image());

        assertTrue(capturedUpdate().getParamNameValuePairs().containsValue(Image.AI_VERDICT_ERROR));
    }

    @Test
    void disabledDoesNotScan() {
        service(false).runNightlyModeration();

        verify(imageMapper, never()).selectList(any(Wrapper.class));
        verify(imageMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void nightlyScanSelectsPendingWithoutAiVerdict() {
        when(imageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(image()));

        service(true).runNightlyModeration();

        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).selectList(captor.capture());
        var query = (LambdaQueryWrapper<Image>) captor.getValue();
        assertTrue(query.getSqlSegment().contains("review_status"));
        assertTrue(query.getSqlSegment().contains("ai_review_verdict IS NULL"));
        assertTrue(query.getParamNameValuePairs().containsValue(Image.REVIEW_PENDING));
        verify(aiServiceClient).moderate(any(AiModerationRequest.class));
    }

    private AiReviewService service(boolean enabled) {
        return new AiReviewService(imageMapper, cosStorage, aiServiceClient, enabled, 80, 90);
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<Image> capturedUpdate() {
        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<Image>) captor.getValue();
    }

    private static AiModerationVO vo(String verdict, int confidence, List<String> labels) {
        AiModerationVO vo = new AiModerationVO();
        vo.setVerdict(verdict);
        vo.setConfidence(confidence);
        vo.setLabels(labels);
        return vo;
    }

    private static Image image() {
        Image image = new Image();
        image.setId(1L);
        image.setOwnerId(42L);
        image.setReviewStatus(Image.REVIEW_PENDING);
        image.setCosKey("picture/1.png");
        image.setPicFormat("png");
        image.setName("pic1");
        return image;
    }

    private static FeignException feignException(int status) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        return exception;
    }
}
