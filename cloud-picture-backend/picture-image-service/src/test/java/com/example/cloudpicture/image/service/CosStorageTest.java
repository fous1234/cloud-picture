package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cloudpicture.image.config.CosConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CosStorageTest {

    @Test
    void signedDownloadUrlSetsAttachmentResponseHeaders() throws Exception {
        CosConfig cosConfig = mock(CosConfig.class);
        COSClient cosClient = mock(COSClient.class);
        when(cosConfig.getBucket()).thenReturn("demo-1250000000");
        when(cosConfig.getSecretId()).thenReturn("AKIDfake");
        when(cosConfig.getSecretKey()).thenReturn("fakekey");
        when(cosConfig.cosClient()).thenReturn(cosClient);
        String signedUrl = "https://demo-1250000000.cos.ap-shanghai.myqcloud.com/picture/x.png?sig=1";
        when(cosClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL(signedUrl));

        CosStorage cosStorage = new CosStorage(cosConfig, 3600);
        String url = cosStorage.signedDownloadUrl("picture/x.png", "雨后的校园林荫道.jpg");

        assertEquals(signedUrl, url);
        ArgumentCaptor<GeneratePresignedUrlRequest> captor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(cosClient).generatePresignedUrl(captor.capture());
        var headers = captor.getValue().getResponseHeaders();
        assertTrue(headers.getContentDisposition().startsWith("attachment;"));
        assertTrue(headers.getContentDisposition().contains("filename*=UTF-8''"));
        assertEquals("image/png", headers.getContentType());
    }
}