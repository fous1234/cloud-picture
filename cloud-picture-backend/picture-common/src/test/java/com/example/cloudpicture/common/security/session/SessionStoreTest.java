package com.example.cloudpicture.common.security.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class SessionStoreTest {

    @Test
    void refreshKeepsTheTokenInTheUserIndex() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        SessionStore sessionStore = new SessionStore(redisTemplate, 600);

        sessionStore.refresh("token", 42L);

        verify(redisTemplate).expire("picture:session:token", Duration.ofSeconds(600));
        verify(setOperations).add("picture:session:user:42", "token");
        verify(redisTemplate).expire("picture:session:user:42", Duration.ofSeconds(600));
    }
}
