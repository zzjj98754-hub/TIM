package com.tuling.tim.gateway.service.impl;

import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.gateway.service.impl.AccountServiceRedisImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
public class AccountServiceRedisImplTest {

    @Test
    public void offlineRouteShouldThrowBusinessException() {
        AccountServiceRedisImpl service = new AccountServiceRedisImpl();
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        ValueOperations values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("tim-route:42")).thenReturn(null);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        try {
            service.loadRouteRelatedByUserId(42L);
            org.junit.jupiter.api.Assertions.fail("offline route must not be reported as success");
        } catch (TIMException ex) {
            org.junit.jupiter.api.Assertions.assertEquals(StatusEnum.OFF_LINE.getCode(), ex.getErrorCode());
        }
    }

}
