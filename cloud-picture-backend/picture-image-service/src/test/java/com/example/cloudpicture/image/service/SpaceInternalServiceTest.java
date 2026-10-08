package com.example.cloudpicture.image.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.dto.request.SpaceGrantRequest;
import com.example.cloudpicture.image.dto.response.InternalSpaceVO;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SpaceInternalServiceTest {

    private static final long OWNER_ID = 42L;
    private static final long SPACE_ID = 7L;

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrivateSpace.class);
    }

    @Test
    void findByUserIdReturnsNullWhenUserHasNoSpace() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertNull(service(mapper).findByUserId(OWNER_ID));
    }

    @Test
    void findByUserIdReportsActiveTier() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(space("MAX", LocalDateTime.now().plusDays(5)));

        InternalSpaceVO vo = service(mapper).findByUserId(OWNER_ID);

        assertEquals(SPACE_ID, vo.getSpaceId());
        assertEquals(TierPlan.MAX.name(), vo.getTier());
        assertEquals(LocalDateTime.now().plusDays(5).toLocalDate(), vo.getTierExpireTime().toLocalDate());
    }

    @Test
    void findByUserIdHidesExpiredTier() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(space("PRO", LocalDateTime.now().minusMinutes(1)));

        InternalSpaceVO vo = service(mapper).findByUserId(OWNER_ID);

        assertEquals(TierPlan.FREE.name(), vo.getTier());
        assertNull(vo.getTierExpireTime());
    }

    @Test
    void grantRejectsNonPurchasableTier() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(mapper).grant(request(TierPlan.FREE.name(), LocalDateTime.now().plusDays(30))));

        assertEquals(ErrorCode.PARAMS_ERROR, error.getErrorCode());
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void grantRejectsPastExpireTime() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(mapper).grant(request(TierPlan.PRO.name(), LocalDateTime.now().minusSeconds(1))));

        assertEquals(ErrorCode.PARAMS_ERROR, error.getErrorCode());
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void grantReportsNotFoundWhenSpaceMissing() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(mapper).grant(request(TierPlan.PRO.name(), LocalDateTime.now().plusDays(30))));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void grantOverwritesTierAndExpireTimeByOwner() {
        PrivateSpaceMapper mapper = mock(PrivateSpaceMapper.class);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        LocalDateTime expireTime = LocalDateTime.now().plusDays(30);

        assertTrue(service(mapper).grant(request(TierPlan.MAX.name(), expireTime)));

        LambdaUpdateWrapper<PrivateSpace> wrapper = captureUpdate(mapper);
        assertTrue(wrapper.getSqlSet().contains("tier"));
        assertTrue(wrapper.getSqlSet().contains("tier_expire_time"));
        assertTrue(wrapper.getSqlSegment().contains("owner_id"));
    }

    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<PrivateSpace> captureUpdate(PrivateSpaceMapper mapper) {
        ArgumentCaptor<Wrapper<PrivateSpace>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<PrivateSpace>) captor.getValue();
    }

    private static SpaceInternalService service(PrivateSpaceMapper mapper) {
        return new SpaceInternalService(mapper);
    }

    private static PrivateSpace space(String tier, LocalDateTime expireTime) {
        PrivateSpace space = new PrivateSpace();
        space.setId(SPACE_ID);
        space.setOwnerId(OWNER_ID);
        space.setName("影集");
        space.setTier(tier);
        space.setTierExpireTime(expireTime);
        return space;
    }

    private static SpaceGrantRequest request(String tier, LocalDateTime expireTime) {
        SpaceGrantRequest request = new SpaceGrantRequest();
        request.setUserId(OWNER_ID);
        request.setTier(tier);
        request.setTierExpireTime(expireTime);
        return request;
    }
}