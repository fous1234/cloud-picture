package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

