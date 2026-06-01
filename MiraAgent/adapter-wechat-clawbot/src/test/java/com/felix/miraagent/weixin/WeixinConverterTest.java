package com.felix.miraagent.weixin;

import com.felix.miraagent.weixin.client.dto.MessageItem;
import com.felix.miraagent.weixin.client.dto.TextItem;
import com.felix.miraagent.weixin.client.dto.WeixinMessage;
import com.felix.miraagent.weixin.poll.ContextTokenStore;
import com.felix.miraagent.weixin.poll.MessageDeduplicator;
import com.felix.miraagent.weixin.poll.UserSessionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeixinConverterTest {

    @Test
    void textMessageExtraction() {
        TextItem textItem = new TextItem("hello world");
        MessageItem item = new MessageItem(1, textItem);

        WeixinMessage msg = new WeixinMessage();
        msg.setFromUserId("user123");
        msg.setMessageId("msg456");
        msg.setContextToken("ctx789");
        msg.setItemList(List.of(item));

        assertNotNull(msg.getItemList());
        assertEquals(1, msg.getItemList().get(0).getType());
        assertEquals("hello world", msg.getItemList().get(0).getTextItem().getText());
        assertEquals("user123", msg.getFromUserId());
        assertEquals("ctx789", msg.getContextToken());
    }

    @Test
    void nonTextMessageIgnored() {
        MessageItem item = new MessageItem(3, null); // type 3 = image, not text
        WeixinMessage msg = new WeixinMessage();
        msg.setItemList(List.of(item));

        assertNotEquals(1, msg.getItemList().get(0).getType());
        assertNull(msg.getItemList().get(0).getTextItem());
    }

    @Test
    void messageDeduplication() {
        MessageDeduplicator deduplicator = new MessageDeduplicator();

        assertFalse(deduplicator.isDuplicate("msg1"));
        assertTrue(deduplicator.isDuplicate("msg1"));
        assertFalse(deduplicator.isDuplicate("msg2"));
        assertTrue(deduplicator.isDuplicate("msg2"));
    }

    @Test
    void contextTokenStorage() {
        ContextTokenStore store = new ContextTokenStore();

        store.put("user1", "token1");
        assertEquals("token1", store.get("user1"));

        store.put("user1", "token2");
        assertEquals("token2", store.get("user1"));

        assertNull(store.get("user2"));
    }

    @Test
    void userSessionMapping() {
        UserSessionMapper mapper = new UserSessionMapper();

        String session1 = mapper.getOrCreateSession("user1");
        String session2 = mapper.getOrCreateSession("user1");
        assertNotNull(session1);
        assertEquals(session1, session2);

        String session3 = mapper.getOrCreateSession("user2");
        assertNotEquals(session1, session3);
    }
}
