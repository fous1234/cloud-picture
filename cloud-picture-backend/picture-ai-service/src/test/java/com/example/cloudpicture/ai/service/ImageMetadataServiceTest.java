package com.example.cloudpicture.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cloudpicture.ai.client.BailianVisionClient;
import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

class ImageMetadataServiceTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @AfterEach
    void clearUser() {
        CurrentUser.clear();
    }

    @Test
    void returnsParsedMetadataForValidImage() {
        login(1L);
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString())).thenReturn("{\"introduction\":\"猫\",\"tags\":[\"猫\"]}");
        ImageMetadataVO vo = service(client, new AtomicLong()).suggest(pngFile());
        assertEquals("猫", vo.getIntroduction());
        assertEquals(List.of("猫"), vo.getTags());
    }

    @Test
    void rejectsAnonymousRequest() {
        CurrentUser.clear();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(mock(BailianVisionClient.class), new AtomicLong()).suggest(pngFile()));
        assertEquals(ErrorCode.NOT_LOGIN, ex.getErrorCode());
    }

    @Test
    void rejectsUnsupportedExtension() {
        login(1L);
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", PNG_SIGNATURE);
        assertParamsError(() -> service(mock(BailianVisionClient.class), new AtomicLong()).suggest(gif));
    }

    @Test
    void rejectsFakeImageContent() {
        login(1L);
        MockMultipartFile fake = new MockMultipartFile("file", "a.png", "image/png",
                "not an image".getBytes(StandardCharsets.UTF_8));
        assertParamsError(() -> service(mock(BailianVisionClient.class), new AtomicLong()).suggest(fake));
    }

    @Test
    void rejectsOversizeFile() {
        login(1L);
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", tooBig);
        assertParamsError(() -> service(mock(BailianVisionClient.class), new AtomicLong()).suggest(file));
    }

    @Test
    void rateLimitsEleventhRequestPerMinute() {
        login(7L);
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString())).thenReturn("{\"introduction\":\"x\",\"tags\":[]}");
        ImageMetadataService service = service(client, new AtomicLong());
        for (int i = 0; i < 10; i++) {
            service.suggest(pngFile());
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suggest(pngFile()));
        assertEquals(ErrorCode.AI_RATE_LIMIT, ex.getErrorCode());
    }

    @Test
    void propagatesModelFailureAsUnavailable() {
        login(9L);
        BailianVisionClient client = mock(BailianVisionClient.class);
        when(client.complete(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));
        BusinessException ex = assertThrows(BusinessException.class, () -> service(client, new AtomicLong()).suggest(pngFile()));
        assertEquals(ErrorCode.AI_UNAVAILABLE, ex.getErrorCode());
    }

    private static void assertParamsError(org.junit.jupiter.api.function.Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.PARAMS_ERROR, ex.getErrorCode());
    }

    private static void login(long userId) {
        CurrentUser user = new CurrentUser();
        user.setId(userId);
        user.setAccount("u" + userId);
        user.setRole(CurrentUser.ROLE_USER);
        CurrentUser.set(user);
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG_SIGNATURE);
    }

    private static ImageMetadataService service(BailianVisionClient client, AtomicLong counter) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());
        return new ImageMetadataService(client, redis, "10MB", 10);
    }
}