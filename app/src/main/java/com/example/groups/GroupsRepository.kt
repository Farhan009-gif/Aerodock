package com.example.groups

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Firestore access for Groups (Phase 1).
 *
 * Layout:
 * - users/{uid}                 profile card written on sign-in
 * - groups/{groupId}            one group (members, invite code, counters)
 * - groups/{groupId}/messages   the discussion thread
 * - stats/global                app-wide counters for the admin page
 * - config/admin                { uids: [...] } — console-managed admin list
 *
 * Fail-soft contract: when Firebase is unavailable every suspend call quietly
 * no-ops or fails its Result, and every flow emits its empty default.
 */
class GroupsRepository {

    private val db: FirebaseFirestore?
        get() = GroupsFirebase.firestore()

    // ---------------------------------------------------------------- users

    /** Upserts users/{uid}; bumps stats/global.totalUsers only for a brand-new doc. */
    suspend fun upsertCurrentUser(user: FirebaseUser) {
        val firestore = db ?: return
        runCatching {
            val ref = firestore.collection("users").document(user.uid)
            val getTask = ref.get()
            getTask.awaitCompletion()
            val isNewUser = !getTask.result.exists()
            ref.set(
                mapOf(
                    "displayName" to (user.displayName ?: ""),
                    "email" to (user.email ?: ""),
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).awaitCompletion()
            if (isNewUser) {
                runCatching { incrementStat("totalUsers") }
            }
        }
    }

    // --------------------------------------------------------------- groups

    suspend fun createGroup(user: FirebaseUser, rawName: String): Result<GroupRoom> {
        val firestore = db
            ?: return Result.failure(IllegalStateException("Groups unavailable"))
        val name = rawName.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("Give the group a name"))
        }
        return try {
            val ref = firestore.collection("groups").document()
            val code = generateInviteCode()
            ref.set(
                mapOf(
                    "name" to name,
                    "ownerUid" to user.uid,
                    "memberUids" to listOf(user.uid),
                    "inviteCode" to code,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "messageCount" to 0L
                )
            ).awaitCompletion()
            runCatching { incrementStat("totalGroups") }
            Result.success(
                GroupRoom(
                    id = ref.id,
                    name = name,
                    ownerUid = user.uid,
                    memberUids = listOf(user.uid),
                    inviteCode = code
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(user: FirebaseUser, rawCode: String): Result<GroupRoom> {
        val firestore = db
            ?: return Result.failure(IllegalStateException("Groups unavailable"))
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter an invite code"))
        }
        return try {
            val query = firestore.collection("groups")
                .whereEqualTo("inviteCode", code)
                .limit(1)
                .get()
            query.awaitCompletion()
            val doc = query.result.documents.firstOrNull()
                ?: return Result.failure(NoSuchElementException("No group found for that code"))
            val room = doc.toGroupRoom()
            if (user.uid in room.memberUids) {
                return Result.success(room)
            }
            doc.reference.update("memberUids", FieldValue.arrayUnion(user.uid)).awaitCompletion()
            Result.success(room.copy(memberUids = room.memberUids + user.uid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Groups the user belongs to, most recently active first. Sorting happens
     * client-side on purpose: arrayContains + orderBy on different fields
     * would need a composite Firestore index, and group counts are small.
     */
    fun groupsFlow(uid: String): Flow<List<GroupRoom>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection("groups")
                .whereArrayContains("memberUids", uid)
                .addSnapshotListener { snapshot, _ ->
                    val rooms = snapshot?.documents.orEmpty()
                        .map { it.toGroupRoom() }
                        .sortedByDescending { it.lastMessageAt }
                    trySend(rooms)
                }
            awaitClose { registration.remove() }
        }
    }

    // ------------------------------------------------------------- messages

    fun messagesFlow(groupId: String): Flow<List<GroupMessage>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection("groups").document(groupId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val messages = snapshot?.documents.orEmpty()
                        .map { it.toGroupMessage() }
                    trySend(messages)
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun sendMessage(user: FirebaseUser, groupId: String, rawText: String) {
        val firestore = db ?: return
        val text = rawText.trim()
        if (text.isEmpty()) return
        val groupRef = firestore.collection("groups").document(groupId)
        val messageRef = groupRef.collection("messages").document()
        val batch = firestore.batch()
        batch.set(
            messageRef,
            mapOf(
                "senderUid" to user.uid,
                "senderName" to (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email
                    ?: "Someone"),
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            groupRef,
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "messageCount" to FieldValue.increment(1)
            )
        )
        batch.commit().awaitCompletion()
        runCatching { incrementStat("totalMessages") }
    }

    // ---------------------------------------------------------------- admin

    /** Every group on the backend, most recently active first. Admin-only (rules). */
    fun allGroupsFlow(): Flow<List<GroupRoom>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection("groups")
                .addSnapshotListener { snapshot, _ ->
                    val rooms = snapshot?.documents.orEmpty()
                        .map { it.toGroupRoom() }
                        .sortedByDescending { it.lastMessageAt }
                    trySend(rooms)
                }
            awaitClose { registration.remove() }
        }
    }

    /** Latest messages across all groups, newest first. Admin-only (rules). */
    fun recentMessagesFlow(limit: Long = 25): Flow<List<GroupMessage>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collectionGroup("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, _ ->
                    trySend(snapshot?.documents.orEmpty().map { it.toGroupMessage() })
                }
            awaitClose { registration.remove() }
        }
    }

    /** Every signed-in user, alphabetical. Admin-only (rules). */
    fun allUsersFlow(): Flow<List<GroupUser>> {
        val firestore = db ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection("users")
                .addSnapshotListener { snapshot, _ ->
                    val users = snapshot?.documents.orEmpty()
                        .map { doc ->
                            GroupUser(
                                uid = doc.id,
                                displayName = doc.getString("displayName").orEmpty(),
                                email = doc.getString("email").orEmpty(),
                                lastActiveAt = doc.getTimestamp("lastActiveAt")?.toDate()?.time ?: 0L
                            )
                        }
                        .sortedBy { it.displayName.lowercase() }
                    trySend(users)
                }
            awaitClose { registration.remove() }
        }
    }

