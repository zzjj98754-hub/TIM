package com.tuling.tim.gateway.service.impl;

import com.tuling.tim.gateway.service.impl.UserInfoCacheServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
public class UserInfoCacheServiceImplTest {

    @Test
    public void onlineUsersShouldReturnEmptySetWhenRedisHasNoMembers() {
        UserInfoCacheServiceImpl service = new UserInfoCacheServiceImpl();
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        SetOperations sets = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(sets.members("tim-login-status")).thenReturn(null);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        org.junit.jupiter.api.Assertions.assertTrue(service.onlineUser().isEmpty());
    }

}
