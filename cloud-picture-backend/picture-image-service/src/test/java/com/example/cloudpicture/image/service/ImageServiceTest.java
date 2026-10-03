package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.example.cloudpicture.image.dto.response.ImageShareVO;
import com.example.cloudpicture.image.dto.response.SharedImageVO;
import com.example.cloudpicture.image.service.impl.ImageServiceImpl;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import javax.imageio.ImageIO;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

    @Test
    void getShareReportsCurrentTokenState() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        Image owned = image(10L, 42L, Image.REVIEW_PASSED);
        owned.setShareToken("current-token");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(owned);
        setCurrentUser(42L, CurrentUser.ROLE_USER);

        ImageShareVO share = service(imageMapper, mock(CosStorage.class)).getShare(10L);

        assertTrue(share.isEnabled());
        assertEquals("current-token", share.getToken());
    }

    @Test
    void getShareReportsDisabledWhenTokenAbsent() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(10L, 42L, Image.REVIEW_PASSED));
        setCurrentUser(42L, CurrentUser.ROLE_USER);

        ImageShareVO share = service(imageMapper, mock(CosStorage.class)).getShare(10L);

        assertFalse(share.isEnabled());
        assertNull(share.getToken());
    }

    @Test
    void createShareWritesFreshUrlSafeTokenOverwritingOld() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        Image owned = image(10L, 42L, Image.REVIEW_PASSED);
        owned.setShareToken("old-token");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(owned);
        when(imageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        setCurrentUser(42L, CurrentUser.ROLE_USER);
        ImageService imageService = service(imageMapper, mock(CosStorage.class));

        ImageShareVO first = imageService.createShare(10L);
        ImageShareVO second = imageService.createShare(10L);

        assertTrue(first.isEnabled());
        assertEquals(43, first.getToken().length());
        assertTrue(first.getToken().matches("[A-Za-z0-9_-]+"));
        assertNotEquals("old-token", first.getToken());
        assertNotEquals(first.getToken(), second.getToken());

        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper, times(2)).update(isNull(), captor.capture());
        LambdaUpdateWrapper<Image> secondUpdate = (LambdaUpdateWrapper<Image>) captor.getAllValues().get(1);
        assertTrue(secondUpdate.getSqlSet().contains("share_token"));
        assertTrue(secondUpdate.getParamNameValuePairs().containsValue(second.getToken()));
    }

    @Test
    void revokeShareClearsToken() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        Image owned = image(10L, 42L, Image.REVIEW_PASSED);
        owned.setShareToken("current-token");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(owned);
        when(imageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        setCurrentUser(42L, CurrentUser.ROLE_USER);

        assertTrue(service(imageMapper, mock(CosStorage.class)).revokeShare(10L));

        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<Image> updateWrapper = (LambdaUpdateWrapper<Image>) captor.getValue();
        assertTrue(updateWrapper.getSqlSet().contains("share_token"));
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(null));
    }

    @Test
    void ownerCanManageShareOfNotPassedImage() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        Image pending = image(10L, 42L, Image.REVIEW_PENDING);
        pending.setShareToken("current-token");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(imageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        setCurrentUser(42L, CurrentUser.ROLE_USER);
        ImageService imageService = service(imageMapper, mock(CosStorage.class));

        assertEquals("current-token", imageService.getShare(10L).getToken());
        assertTrue(imageService.revokeShare(10L));
    }

    @Test
    void nonOwnerAndAdminCannotManageOtherUsersShare() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ImageService imageService = service(imageMapper, mock(CosStorage.class));

        setCurrentUser(7L, CurrentUser.ROLE_USER);
        assertNotFound(() -> imageService.getShare(10L));
        assertNotFound(() -> imageService.createShare(10L));
        assertNotFound(() -> imageService.revokeShare(10L));

        setCurrentUser(1L, CurrentUser.ROLE_ADMIN);
        assertNotFound(() -> imageService.getShare(10L));
        assertNotFound(() -> imageService.createShare(10L));
        assertNotFound(() -> imageService.revokeShare(10L));

        verify(imageMapper, never()).update(isNull(), any(Wrapper.class));
        // 管理查询始终按当前用户 id 过滤，管理员身份不越权
        ArgumentCaptor<Wrapper<Image>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper, atLeastOnce()).selectOne(captor.capture());
        var ownershipQuery = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Image>) captor.getValue();
        assertTrue(ownershipQuery.getSqlSegment().contains("owner_id"));
        assertTrue(ownershipQuery.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void pendingAndRejectedImagesCannotCreateShare() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        setCurrentUser(42L, CurrentUser.ROLE_USER);
        ImageService imageService = service(imageMapper, mock(CosStorage.class));

        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(10L, 42L, Image.REVIEW_PENDING));
        assertNotFound(() -> imageService.createShare(10L));

        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(10L, 42L, Image.REVIEW_REJECTED));
        assertNotFound(() -> imageService.createShare(10L));

        verify(imageMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void publicShareReturnsSafeDtoWithSignedPreview() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        Image passed = image(10L, 42L, Image.REVIEW_PASSED);
        passed.setShareToken("tok");
        passed.setName("分享图片");
        passed.setSource(Image.SOURCE_PEXELS);
        passed.setSourcePageUrl("https://pexels.com/photo/1");
        passed.setPhotographer("Alice");
        passed.setPhotographerUrl("https://pexels.com/@alice");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(passed);
        CosStorage cosStorage = mock(CosStorage.class);
        when(cosStorage.signedUrl("picture/10.png")).thenReturn("https://preview-signed");
        CurrentUser.clear();

        SharedImageVO shared = service(imageMapper, cosStorage).getSharedImage("tok");

        assertEquals(10L, shared.getId());
        assertEquals("https://preview-signed", shared.getUrl());
        assertEquals("分享图片", shared.getName());
        assertEquals("Alice", shared.getPhotographer());
        assertEquals("https://pexels.com/photo/1", shared.getSourcePageUrl());
        verify(cosStorage).signedUrl("picture/10.png");
        assertTrue(fieldNames(SharedImageVO.class).containsAll(Set.of("id", "url", "name", "photographer")));
        assertTrue(fieldNames(SharedImageVO.class).stream().noneMatch(FORBIDDEN_SHARED_FIELDS::contains));
    }

    @Test
    void invalidOrRevokedOrNotPassedShareReturnsNotFound() {
        initImageTableInfo();
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ImageService imageService = service(imageMapper, mock(CosStorage.class));

        assertNotFound(() -> imageService.getSharedImage("unknown-token"));
        assertNotFound(() -> imageService.getSharedImage("  "));

        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(11L, 42L, Image.REVIEW_PENDING));
        assertNotFound(() -> imageService.getSharedImage("pending-token"));

        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(12L, 42L, Image.REVIEW_REJECTED));
        assertNotFound(() -> imageService.getSharedImage("rejected-token"));
    }

    private static final Set<String> FORBIDDEN_SHARED_FIELDS = Set.of(
            "ownerId", "cosKey", "shareToken", "reviewStatus", "reviewMessage", "reviewerId", "sourceId", "thumbnailUrl");

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(java.util.stream.Collectors.toSet());
    }

    private static void initImageTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Image.class);
    }

    private static void assertNotFound(Executable executable) {
        BusinessException error = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
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