    // ---------------------------------------------------------------- stats

    /** App-wide counters. Rules allow only admins to read stats/global. */
    fun statsFlow(): Flow<GroupStats> {
        val firestore = db ?: return flowOf(GroupStats())
        return callbackFlow {
            val registration = firestore.collection("stats").document("global")
                .addSnapshotListener { snapshot, _ ->
                    trySend(
                        GroupStats(
                            totalUsers = snapshot?.getLong("totalUsers") ?: 0L,
                            totalGroups = snapshot?.getLong("totalGroups") ?: 0L,
                            totalMessages = snapshot?.getLong("totalMessages") ?: 0L
                        )
                    )
                }
            awaitClose { registration.remove() }
        }
    }

    /** True when the uid is listed in config/admin's `uids` array. Missing doc = no admins. */
    fun isAdminFlow(uid: String): Flow<Boolean> {
        val firestore = db ?: return flowOf(false)
        return callbackFlow {
            val registration = firestore.collection("config").document("admin")
                .addSnapshotListener { snapshot, _ ->
                    val uids = (snapshot?.get("uids") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()
                    trySend(uid in uids)
                }
            awaitClose { registration.remove() }
        }
    }

    // -------------------------------------------------------------- helpers

    private suspend fun incrementStat(field: String) {
        val firestore = db ?: return
        firestore.collection("stats").document("global")
            .set(mapOf(field to FieldValue.increment(1)), SetOptions.merge())
            .awaitCompletion()
    }

    private fun DocumentSnapshot.toGroupRoom(): GroupRoom {
        val created = getTimestamp("createdAt")?.toDate()?.time ?: 0L
        return GroupRoom(
            id = id,
            name = getString("name").orEmpty(),
            ownerUid = getString("ownerUid").orEmpty(),
            memberUids = (get("memberUids") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList(),
            inviteCode = getString("inviteCode").orEmpty(),
            createdAt = created,
            lastMessageAt = getTimestamp("lastMessageAt")?.toDate()?.time ?: created,
            messageCount = getLong("messageCount") ?: 0L
        )
    }

    private fun DocumentSnapshot.toGroupMessage(): GroupMessage = GroupMessage(
        id = id,
        groupId = reference.parent.parent?.id.orEmpty(),
        senderUid = getString("senderUid").orEmpty(),
        senderName = getString("senderName").orEmpty(),
        text = getString("text").orEmpty(),
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )

    private fun generateInviteCode(): String {
        val chars = INVITE_CODE_CHARS.toList()
        return (1..6).map { chars.random() }.joinToString("")
    }

    private companion object {
        /** No 0/O/1/I/L — codes are read aloud and typed by hand. */
        const val INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}
