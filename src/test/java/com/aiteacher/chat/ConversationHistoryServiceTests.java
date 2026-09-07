package com.aiteacher.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiteacher.chat.ConversationHistoryService.ChatTurn;

class ConversationHistoryServiceTests {

    private final ConversationHistoryService service = new ConversationHistoryService();

    @Test
    void appendAndGetReturnsTurnsInOrder() {
        service.append("chat-1", ChatTurn.user("What is gravity?"));
        service.append("chat-1", ChatTurn.assistant("A force of attraction."));

        List<ChatTurn> turns = service.get("chat-1");
        assertEquals(2, turns.size());
        assertEquals("user", turns.get(0).role());
        assertEquals("What is gravity?", turns.get(0).text());
        assertEquals("assistant", turns.get(1).role());
    }

    @Test
    void chatsAreIsolatedByKey() {
        service.append("a", ChatTurn.user("question a"));
        service.append("b", ChatTurn.user("question b"));

        assertEquals(1, service.get("a").size());
        assertEquals("question b", service.get("b").get(0).text());
    }

    @Test
    void getForUnknownChatIsEmptyAndNeverNull() {
        assertTrue(service.get("missing").isEmpty());
    }

    @Test
    void historyIsCappedAtMaxTurns() {
        for (int i = 0; i < ConversationHistoryService.MAX_TURNS_PER_CHAT + 10; i++) {
            service.append("chat", ChatTurn.user("message " + i));
        }
        List<ChatTurn> turns = service.get("chat");
        assertEquals(ConversationHistoryService.MAX_TURNS_PER_CHAT, turns.size());
        // Oldest entries were trimmed — the first retained is the 10th message.
        assertEquals("message 10", turns.get(0).text());
        assertEquals("message " + (ConversationHistoryService.MAX_TURNS_PER_CHAT + 9),
                turns.get(turns.size() - 1).text());
    }

    @Test
    void promptContextReturnsMostRecentTurnsWithSpeakerPrefixes() {
        service.append("chat", ChatTurn.user("q1"));
        service.append("chat", ChatTurn.assistant("a1"));
        service.append("chat", ChatTurn.user("q2"));

        List<String> lines = service.recentTurnsForPrompt("chat");
        assertEquals(List.of("Student: q1", "Teacher: a1", "Student: q2"), lines);
    }

    @Test
    void clearDropsAllHistoryForTheChat() {
        service.append("chat", ChatTurn.user("hello"));
        service.clear("chat");
        assertTrue(service.get("chat").isEmpty());
    }
}
