package com.aiteacher.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * In-memory conversation history for ask-the-teacher WebSocket chats.
 * Keyed by client-supplied chat id (one per lesson session). Capped so a
 * long-running lesson cannot grow unbounded. History is also replayed to the
 * client on reconnect and compacted into the AI prompt for continuity.
 */
@Service
public class ConversationHistoryService {

    /** Maximum turns (user + assistant messages) retained per chat. */
    static final int MAX_TURNS_PER_CHAT = 60;

    /** How many recent turns are replayed into the AI prompt. */
    static final int PROMPT_CONTEXT_TURNS = 6;

    /** One message in a chat: "user" or "assistant". */
    public record ChatTurn(String role, String text, long at) {
        public static ChatTurn user(String text) {
            return new ChatTurn("user", text, System.currentTimeMillis());
        }

        public static ChatTurn assistant(String text) {
            return new ChatTurn("assistant", text, System.currentTimeMillis());
        }
    }

    private final Map<String, List<ChatTurn>> history = new ConcurrentHashMap<>();

    /** Append a turn, trimming the oldest entries beyond the cap. */
    public void append(String chatId, ChatTurn turn) {
        history.compute(chatId, (key, turns) -> {
            List<ChatTurn> updated = (turns == null) ? new ArrayList<>() : new ArrayList<>(turns);
            updated.add(turn);
            if (updated.size() > MAX_TURNS_PER_CHAT) {
                updated = new ArrayList<>(updated.subList(updated.size() - MAX_TURNS_PER_CHAT, updated.size()));
            }
            return updated;
        });
    }

    /** Immutable snapshot of the chat so far (oldest first); never null. */
    public List<ChatTurn> get(String chatId) {
        List<ChatTurn> turns = history.get(chatId);
        return (turns == null) ? List.of() : List.copyOf(turns);
    }

    /** The most recent turns formatted for the AI prompt, e.g. "Student: …". */
    public List<String> recentTurnsForPrompt(String chatId) {
        List<ChatTurn> turns = get(chatId);
        int from = Math.max(0, turns.size() - PROMPT_CONTEXT_TURNS);
        List<String> lines = new ArrayList<>();
        for (ChatTurn turn : turns.subList(from, turns.size())) {
            lines.add(("user".equals(turn.role()) ? "Student: " : "Teacher: ") + turn.text());
        }
        return lines;
    }

    /** Drop all history for a chat (client-initiated clear). */
    public void clear(String chatId) {
        history.remove(chatId);
    }
}
