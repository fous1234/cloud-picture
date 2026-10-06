package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.image.client.UserServiceClient;
import com.example.cloudpicture.image.dto.request.AdminSpaceQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.AdminSpaceVO;
import com.example.cloudpicture.image.dto.response.UserBriefVO;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.entity.SpaceImage;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import com.example.cloudpicture.image.mapper.SpaceImageMapper;
import com.example.cloudpicture.image.service.impl.AdminSpaceServiceImpl;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;

class AdminSpaceServiceTest {

    private static final long SPACE_ID = 7L;
    private static final long OWNER_ID = 42L;

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrivateSpace.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceImage.class);
    }

    @Test
    void adminSpaceVOExposesOnlyMetadata() {
        Set<String> fields = fieldNames(AdminSpaceVO.class);

        assertTrue(fields.containsAll(Set.of("id", "name", "imageCount", "totalSize", "createTime", "owner")));
        assertTrue(fields.stream().noneMatch(Set.of("url", "thumbnailUrl", "cosKey")::contains));
    }

    @Test
    void pageSpacesFillsAggregatesAndOwner() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        Page<PrivateSpace> page = new Page<>(1, 10);
        page.setRecords(List.of(space(SPACE_ID, OWNER_ID, "影集")));
        when(spaceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(imageMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(Map.of("spaceId", SPACE_ID, "imageCount", 5L, "totalSize", 4096L)));
        UserBriefVO owner = new UserBriefVO();
        owner.setId(OWNER_ID);
        owner.setName("张三");
        when(userServiceClient.batchGetUsers(List.of(OWNER_ID))).thenReturn(ApiResponse.success(List.of(owner)));

        PageData<AdminSpaceVO> result = service(spaceMapper, imageMapper, mock(CosStorage.class), userServiceClient)
                .pageSpaces(new AdminSpaceQueryRequest());

        AdminSpaceVO vo = result.getRecords().get(0);
        assertEquals(SPACE_ID, vo.getId());
        assertEquals("影集", vo.getName());
        assertEquals(5L, vo.getImageCount());
        assertEquals(4096L, vo.getTotalSize());
        assertEquals("张三", vo.getOwner().getName());
    }

    @Test
    void pageSpacesStillReturnsWhenOwnerQueryFails() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        Page<PrivateSpace> page = new Page<>(1, 10);
        page.setRecords(List.of(space(SPACE_ID, OWNER_ID, "影集")));
        when(spaceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(imageMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());
        when(userServiceClient.batchGetUsers(any())).thenThrow(new RuntimeException("用户服务不可用"));

        PageData<AdminSpaceVO> result = service(spaceMapper, imageMapper, mock(CosStorage.class), userServiceClient)
                .pageSpaces(new AdminSpaceQueryRequest());

        AdminSpaceVO vo = result.getRecords().get(0);
        assertEquals(0L, vo.getImageCount());
        assertEquals(0L, vo.getTotalSize());
        assertNull(vo.getOwner());
    }

    @Test
    void renameAndDeleteRejectMissingSpace() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        when(spaceMapper.selectById(anyLong())).thenReturn(null);
        AdminSpaceService adminSpaceService =
                service(spaceMapper, mock(SpaceImageMapper.class), mock(CosStorage.class), mock(UserServiceClient.class));
        SpaceRenameRequest request = new SpaceRenameRequest();
        request.setName("新名字");

        assertNotFound(() -> adminSpaceService.rename(SPACE_ID, request));
        assertNotFound(() -> adminSpaceService.delete(SPACE_ID));

        verify(spaceMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteCascadeDeletesImagesThenRemovesSpace() {
        PrivateSpaceMapper spaceMapper = mock(PrivateSpaceMapper.class);
        SpaceImageMapper imageMapper = mock(SpaceImageMapper.class);
        CosStorage cosStorage = mock(CosStorage.class);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID, OWNER_ID, "影集"));
        when(imageMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(image(1L, SPACE_ID, OWNER_ID), image(2L, SPACE_ID, OWNER_ID)));

        assertTrue(service(spaceMapper, imageMapper, cosStorage, mock(UserServiceClient.class)).delete(SPACE_ID));

        InOrder ordered = inOrder(cosStorage, imageMapper, spaceMapper);
        ordered.verify(cosStorage).delete("private/1.png");
        ordered.verify(cosStorage).delete("private/2.png");
        ordered.verify(imageMapper).delete(any(Wrapper.class));
        ordered.verify(spaceMapper).deleteById(SPACE_ID);
    }

    private static AdminSpaceService service(PrivateSpaceMapper spaceMapper, SpaceImageMapper imageMapper,
                                             CosStorage cosStorage, UserServiceClient userServiceClient) {
        return new AdminSpaceServiceImpl(spaceMapper, imageMapper, cosStorage, userServiceClient);
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
        return image;
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }

    private static void assertNotFound(Executable executable) {
        BusinessException error = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }
}