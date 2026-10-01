package com.example.cloudpicture.image.service;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.aspect.RoleCheckAspect;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.client.PexelsClient;
import com.example.cloudpicture.image.controller.AdminImageController;
import com.example.cloudpicture.image.dto.request.ImageImportRequest;
import com.example.cloudpicture.image.dto.response.ImportResultVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import javax.imageio.ImageIO;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

class ImageImportServiceTest {

    private static final long ADMIN_ID = 42L;
    private static final String API_BASE_URL = "https://api.pexels.com/v1";
    private static final String API_KEY = "test-key";

    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private final ImageTagMapper imageTagMapper = mock(ImageTagMapper.class);
    private final CosStorage cosStorage = mock(CosStorage.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Image.class);
    }

    @AfterEach
    void clearCurrentUser() {
        CurrentUser.clear();
    }

    @Test
    void importsPhotosAsPexelsSource() {
        setCurrentAdmin();
        PexelsClient pexelsClient = mock(PexelsClient.class);
        when(pexelsClient.search("校园风景", 5)).thenReturn(List.of(photo(101L, "alt-one"), photo(102L, "alt-two")));
        when(pexelsClient.download(any(URI.class))).thenReturn(downloadedPng());
        ImageImportService service = service(pexelsClient);

        ImportResultVO result = service.importImages(request("校园风景"));

        assertEquals(2, result.getImported());
        assertEquals(0, result.getSkipped());
        assertEquals(0, result.getFailed());
        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageMapper, times(2)).insert(captor.capture());
        Image inserted = captor.getAllValues().get(0);
        assertEquals(Image.SOURCE_PEXELS, inserted.getSource());
        assertEquals("101", inserted.getSourceId());
        assertEquals("https://www.pexels.com/photo/101/", inserted.getSourcePageUrl());
        assertEquals("摄影师101", inserted.getPhotographer());
        assertEquals("https://www.pexels.com/@p101/", inserted.getPhotographerUrl());
        assertEquals("alt-one", inserted.getName());
        assertEquals(Image.REVIEW_PASSED, inserted.getReviewStatus());
        assertEquals(ADMIN_ID, inserted.getOwnerId());
        assertEquals("png", inserted.getPicFormat());
        assertEquals(List.of("校园风景"), inserted.tagList());
        assertNotNull(inserted.getCosKey());
        // 实际下载尺寸（2x3）优先于搜索结果的 API 原始尺寸（4000x3000）
        assertEquals(2, inserted.getPicWidth());
        assertEquals(3, inserted.getPicHeight());
        verify(imageTagMapper, times(2)).increaseTags(List.of("校园风景"));
    }

    @Test
    void existingSourceIdCountsAsSkipped() {
        setCurrentAdmin();
        PexelsClient pexelsClient = mock(PexelsClient.class);
        when(pexelsClient.search("校园风景", 5)).thenReturn(List.of(photo(101L, "alt-one"), photo(102L, "alt-two")));
        when(pexelsClient.download(any(URI.class))).thenReturn(downloadedPng());
        Image existing = new Image();
        existing.setSourceId("101");
        when(imageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        ImageImportService service = service(pexelsClient);

        ImportResultVO result = service.importImages(request("校园风景"));

        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getFailed());
        verify(imageMapper, times(1)).insert(any(Image.class));
        // 跳过的图片不再下载
        verify(pexelsClient, times(1)).download(any(URI.class));
    }

    @Test
    void downloadFailureDoesNotStopOtherPhotos() {
        setCurrentAdmin();
        PexelsClient pexelsClient = mock(PexelsClient.class);
        when(pexelsClient.search("校园风景", 5)).thenReturn(List.of(photo(101L, "alt-one"), photo(102L, "alt-two")));
        when(pexelsClient.download(any(URI.class)))
                .thenThrow(new BusinessException(ErrorCode.PEXELS_ERROR, "下载失败"))
                .thenReturn(downloadedPng());
        ImageImportService service = service(pexelsClient);

        ImportResultVO result = service.importImages(request("校园风景"));

        assertEquals(1, result.getImported());
        assertEquals(0, result.getSkipped());
        assertEquals(1, result.getFailed());
        verify(imageMapper, times(1)).insert(any(Image.class));
    }

    @Test
    void databaseFailureDeletesUploadedCosObject() {
        setCurrentAdmin();
        PexelsClient pexelsClient = mock(PexelsClient.class);
        when(pexelsClient.search("校园风景", 5)).thenReturn(List.of(photo(101L, "alt-one")));
        when(pexelsClient.download(any(URI.class))).thenReturn(downloadedPng());
        when(imageMapper.insert(any(Image.class))).thenThrow(new RuntimeException("db down"));
        ImageImportService service = service(pexelsClient);

        ImportResultVO result = service.importImages(request("校园风景"));

        assertEquals(0, result.getImported());
        assertEquals(1, result.getFailed());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cosStorage).upload(any(byte[].class), keyCaptor.capture(), anyString());
        verify(cosStorage).delete(keyCaptor.getValue());
    }

    @Test
    void search429ThrowsPexelsErrorAndInsertsNothing() {
        setCurrentAdmin();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(startsWith(API_BASE_URL + "/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        PexelsClient pexelsClient = new PexelsClient(restTemplate, API_BASE_URL, API_KEY);
        ImageImportService service = service(pexelsClient);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.importImages(request("校园风景")));

        assertEquals(ErrorCode.PEXELS_ERROR, e.getErrorCode());
        assertEquals(502, ErrorCode.PEXELS_ERROR.getHttpStatus());
        verifyNoInteractions(imageMapper);
        server.verify();
    }

    @Test
    void onlyAdminCanCallImportController() throws Exception {
        AdminImageController controller =
                new AdminImageController(mock(AdminImageService.class), mock(ImageImportService.class));
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod())
                .thenReturn(AdminImageController.class.getMethod("importImages", ImageImportRequest.class));
        when(joinPoint.getTarget()).thenReturn(controller);
        RoleCheckAspect aspect = new RoleCheckAspect();

        CurrentUser user = new CurrentUser();
        user.setId(7L);
        user.setRole(CurrentUser.ROLE_USER);
        CurrentUser.set(user);
        BusinessException denied = assertThrows(BusinessException.class, () -> aspect.checkRole(joinPoint));
        assertEquals(ErrorCode.NO_AUTH, denied.getErrorCode());
        assertEquals(403, ErrorCode.NO_AUTH.getHttpStatus());

        CurrentUser admin = new CurrentUser();
        admin.setId(ADMIN_ID);
        admin.setRole(CurrentUser.ROLE_ADMIN);
        CurrentUser.set(admin);
        assertDoesNotThrow(() -> aspect.checkRole(joinPoint));
    }

    private ImageImportService service(PexelsClient pexelsClient) {
        return new ImageImportService(pexelsClient, imageMapper, imageTagMapper, cosStorage, transactionManager);
    }

    private static void setCurrentAdmin() {
        CurrentUser admin = new CurrentUser();
        admin.setId(ADMIN_ID);
        admin.setRole(CurrentUser.ROLE_ADMIN);
        CurrentUser.set(admin);
    }

    private static ImageImportRequest request(String keyword) {
        ImageImportRequest request = new ImageImportRequest();
        request.setKeyword(keyword);
        return request;
    }

    private static PexelsClient.Photo photo(long id, String alt) {
        PexelsClient.Photo photo = new PexelsClient.Photo();
        photo.setId(id);
        photo.setUrl("https://www.pexels.com/photo/" + id + "/");
        photo.setPhotographer("摄影师" + id);
        photo.setPhotographerUrl("https://www.pexels.com/@p" + id + "/");
        photo.setAlt(alt);
        photo.setWidth(4000);
        photo.setHeight(3000);
        PexelsClient.Src src = new PexelsClient.Src();
        src.setLarge2x("https://images.pexels.com/photos/" + id + "/pexels-photo-" + id + ".png");
        photo.setSrc(src);
        return photo;
    }

    /** 2x3 的真实 PNG 字节，用于尺寸校验 */
    private static PexelsClient.DownloadedImage downloadedPng() {
        try {
            BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return new PexelsClient.DownloadedImage("image/png", output.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
