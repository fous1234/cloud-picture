package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SpaceTierExpireTaskTest {

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrivateSpace.class);
    }

    @Test
    void downgradeClearsTierAndExpireTimeForExpiredSpacesOnly() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(3);

        new SpaceTierExpireTask(mapper).downgradeExpiredTiers();

        ArgumentCaptor<Wrapper<PrivateSpace>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PrivateSpace> wrapper = (LambdaUpdateWrapper<PrivateSpace>) captor.getValue();

        // 两列都必须出现在 SET 里：只写 tier 不清 tier_expire_time 会留下"FREE 却有到期时间"的脏数据
        assertTrue(wrapper.getSqlSet().contains("tier"), wrapper.getSqlSet());
        assertTrue(wrapper.getSqlSet().contains("tier_expire_time"), wrapper.getSqlSet());
        // 条件是"非 FREE 且已到期"
        assertTrue(wrapper.getSqlSegment().contains("tier"));
        assertTrue(wrapper.getSqlSegment().contains("tier_expire_time"));
    }
}