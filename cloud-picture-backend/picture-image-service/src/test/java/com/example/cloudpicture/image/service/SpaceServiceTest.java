package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.request.SpaceCreateRequest;
import com.example.cloudpicture.image.dto.request.SpaceImageQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.SpaceVO;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.entity.SpaceImage;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import com.example.cloudpicture.image.mapper.SpaceImageMapper;
import com.example.cloudpicture.image.service.impl.SpaceServiceImpl;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class SpaceServiceTest {

    private static final long OWNER_ID = 42L;
    private static final long SPACE_ID = 7L;

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrivateSpace.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceImage.class);
    }

    @AfterEach
    void clearCurrentUser() {
        CurrentUser.clear();
    }

    @Test
    void createRejectsWhenSpaceAlreadyExists() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "我的私有空间"));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class))
                        .create(new SpaceCreateRequest()));

        assertEquals(ErrorCode.SPACE_EXISTS, error.getErrorCode());
        verify(spaceMapper, never()).insert(any(PrivateSpace.class));
    }

    @Test
    void createInsertsDefaultNameForCurrentOwner() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        SpaceVO vo = service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class))
                .create(new SpaceCreateRequest());

        ArgumentCaptor<PrivateSpace> captor = ArgumentCaptor.forClass(PrivateSpace.class);
        verify(spaceMapper).insert(captor.capture());
        assertEquals(OWNER_ID, captor.getValue().getOwnerId());
        assertEquals("我的私有空间", captor.getValue().getName());
        assertEquals(0L, vo.getImageCount());
        assertEquals(0L, vo.getTotalSize());
    }

    @Test
    void createTranslatesDuplicateKeyToSpaceExists() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(spaceMapper.insert(any(PrivateSpace.class))).thenThrow(new DuplicateKeyException("uk_owner_id"));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class))
                        .create(new SpaceCreateRequest()));

        assertEquals(ErrorCode.SPACE_EXISTS, error.getErrorCode());
    }

    @Test
    void getMineReturnsNullWhenAbsent() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        assertNull(service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class)).getMine());
    }

    @Test
    void getMineFillsImageCountAndTotalSize() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("imageCount", 3L, "totalSize", 2048L)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        SpaceVO vo = service(spaceMapper, imageMapper, mock(CosStorage.class)).getMine();

        assertEquals(SPACE_ID, vo.getId());
        assertEquals("影集", vo.getName());
        assertEquals(3L, vo.getImageCount());
        assertEquals(2048L, vo.getTotalSize());
    }

    @Test
    void renameAndDeleteRejectWhenNoSpace() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);
        SpaceService spaceService = service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class));
        SpaceRenameRequest request = new SpaceRenameRequest();
        request.setName("新名字");

        assertNotFound(() -> spaceService.rename(request));
        assertNotFound(spaceService::deleteMine);

        verify(spaceMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteMineCascadeDeletesImagesThenRemovesSpace() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(image(1L, SPACE_ID, OWNER_ID), image(2L, SPACE_ID, OWNER_ID)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        assertTrue(service(spaceMapper, imageMapper, cosStorage).deleteMine());

        InOrder ordered = inOrder(cosStorage, imageMapper, spaceMapper);
        ordered.verify(cosStorage).delete("private/1.png");
        ordered.verify(cosStorage).delete("private/2.png");
        ordered.verify(imageMapper).delete(any(Wrapper.class));
        ordered.verify(spaceMapper).deleteById(SPACE_ID);
    }

    @Test
    void uploadRejectsWhenNoSpace() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);
        SpaceService spaceService = service(spaceMapper, mock(SpaceImageMapper.class), cosStorage);

        assertNotFound(() -> spaceService.upload(pngFile(), new ImageUploadRequest()));

        verify(cosStorage, never()).upload(any(MultipartFile.class), anyString(), anyString());
    }

    @Test
    void uploadStoresPrivateKeyAndFullMetadata() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);
        ImageUploadRequest request = new ImageUploadRequest();
        request.setName("海边");
        request.setIntroduction("简介");
        request.setCategory("风景");
        request.setTags(List.of("海", " 海 ", "日落"));

        service(spaceMapper, imageMapper, cosStorage).upload(pngFile(), request);

        ArgumentCaptor<SpaceImage> captor = ArgumentCaptor.forClass(SpaceImage.class);
        verify(imageMapper).insert(captor.capture());
        SpaceImage saved = captor.getValue();
        assertEquals(SPACE_ID, saved.getSpaceId());
        assertEquals(OWNER_ID, saved.getOwnerId());
        assertTrue(saved.getCosKey().startsWith("private/"));
        assertTrue(saved.getCosKey().endsWith(".png"));
        assertEquals("海边", saved.getName());
        assertEquals("简介", saved.getIntroduction());
        assertEquals("风景", saved.getCategory());
        assertEquals("海,日落", saved.getTags());
        assertEquals("png", saved.getPicFormat());
        verify(cosStorage).upload(any(MultipartFile.class), eq(saved.getCosKey()), eq("image/png"));
    }

    @Test
    void uploadCleansCosObjectWhenInsertFails() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.insert(any(SpaceImage.class))).thenThrow(new RuntimeException("数据库不可用"));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        SpaceService spaceService = service(spaceMapper, imageMapper, cosStorage);
        assertThrows(RuntimeException.class, () -> spaceService.upload(pngFile(), new ImageUploadRequest()));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cosStorage).delete(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith("private/"));
    }

    @Test
    void pageImagesAlwaysFiltersSpaceAndOwner() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(1, 12));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);
        SpaceImageQueryRequest request = new SpaceImageQueryRequest();
        request.setCurrent(1);
        request.setSize(12);

        service(spaceMapper, imageMapper, mock(CosStorage.class)).pageImages(request);

        ArgumentCaptor<Wrapper<SpaceImage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).selectPage(any(Page.class), captor.capture());
        LambdaQueryWrapper<SpaceImage> wrapper = (LambdaQueryWrapper<SpaceImage>) captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("space_id"));
        assertTrue(wrapper.getSqlSegment().contains("owner_id"));
    }

    @Test
    void downloadAndDeleteRejectImagesOfOtherUsersIncludingAdmin() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        SpaceService spaceService = service(spaceMapper, imageMapper, cosStorage);

        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);
        assertNotFound(() -> spaceService.download(9L));
        assertNotFound(() -> spaceService.deleteImage(9L));

        // 管理员没有图片级入口，本服务不读角色，身份不影响过滤结果
        setCurrentUser(1L, CurrentUser.ROLE_ADMIN);
        assertNotFound(() -> spaceService.download(9L));
        assertNotFound(() -> spaceService.deleteImage(9L));

        verify(cosStorage, never()).delete(anyString());
    }

    @Test
    void downloadReturnsSignedUrlForOwnImageWithSharedFilenameRule() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        SpaceImage owned = image(9L, SPACE_ID, OWNER_ID);
        owned.setName("海边.jpg");
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(owned);
        when(cosStorage.signedDownloadUrl(anyString(), anyString())).thenReturn("https://signed");
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        assertEquals("https://signed", service(spaceMapper, imageMapper, cosStorage).download(9L));

        verify(cosStorage).signedDownloadUrl("private/9.png", "海边.jpg.png");
    }

    @Test
    void deleteImageDeletesCosObjectBeforeSoftDelete() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(imageMapper.selectOne(any(Wrapper.class))).thenReturn(image(9L, SPACE_ID, OWNER_ID));
        when(imageMapper.deleteById(9L)).thenReturn(1);
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        assertTrue(service(spaceMapper, imageMapper, cosStorage).deleteImage(9L));

        InOrder ordered = inOrder(cosStorage, imageMapper);
        ordered.verify(cosStorage).delete("private/9.png");
        ordered.verify(imageMapper).deleteById(9L);
    }

    @Test
    void uploadRejectsWhenImageCountAlreadyAtPlanLimit() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        PrivateSpace space = space(SPACE_ID, OWNER_ID, "影集");
        space.setTier(TierPlan.PRO.name());
        space.setTierExpireTime(LocalDateTime.now().plusDays(1));
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space);
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("imageCount", (long) TierPlan.PRO.getImageLimit(), "totalSize", 0L)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(spaceMapper, imageMapper, cosStorage).upload(pngFile(), new ImageUploadRequest()));

        assertEquals(ErrorCode.SPACE_QUOTA_EXCEEDED, error.getErrorCode());
        // 预检必须在传 COS 之前，超限不能留下任何对象
        verify(cosStorage, never()).upload(any(MultipartFile.class), anyString(), anyString());
        verify(imageMapper, never()).insert(any(SpaceImage.class));
    }

    @Test
    void uploadRejectsWhenSizeAlreadyAtPlanLimit() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("imageCount", 0L, "totalSize", TierPlan.FREE.getSizeLimitBytes())));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(spaceMapper, imageMapper, cosStorage).upload(pngFile(), new ImageUploadRequest()));

        assertEquals(ErrorCode.SPACE_QUOTA_EXCEEDED, error.getErrorCode());
        verify(cosStorage, never()).upload(any(MultipartFile.class), anyString(), anyString());
    }

    @Test
    void uploadAllowsLastSlotWithinPlanLimit() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("imageCount", (long) TierPlan.FREE.getImageLimit() - 1, "totalSize", 0L)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        service(spaceMapper, imageMapper, cosStorage).upload(pngFile(), new ImageUploadRequest());

        verify(imageMapper).insert(any(SpaceImage.class));
    }

    @Test
    void uploadTreatsExpiredTierAsFree() throws Exception {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        PrivateSpace space = space(SPACE_ID, OWNER_ID, "影集");
        space.setTier(TierPlan.PRO.name());
        // 已过期：档位字段还写着 PRO，但读时应按 FREE 判配额
        space.setTierExpireTime(LocalDateTime.now().minusDays(1));
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space);
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("imageCount", (long) TierPlan.FREE.getImageLimit(), "totalSize", 0L)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(spaceMapper, imageMapper, cosStorage).upload(pngFile(), new ImageUploadRequest()));

        assertEquals(ErrorCode.SPACE_QUOTA_EXCEEDED, error.getErrorCode());
    }

    @Test
    void getMineReportsPlanLimitsAndHidesExpiredTier() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        PrivateSpace space = space(SPACE_ID, OWNER_ID, "影集");
        space.setTier(TierPlan.MAX.name());
        space.setTierExpireTime(LocalDateTime.now().plusDays(10));
        when(spaceMapper.selectOne(any(Wrapper.class))).thenReturn(space);
        when(imageMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(Map.of("imageCount", 1L, "totalSize", 10L)));
        setCurrentUser(OWNER_ID, CurrentUser.ROLE_USER);

        SpaceVO active = service(spaceMapper, imageMapper, mock(CosStorage.class)).getMine();
        assertEquals(TierPlan.MAX.name(), active.getTier());
        assertEquals(TierPlan.MAX.getImageLimit(), active.getImageLimit());
        assertEquals(TierPlan.MAX.getSizeLimitBytes(), active.getSizeLimitBytes());
        assertEquals(space.getTierExpireTime(), active.getTierExpireTime());

        space.setTierExpireTime(LocalDateTime.now().minusDays(1));
        SpaceVO expired = service(spaceMapper, imageMapper, mock(CosStorage.class)).getMine();
        assertEquals(TierPlan.FREE.name(), expired.getTier());
        assertEquals(TierPlan.FREE.getImageLimit(), expired.getImageLimit());
        assertNull(expired.getTierExpireTime());
    }

    private static SpaceService service(PrivateSpaceMapper spaceMapper, SpaceImageMapper imageMapper,
                                        CosStorage cosStorage) {
        return new SpaceServiceImpl(spaceMapper, imageMapper, cosStorage, new ImageFileSupport("10MB"));
    }

    private static PrivateSpace space(long id, long ownerId, String name) {
        PrivateSpace space = new PrivateSpace();
        space.setId(id);
        space.setOwnerId(ownerId);
        space.setName(name);
        return space;
    }

    private static SpaceImage image(long id, long spaceId, long ownerId) {
        SpaceImage image = new SpaceImage();
        image.setId(id);
        image.setSpaceId(spaceId);
        image.setOwnerId(ownerId);
        image.setCosKey("private/" + id + ".png");
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

    private static void assertNotFound(Executable executable) {
        BusinessException error = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    private static MockMultipartFile pngFile() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "test.png", "image/png", output.toByteArray());
    }
}