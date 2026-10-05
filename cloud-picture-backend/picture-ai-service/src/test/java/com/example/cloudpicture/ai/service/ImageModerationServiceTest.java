package com.example.cloudpicture.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cloudpicture.ai.client.BailianVisionClient;
import com.example.cloudpicture.ai.client.ImageUrlDownloader;
import com.example.cloudpicture.ai.dto.response.ImageModerationVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ImageModerationServiceTest {

    @Test
    void returnsParsedVerdictForValidImage() {
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString()))
                .thenReturn("{\"verdict\":\"PASS\",\"confidence\":95,\"labels\":[]}");
        ImageModerationVO vo = service(client, downloader(), new AtomicLong()).moderate("https://img", 7L);
        assertEquals("PASS", vo.getVerdict());
        assertEquals(95, vo.getConfidence());
    }

    @Test
    void rejectsNullOwnerId() {
        assertParamsError(() -> service(mock(BailianVisionClient.class), downloader(), new AtomicLong())
                .moderate("https://img", null));
    }

    @Test
    void rateLimitsEleventhRequestPerMinute() {
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString()))
                .thenReturn("{\"verdict\":\"PASS\",\"confidence\":95,\"labels\":[]}");
        ImageModerationService service = service(client, downloader(), new AtomicLong());
        for (int i = 0; i < 10; i++) {
            service.moderate("https://img", 7L);
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> service.moderate("https://img", 7L));
        assertEquals(ErrorCode.AI_RATE_LIMIT, ex.getErrorCode());
    }

    @Test
    void rateLimitedRequestNeverReachesModel() {
        BailianVisionClient client = mock(BailianVisionClient.class);
        ImageUrlDownloader downloader = downloader();
        AtomicLong counter = new AtomicLong(10);
        assertThrows(BusinessException.class,
                () -> service(client, downloader, counter).moderate("https://img", 7L));
        org.mockito.Mockito.verifyNoInteractions(client, downloader);
    }

    @Test
    void propagatesDownloadFailure() {
        ImageUrlDownloader downloader = mock(ImageUrlDownloader.class);
        when(downloader.toDataUrl(anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "图片下载失败"));
        assertUnavailable(() -> service(mock(BailianVisionClient.class), downloader, new AtomicLong())
                .moderate("https://img", 7L));
    }

    @Test
    void propagatesModelFailure() {
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));
        assertUnavailable(() -> service(client, downloader(), new AtomicLong()).moderate("https://img", 7L));
    }

    private static ImageUrlDownloader downloader() {
        ImageUrlDownloader downloader = mock(ImageUrlDownloader.class);
        when(downloader.toDataUrl(anyString())).thenReturn("data:image/png;base64,xxx");
        return downloader;
    }

    private static ImageModerationService service(BailianVisionClient client, ImageUrlDownloader downloader,
                                                   AtomicLong counter) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());
        return new ImageModerationService(client, downloader, redis, 10);
    }

    private static void assertParamsError(org.junit.jupiter.api.function.Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.PARAMS_ERROR, ex.getErrorCode());
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.AI_UNAVAILABLE, ex.getErrorCode());
    }
}
