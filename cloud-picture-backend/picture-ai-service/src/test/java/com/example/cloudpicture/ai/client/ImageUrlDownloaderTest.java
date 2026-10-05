package com.example.cloudpicture.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageUrlDownloaderTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP_SIGNATURE = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    void sniffsSupportedFormats() {
        assertEquals("png", ImageUrlDownloader.sniffFormat(PNG_SIGNATURE));
        assertEquals("jpg", ImageUrlDownloader.sniffFormat(JPG_SIGNATURE));
        assertEquals("webp", ImageUrlDownloader.sniffFormat(WEBP_SIGNATURE));
    }

    @Test
    void returnsNullForNonImageBytes() {
        assertNull(ImageUrlDownloader.sniffFormat("not an image".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageUrlDownloader.sniffFormat(new byte[0]));
        assertNull(ImageUrlDownloader.sniffFormat(new byte[]{(byte) 0xFF, (byte) 0xD8}));
    }

    @Test
    void buildsDataUrlWithSniffedMime() {
        String dataUrl = ImageUrlDownloader.buildDataUrl(PNG_SIGNATURE);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"), dataUrl);
        assertTrue(ImageUrlDownloader.buildDataUrl(JPG_SIGNATURE).startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void rejectsNonImageContent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ImageUrlDownloader.buildDataUrl("not an image".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ErrorCode.PARAMS_ERROR, ex.getErrorCode());
    }
}
