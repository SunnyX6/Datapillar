package com.sunny.datapillar.auth.service.login.method.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoStateStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SsoStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new SsoStateStore(stringRedisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(stateStore, "stateTtlSeconds", 300L);
        ReflectionTestUtils.setField(stateStore, "replayStateTtlSeconds", 3600L);
        ReflectionTestUtils.setField(stateStore, "stateBytes", 24);
    }

    @Test
    void createState_shouldGenerateUrlSafeStateWithConfiguredEntropy() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        String state = stateStore.createState(10L, "dingtalk");

        assertNotNull(state);
        assertEquals(32, state.length());
        assertTrue(state.matches("^[A-Za-z0-9_-]+$"));

        ArgumentCaptor<String> payloadKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(payloadKeyCaptor.capture(), anyString(), eq(Duration.ofSeconds(300)));
        assertEquals("sso:state:payload:" + state, payloadKeyCaptor.getValue());
        verify(valueOperations).set(eq("sso:state:issued:" + state), eq("1"), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void createState_shouldGenerateUniqueStates() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        Set<String> states = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            states.add(stateStore.createState(10L, "dingtalk"));
        }

        assertEquals(256, states.size());
    }

    @Test
    void createState_shouldRejectTooSmallStateBytes() {
        ReflectionTestUtils.setField(stateStore, "stateBytes", 8);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> stateStore.createState(10L, "dingtalk"));

        assertTrue(exception.getMessage().contains("sso.state-bytes must be >= 16"));
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}
