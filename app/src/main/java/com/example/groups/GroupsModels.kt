package com.example.groups

/**
 * Groups (Phase 1) data models. These mirror the Firestore documents in
 * `groups/{groupId}`, `groups/{groupId}/messages/{messageId}` and
 * `stats/global`. Only the fields listed here are ever written to Firestore —
 * no chat content, provider data or API keys leave the phone via Groups.
 */
data class GroupRoom(
    val id: String,
    val name: String,
    val ownerUid: String,
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val createdAt: Long = 0L,
    val lastMessageAt: Long = 0L,
    val messageCount: Long = 0L
)

data class GroupMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val createdAt: Long = 0L
)

data class GroupStats(
    val totalUsers: Long = 0L,
    val totalGroups: Long = 0L,
    val totalMessages: Long = 0L
)
