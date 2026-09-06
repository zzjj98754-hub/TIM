package com.tuling.tim.server.controller;

import com.tuling.tim.server.group.GroupMessageService;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.ReliableMessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HTTP shortcuts make the distributed flow demonstrable without modifying the terminal client. */
@RestController
@RequestMapping("/demo")
public class DemoMessageController {
    private final ReliableMessageService messages;
    private final GroupMessageService groups;
    public DemoMessageController(ReliableMessageService messages, GroupMessageService groups) { this.messages = messages; this.groups = groups; }
    @PostMapping("/messages") public Map<String, String> send(@RequestBody ChatMessage message) { messages.accept(message); return Map.of("status", "accepted", "messageId", message.getMessageId()); }
    @GetMapping("/offline/{userId}") public List<String> offline(@PathVariable long userId, @RequestParam(defaultValue = "0") long cursor, @RequestParam(defaultValue = "20") int limit) { return messages.pullOffline(userId, cursor, Math.min(limit, 100)); }
    @PutMapping("/groups/{groupId}/members/{userId}") public Map<String, String> member(@PathVariable long groupId, @PathVariable long userId) { groups.addMember(groupId, userId); return Map.of("status", "added"); }
    @PostMapping("/groups/{groupId}/messages") public Map<String, String> group(@PathVariable long groupId, @RequestBody ChatMessage message) { message.setGroupId(groupId); if (message.getCreatedAt() == 0) message.setCreatedAt(System.currentTimeMillis()); return Map.of("strategy", groups.send(message)); }
    @GetMapping("/groups/{groupId}/messages/{userId}") public List<String> pullGroup(@PathVariable long groupId, @PathVariable long userId, @RequestParam(defaultValue = "0") long cursor, @RequestParam(defaultValue = "20") int limit) { return groups.pull(groupId, userId, cursor, Math.min(limit, 100)); }
}
