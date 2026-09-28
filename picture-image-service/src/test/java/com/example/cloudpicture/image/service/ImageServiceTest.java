package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageUpdateRequest;
import com.example.cloudpicture.image.service.impl.ImageServiceImpl;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}

