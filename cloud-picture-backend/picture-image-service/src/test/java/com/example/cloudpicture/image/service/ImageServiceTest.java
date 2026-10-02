package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageUpdateRequest;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.service.impl.ImageServiceImpl;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ImageServiceTest {

    @AfterEach
    void clearCurrentUser() {
        CurrentUser.clear();
    }

    @Test
    void emptyTagsExplicitlyClearExistingTags() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Image.class);
        ImageMapper imageMapper = mock(ImageMapper.class);
        ImageTagMapper imageTagMapper = mock(ImageTagMapper.class);
        Image existing = new Image();
        existing.setId(12L);
        existing.setOwnerId(42L);
        existing.setTags("cat,dog");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(imageMapper.update(any(Image.class), any(Wrapper.class))).thenReturn(1);
        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(42L);
        CurrentUser.set(currentUser);

        ImageService imageService = new ImageServiceImpl(imageMapper, imageTagMapper, mock(CosStorage.class),
                mock(UploaderFiller.class), "10MB");
        ImageUpdateRequest request = new ImageUpdateRequest();
        request.setId(12L);
        request.setTags(List.of());

        assertTrue(imageService.updateImage(request));

        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).update(any(Image.class), captor.capture());
        LambdaUpdateWrapper<Image> updateWrapper = (LambdaUpdateWrapper<Image>) captor.getValue();
        assertTrue(updateWrapper.getSqlSet().contains("tags"));
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(null));
        verify(imageTagMapper).decreaseTags(List.of("cat", "dog"));
    }

    @Test
    void adminUploadIsPublishedImmediately() throws Exception {
        Image uploaded = uploadAs("ADMIN");

        assertEquals(Image.REVIEW_PASSED, uploaded.getReviewStatus());
    }

    @Test
    void userUploadWaitsForReview() throws Exception {
        Image uploaded = uploadAs("USER");

        assertEquals(Image.REVIEW_PENDING, uploaded.getReviewStatus());
    }

    @Test
    void pageMyImagesAlwaysFiltersCurrentOwner() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Image.class);
        ImageMapper imageMapper = mock(ImageMapper.class);
        ImageTagMapper imageTagMapper = mock(ImageTagMapper.class);
        Page<Image> page = new Page<>(1, 12);
        when(imageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(42L);
        currentUser.setRole(CurrentUser.ROLE_USER);
        CurrentUser.set(currentUser);

        ImageService imageService = new ImageServiceImpl(imageMapper, imageTagMapper, mock(CosStorage.class),
                mock(UploaderFiller.class), "10MB");
        ImageQueryRequest request = new ImageQueryRequest();
        request.setCurrent(1);
        request.setSize(12);
        imageService.pageMyImages(request);

        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).selectPage(any(Page.class), captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Image>) captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("owner_id"));
        assertEquals(42L, ((Number) wrapper.getParamNameValuePairs().get("MPGENVAL1")).longValue());
    }

    @Test
    void passedImageDownloadableByAnyLoggedInUser() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectById(10L)).thenReturn(image(10L, 42L, Image.REVIEW_PASSED));
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedDownloadUrl(any(), any())).thenReturn("https://signed-download");
        setCurrentUser(7L, CurrentUser.ROLE_USER);

        assertEquals("https://signed-download", service(imageMapper, cosStorage).download(10L));
        verify(cosStorage).signedDownloadUrl("picture/10.png", "pic10.png");
    }

    @Test
    void pendingImageDownloadableByOwner() {
        assertPendingDownloadAllowed(42L, CurrentUser.ROLE_USER);
    }

    @Test
    void pendingImageDownloadableByAdmin() {
        assertPendingDownloadAllowed(1L, CurrentUser.ROLE_ADMIN);
    }

    @Test
    void pendingImageRejectedForOtherUser() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectById(10L)).thenReturn(image(10L, 42L, Image.REVIEW_PENDING));
        setCurrentUser(7L, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(imageMapper, mock(CosStorage.class)).download(10L));
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void rejectedImageFollowsOwnerAndAdminRule() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectById(10L)).thenReturn(image(10L, 42L, Image.REVIEW_REJECTED));
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedDownloadUrl(any(), any())).thenReturn("https://signed-download");

        setCurrentUser(42L, CurrentUser.ROLE_USER);
        assertEquals("https://signed-download", service(imageMapper, cosStorage).download(10L));

        setCurrentUser(1L, CurrentUser.ROLE_ADMIN);
        assertEquals("https://signed-download", service(imageMapper, cosStorage).download(10L));

        setCurrentUser(7L, CurrentUser.ROLE_USER);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(imageMapper, cosStorage).download(10L));
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void missingImageReturnsNotFound() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectById(99L)).thenReturn(null);
        setCurrentUser(7L, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(imageMapper, mock(CosStorage.class)).download(99L));
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void downloadFilenameSanitizesPathAndFillsExtension() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedDownloadUrl(any(), any())).thenReturn("https://signed");
        setCurrentUser(1L, CurrentUser.ROLE_ADMIN);

        Image pathName = image(10L, 42L, Image.REVIEW_PENDING);
        pathName.setName("a/b:c*d?.jpg");
        when(imageMapper.selectById(10L)).thenReturn(pathName);
        service(imageMapper, cosStorage).download(10L);

        Image noExtension = image(11L, 42L, Image.REVIEW_PENDING);
        noExtension.setName("photo");
        noExtension.setPicFormat("png");
        when(imageMapper.selectById(11L)).thenReturn(noExtension);
        service(imageMapper, cosStorage).download(11L);

        Image emptyName = image(12L, 42L, Image.REVIEW_PENDING);
        emptyName.setName("   ");
        when(imageMapper.selectById(12L)).thenReturn(emptyName);
        service(imageMapper, cosStorage).download(12L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cosStorage, times(3)).signedDownloadUrl(any(), captor.capture());
        assertEquals(List.of("abcd.jpg.png", "photo.png", "image-12.png"), captor.getAllValues());
    }

    @Test
    void downloadFilenameAppendsActualFormatWhenExistingExtensionDiffers() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedDownloadUrl(any(), any())).thenReturn("https://signed");
        setCurrentUser(1L, CurrentUser.ROLE_ADMIN);

        Image image = image(13L, 42L, Image.REVIEW_PENDING);
        image.setName("photo.txt");
        image.setPicFormat("png");
        when(imageMapper.selectById(13L)).thenReturn(image);

        service(imageMapper, cosStorage).download(13L);

        verify(cosStorage).signedDownloadUrl("picture/13.png", "photo.txt.png");
    }

    private void assertPendingDownloadAllowed(Long userId, String role) {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectById(10L)).thenReturn(image(10L, 42L, Image.REVIEW_PENDING));
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedDownloadUrl(any(), any())).thenReturn("https://signed-download");
        setCurrentUser(userId, role);

        assertEquals("https://signed-download", service(imageMapper, cosStorage).download(10L));
    }

    private static ImageService service(ImageMapper imageMapper, CosStorage cosStorage) {
        return new ImageServiceImpl(imageMapper, mock(ImageTagMapper.class), cosStorage,
                mock(UploaderFiller.class), "10MB");
    }

    private static Image image(long id, long ownerId, int reviewStatus) {
        Image image = new Image();
        image.setId(id);
        image.setOwnerId(ownerId);
        image.setReviewStatus(reviewStatus);
        image.setCosKey("picture/" + id + ".png");
        image.setPicFormat("png");
        image.setName("pic" + id);
        return image;
    }

    private static void setCurrentUser(Long id, String role) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(id);
        currentUser.setRole(role);
        CurrentUser.set(currentUser);
    }

    private Image uploadAs(String role) throws Exception {
        ImageMapper imageMapper = mock(ImageMapper.class);
        ImageTagMapper imageTagMapper = mock(ImageTagMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(42L);
        currentUser.setRole(role);
        CurrentUser.set(currentUser);

        ImageService imageService = new ImageServiceImpl(imageMapper, imageTagMapper, cosStorage,
                mock(UploaderFiller.class), "10MB");
        imageService.upload(pngFile(), new ImageUploadRequest());

        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageMapper).insert(captor.capture());
        return captor.getValue();
    }

    private MockMultipartFile pngFile() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "test.png", "image/png", output.toByteArray());
    }
}

