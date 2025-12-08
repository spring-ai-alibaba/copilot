package com.alibaba.cloud.ai.copilot.memory.cache;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemoryCacheService 单元测试
 * 测试分布式缓存的核心功能
 */
@ExtendWith(MockitoExtension.class)
class MemoryCacheServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private MemoryCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new MemoryCacheService(redissonClient);
    }

    @Test
    void testSetAndGetMessages() {
        // Arrange
        String conversationId = "test-conversation-1";
        List<Message> messages = createTestMessages(3);

        // Act
        cacheService.setMessages(conversationId, messages);
        List<Message> retrieved = cacheService.getMessages(conversationId);

        // Assert
        assertNotNull(retrieved);
        assertEquals(3, retrieved.size());
        assertEquals(messages.get(0).getId(), retrieved.get(0).getId());
    }

    @Test
    void testGetMessages_CacheMiss() {
        // Arrange
        String conversationId = "non-existent-conversation";

        // Act
        List<Message> retrieved = cacheService.getMessages(conversationId);

        // Assert
        assertNull(retrieved);
    }

    @Test
    void testDeleteMessages() {
        // Arrange
        String conversationId = "test-conversation-2";
        List<Message> messages = createTestMessages(2);
        cacheService.setMessages(conversationId, messages);

        // Act
        cacheService.deleteMessages(conversationId);
        List<Message> retrieved = cacheService.getMessages(conversationId);

        // Assert
        assertNull(retrieved);
    }

    @Test
    void testSetAndGetLastCompressionTime() {
        // Arrange
        String conversationId = "test-conversation-3";
        long timestamp = System.currentTimeMillis();

        // Act
        cacheService.setLastCompressionTime(conversationId, timestamp);
        Long retrieved = cacheService.getLastCompressionTime(conversationId);

        // Assert
        assertNotNull(retrieved);
        assertEquals(timestamp, retrieved);
    }

    @Test
    void testTryLock_Success() throws InterruptedException {
        // Arrange
        String conversationId = "test-conversation-4";
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);

        // Act
        RLock lock = cacheService.tryLock(conversationId, 5, 60);

        // Assert
        assertNotNull(lock);
        verify(rLock).tryLock(5, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void testTryLock_Failure() throws InterruptedException {
        // Arrange
        String conversationId = "test-conversation-5";
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        // Act
        RLock lock = cacheService.tryLock(conversationId, 5, 60);

        // Assert
        assertNull(lock);
    }

    @Test
    void testUnlock() {
        // Arrange
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // Act
        cacheService.unlock(rLock);

        // Assert
        verify(rLock).unlock();
    }

    @Test
    void testUnlock_NotHeldByCurrentThread() {
        // Arrange
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        // Act
        cacheService.unlock(rLock);

        // Assert
        verify(rLock, never()).unlock();
    }

    @Test
    void testClearAll() {
        // Arrange
        String conversationId = "test-conversation-6";
        List<Message> messages = createTestMessages(2);
        long timestamp = System.currentTimeMillis();
        
        cacheService.setMessages(conversationId, messages);
        cacheService.setLastCompressionTime(conversationId, timestamp);

        // Act
        cacheService.clearAll(conversationId);

        // Assert
        assertNull(cacheService.getMessages(conversationId));
        assertNull(cacheService.getLastCompressionTime(conversationId));
    }

    @Test
    void testExists() {
        // Arrange
        String conversationId = "test-conversation-7";
        List<Message> messages = createTestMessages(1);
        cacheService.setMessages(conversationId, messages);

        // Act
        boolean exists = cacheService.exists(conversationId);

        // Assert
        assertTrue(exists);
    }

    @Test
    void testExists_NotFound() {
        // Arrange
        String conversationId = "non-existent-conversation";

        // Act
        boolean exists = cacheService.exists(conversationId);

        // Assert
        assertFalse(exists);
    }

    @Test
    void testRefreshExpiration() {
        // Arrange
        String conversationId = "test-conversation-8";
        List<Message> messages = createTestMessages(1);
        cacheService.setMessages(conversationId, messages);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cacheService.refreshExpiration(conversationId));
    }

    /**
     * 创建测试消息列表
     */
    private List<Message> createTestMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message message = new Message();
            message.setId("msg-" + i);
            message.setRole(i % 2 == 0 ? "user" : "assistant");
            message.setContent("Test message " + i);
            message.setTimestamp(LocalDateTime.now());
            messages.add(message);
        }
        return messages;
    }
}

