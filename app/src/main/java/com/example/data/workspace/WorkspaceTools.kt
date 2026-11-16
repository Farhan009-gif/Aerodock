package com.example.data.workspace

import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.provider.McpToolDefinition
import org.json.JSONObject
import java.io.File

/**
 * Built-in Workspace tools: the model's file hands. Five tools over one
 * on-device workspace folder (the repository passes `filesDir/workspace`):
 *
 *  list_files   — show the project tree
 *  read_file    — read a text file, optionally a line range
 *  write_file   — create or overwrite a text file
 *  edit_file    — replace one exact, unique passage of text
 *  search_files — find a text string across the workspace
 *
 * Reads are marked read-only; writes are not, and the parent tool loop shows
 * the approval sheet before any write runs (the run_code rule: a model never
 * changes files silently). Every path is relative to the workspace root and
 * canonicalised, so a call cannot escape the folder with ".." or an absolute
 * path. Subagents do not inherit these tools in this step — the parent loop
 * is the only executor, so the approval sheet always has a user in front of
 * it. Identity and schemas live here; execution is a plain function over a
 * root [File], and logging lives in OmniRepository next to the other
 * built-in executors.
 */
object WorkspaceTools {

    const val WORKSPACE_SERVER_ID = "builtin_workspace"
    const val WORKSPACE_SERVER_NAME = "Workspace"

    const val LIST_FILES_TOOL_NAME = "list_files"
    const val READ_FILE_TOOL_NAME = "read_file"
    const val WRITE_FILE_TOOL_NAME = "write_file"
    const val EDIT_FILE_TOOL_NAME = "edit_file"
    const val SEARCH_FILES_TOOL_NAME = "search_files"
    const val DELETE_FILE_TOOL_NAME = "delete_file"
    const val MOVE_FILE_TOOL_NAME = "move_file"
    const val UPDATE_PLAN_TOOL_NAME = "update_plan"

    // Git tool names live here so isWorkspaceTool/isWriteTool cover the whole
    // family; their schemas and execution live in WorkspaceGit (GitHub API).
    const val GIT_CLONE_TOOL_NAME = "git_clone"
    const val GIT_STATUS_TOOL_NAME = "git_status"
    const val GIT_DIFF_TOOL_NAME = "git_diff"
    const val GIT_COMMIT_PUSH_TOOL_NAME = "git_commit_push"
    const val GIT_PULL_TOOL_NAME = "git_pull"
    const val BUILD_STATUS_TOOL_NAME = "build_status"
    const val ADD_LESSON_TOOL_NAME = "add_lesson"
    const val READ_URL_TOOL_NAME = "read_url"
    const val UNDO_TOOL_NAME = "undo_last_change"

    const val SNAPSHOTS_DIR_NAME = ".snapshots"

    private const val MAX_LIST_ENTRIES = 300
    private const val MAX_LIST_DEPTH = 6
    private const val MAX_READ_CHARS = 48_000
    private const val MAX_WRITE_CHARS = 200_000
    private const val MAX_SEARCH_MATCHES = 100
    private const val MAX_SEARCH_FILE_BYTES = 1_000_000L
    private const val MAX_SNAPSHOT_BYTES = 20L * 1024 * 1024
    private const val MAX_SNAPSHOTS_KEPT = 20

    private val TOOL_NAMES = setOf(
        LIST_FILES_TOOL_NAME,
        READ_FILE_TOOL_NAME,
        WRITE_FILE_TOOL_NAME,
        EDIT_FILE_TOOL_NAME,
        SEARCH_FILES_TOOL_NAME,
        DELETE_FILE_TOOL_NAME,
        MOVE_FILE_TOOL_NAME,
        UPDATE_PLAN_TOOL_NAME,
        GIT_CLONE_TOOL_NAME,
        GIT_STATUS_TOOL_NAME,
        GIT_DIFF_TOOL_NAME,
        GIT_COMMIT_PUSH_TOOL_NAME,
        GIT_PULL_TOOL_NAME,
        BUILD_STATUS_TOOL_NAME,
        ADD_LESSON_TOOL_NAME,
        READ_URL_TOOL_NAME,
        UNDO_TOOL_NAME
    )

    private val WRITE_TOOL_NAMES = setOf(
        WRITE_FILE_TOOL_NAME,
        EDIT_FILE_TOOL_NAME,
        DELETE_FILE_TOOL_NAME,
        MOVE_FILE_TOOL_NAME,
        GIT_CLONE_TOOL_NAME,
        GIT_COMMIT_PUSH_TOOL_NAME,
        GIT_PULL_TOOL_NAME,
        UNDO_TOOL_NAME
    )

    fun isWorkspaceTool(toolName: String): Boolean = toolName in TOOL_NAMES

    fun isWriteTool(toolName: String): Boolean = toolName in WRITE_TOOL_NAMES

    fun buildWorkspaceServerEntity(): McpServerEntity {
        return McpServerEntity(
            id = WORKSPACE_SERVER_ID,
            name = WORKSPACE_SERVER_NAME,
            url = "builtin://workspace",
            transport = McpTransportType.STREAMABLE_HTTP.name,
            authHeaderName = "",
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Built-in on-device project workspace (files live in the app's workspace folder)"
        )
    }

    fun buildToolEntities(): List<McpToolEntity> {
        fun entity(name: String, description: String, schema: String, readOnly: Boolean) =
            McpToolEntity(
                id = "$WORKSPACE_SERVER_ID:$name",
                serverId = WORKSPACE_SERVER_ID,
                serverName = WORKSPACE_SERVER_NAME,
                toolName = name,
                description = description,
                inputSchemaJson = schema,
                isReadOnlyHint = readOnly,
                isEnabled = true
            )
        return listOf(
            entity(
                LIST_FILES_TOOL_NAME,
                "List the files and folders of the on-device project workspace. Paths everywhere " +
                    "in these tools are relative to the workspace root. Use this first to see " +
                    "what a project contains.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Folder to list, relative to the workspace root. Omit for the root."}}}""",
                readOnly = true
            ),
            entity(
                READ_FILE_TOOL_NAME,
                "Read a text file from the workspace. Optionally pass start_line and max_lines " +
                    "to read one section of a long file instead of the whole file.",
                """{"type":"object","properties":{"path":{"type":"string","description":"File path relative to the workspace root."},"start_line":{"type":"integer","description":"Optional 1-based first line to return."},"max_lines":{"type":"integer","description":"Optional maximum number of lines to return (default 400)."}},"required":["path"]}""",
                readOnly = true
            ),
            entity(
                WRITE_FILE_TOOL_NAME,
                "Create a text file in the workspace, or overwrite it completely. Parent folders " +
                    "are created as needed. For large files, write the first chunk here and extend " +
                    "it with edit_file appends, keeping each call modest. The user approves every write.",
                """{"type":"object","properties":{"path":{"type":"string","description":"File path relative to the workspace root."},"content":{"type":"string","description":"The complete new content of the file."}},"required":["path","content"]}""",
                readOnly = false
            ),
            entity(
                EDIT_FILE_TOOL_NAME,
                "Replace one exact passage of a workspace file. old_text must appear exactly once " +
                    "in the file — include enough surrounding context to make it unique. The user " +
                    "approves every edit.",
                """{"type":"object","properties":{"path":{"type":"string","description":"File path relative to the workspace root."},"old_text":{"type":"string","description":"The exact existing text to replace. Must occur exactly once."},"new_text":{"type":"string","description":"The replacement text."}},"required":["path","old_text","new_text"]}""",
                readOnly = false
            ),
            entity(
                SEARCH_FILES_TOOL_NAME,
                "Search for an exact text string in every text file of the workspace and get " +
                    "back file:line matches. Use it to find where something is defined or used.",
                """{"type":"object","properties":{"query":{"type":"string","description":"The exact text to search for."},"path":{"type":"string","description":"Optional folder (or file) to search within, relative to the workspace root."}},"required":["query"]}""",
                readOnly = true
            ),
            entity(
                DELETE_FILE_TOOL_NAME,
                "Delete a file or a whole folder from the workspace. The user approves every " +
                    "deletion, and deletion cannot be undone.",
                """{"type":"object","properties":{"path":{"type":"string","description":"File or folder path relative to the workspace root."}},"required":["path"]}""",
                readOnly = false
            ),
            entity(
                MOVE_FILE_TOOL_NAME,
                "Move or rename a file or folder inside the workspace. The destination must not " +
                    "already exist. The user approves every move.",
                """{"type":"object","properties":{"from":{"type":"string","description":"Current path relative to the workspace root."},"to":{"type":"string","description":"New path relative to the workspace root."}},"required":["from","to"]}""",
                readOnly = false
            ),
            entity(
                UPDATE_PLAN_TOOL_NAME,
                "Write or replace your plan for this conversation: the checklist of steps for " +
                    "the task you are working on. Send the WHOLE list every time — items you " +
                    "leave out are removed. Keep exactly one item in_progress while you work, " +
                    "mark items done as you finish them, and update the plan before starting a " +
                    "long task. The plan is shown back to you at the start of every later turn " +
                    "of this conversation, so a long session never loses the thread.",
                """{"type":"object","properties":{"items":{"type":"array","description":"The full plan, in order.","items":{"type":"object","properties":{"text":{"type":"string","description":"One step, phrased as an action."},"status":{"type":"string","enum":["pending","in_progress","done"],"description":"The step's state."}},"required":["text","status"]}}},"required":["items"]}""",
                readOnly = true
            ),
            entity(
                ADD_LESSON_TOOL_NAME,
                "Record one lesson about a workspace project in its LESSONS.md file — something " +
                    "you learned the hard way while working on it (a build quirk, an API limit, " +
                    "a pattern the codebase uses) that a future session should know before " +
                    "touching the project. One or two sentences, concrete. Read the project's " +
                    "LESSONS.md with read_file before starting work on a cloned project.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Project folder inside the workspace."},"lesson":{"type":"string","description":"The lesson, one or two concrete sentences."}},"required":["path","lesson"]}""",
                readOnly = false
            ),
            entity(
                UNDO_TOOL_NAME,
                "Undo the most recent workspace file change (write, edit, delete or move). " +
                    "Every change first saves a snapshot of what was there; this restores the " +
                    "newest snapshot and removes it, so repeated calls walk backwards. The user " +
                    "approves every undo.",
                """{"type":"object","properties":{}}""",
                readOnly = false
            )
        ) + WorkspaceGit.buildToolEntities() + WorkspaceWeb.buildToolEntity()
    }

    fun buildToolDefinitions(): List<McpToolDefinition> {
        return buildToolEntities().map { entity ->
            McpToolDefinition(
                serverId = entity.serverId,
                serverName = entity.serverName,
                toolName = entity.toolName,
                description = entity.description,
                inputSchemaJson = entity.inputSchemaJson,
                isReadOnlyHint = entity.isReadOnlyHint
            )
        }
    }

    /**
     * Runs one workspace tool against [root]. Returns (model-facing text,
     * success). Every failure is a plain "Error: ..." result the model can
     * react to, never an exception.
     */
    fun execute(root: File, toolName: String, argumentsJson: String): Pair<String, Boolean> {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return "Error: arguments were not valid JSON." to false
        return runCatching {
            when (toolName) {
                LIST_FILES_TOOL_NAME -> listFiles(root, args.optString("path"))
                READ_FILE_TOOL_NAME -> readFile(
                    root,
                    args.optString("path"),
                    args.optInt("start_line", 0),
                    args.optInt("max_lines", 0)
                )
                WRITE_FILE_TOOL_NAME -> writeFile(root, args.optString("path"), args.optString("content"))
                EDIT_FILE_TOOL_NAME -> editFile(
                    root,
                    args.optString("path"),
                    args.optString("old_text"),
                    args.optString("new_text")
                )
                SEARCH_FILES_TOOL_NAME -> searchFiles(root, args.optString("query"), args.optString("path"))
                DELETE_FILE_TOOL_NAME -> deleteFile(root, args.optString("path"))
                MOVE_FILE_TOOL_NAME -> moveFile(root, args.optString("from"), args.optString("to"))
                ADD_LESSON_TOOL_NAME -> addLesson(root, args.optString("path"), args.optString("lesson"))
                UNDO_TOOL_NAME -> undoLastChange(root)
                else -> "Error: tool '$toolName' is executed by the repository, not the file executor." to false
            }
        }.getOrElse { e ->
            "Error: ${e.message ?: e.javaClass.simpleName}" to false
        }
    }

    // Resolves [relativePath] inside [root]; null when it escapes the root.
    // internal (not private) so WorkspaceGit resolves project paths the same way.
    internal fun resolveInside(root: File, relativePath: String): File? {
        val rootCanon = root.canonicalFile
        val target = File(rootCanon, relativePath.trim()).canonicalFile
        val inside = target.path == rootCanon.path ||
            target.path.startsWith(rootCanon.path + File.separator)
        return if (inside) target else null
    }

    private fun relativeDisplay(root: File, target: File): String {
        val rel = target.canonicalFile.relativeTo(root.canonicalFile).path
        return rel.ifEmpty { "." }
    }

    private fun listFiles(root: File, path: String): Pair<String, Boolean> {
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (!target.exists()) {
            return if (path.isBlank()) {
                "The workspace is empty. Use write_file to create the first file." to true
            } else {
                "Error: '$path' does not exist in the workspace." to false
            }
        }
        if (!target.isDirectory) return "Error: '$path' is a file, not a folder." to false
        val entries = target.walkTopDown()
            .maxDepth(MAX_LIST_DEPTH)
            .filter {
                it.path != target.path &&
                    !it.path.contains("${File.separator}.git${File.separator}") &&
                    !it.path.contains("${File.separator}$SNAPSHOTS_DIR_NAME${File.separator}") &&
                    it.name != SNAPSHOTS_DIR_NAME
            }
            .take(MAX_LIST_ENTRIES + 1)
            .toList()
        if (entries.isEmpty()) return "Folder '${path.ifBlank { "." }}' is empty." to true
        val text = buildString {
            appendLine("Workspace listing for ${path.ifBlank { "." }} (${entries.size} entries):")
            for (entry in entries.sortedBy { it.path }) {
                val rel = relativeDisplay(root, entry)
                if (entry.isDirectory) {
                    appendLine("$rel/")
                } else {
                    appendLine("$rel (${entry.length()} B)")
                }
            }
            if (entries.size > MAX_LIST_ENTRIES) {
                appendLine("[Listing truncated at $MAX_LIST_ENTRIES entries — list a subfolder for the rest.]")
            }
        }
        return text.trimEnd() to true
    }

    private fun readFile(root: File, path: String, startLine: Int, maxLines: Int): Pair<String, Boolean> {
        if (path.isBlank()) return "Error: read_file needs a path." to false
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (!target.exists()) return "Error: '$path' does not exist in the workspace." to false
        if (!target.isFile) return "Error: '$path' is a folder, not a file." to false
        val bytes = target.readBytes()
        if (bytes.take(8192).any { it == 0.toByte() }) {
            return "Error: '$path' looks like a binary file; read_file only reads text." to false
        }
        val lines = bytes.toString(Charsets.UTF_8).split("\n")
        val from = (if (startLine > 0) startLine else 1).coerceIn(1, maxOf(1, lines.size))
        val count = if (maxLines > 0) maxLines else 400
        val to = minOf(lines.size, from - 1 + count)
        var slice = lines.subList(from - 1, to).joinToString("\n")
        var truncatedNote = ""
        if (slice.length > MAX_READ_CHARS) {
            slice = slice.substring(0, MAX_READ_CHARS)
            truncatedNote = "\n[Truncated at $MAX_READ_CHARS characters — read a smaller line range.]"
        } else if (to < lines.size) {
            truncatedNote = "\n[Showing lines $from–$to of ${lines.size} — pass start_line=${to + 1} to continue.]"
        }
        return "$path (lines $from–$to of ${lines.size}):\n$slice$truncatedNote" to true
    }

    private fun writeFile(root: File, path: String, content: String): Pair<String, Boolean> {
        if (path.isBlank()) return "Error: write_file needs a path." to false
        if (content.length > MAX_WRITE_CHARS) {
            return "Error: content is ${content.length} characters; the limit per write is " +
                "$MAX_WRITE_CHARS. Write the first chunk, then extend the file with edit_file." to false
        }
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (target.isDirectory) return "Error: '$path' is a folder, not a file." to false
        target.parentFile?.mkdirs()
        val existed = target.exists()
        snapshotBeforeChange(root, WRITE_FILE_TOOL_NAME, path, target)
        target.writeText(content, Charsets.UTF_8)
        val lineCount = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
        return "${if (existed) "Overwrote" else "Created"} $path (${content.length} characters, $lineCount lines)." to true
    }

    private fun editFile(root: File, path: String, oldText: String, newText: String): Pair<String, Boolean> {
        if (path.isBlank()) return "Error: edit_file needs a path." to false
        if (oldText.isEmpty()) return "Error: edit_file needs a non-empty old_text." to false
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (!target.exists() || !target.isFile) return "Error: '$path' does not exist in the workspace." to false
        val original = target.readText(Charsets.UTF_8)
        val occurrences = original.split(oldText).size - 1
        if (occurrences == 0) {
            return "Error: old_text was not found in $path. Read the file and copy the exact text, including whitespace." to false
        }
        if (occurrences > 1) {
            return "Error: old_text appears $occurrences times in $path. Include more surrounding context so it matches exactly one place." to false
        }
        snapshotBeforeChange(root, EDIT_FILE_TOOL_NAME, path, target)
        target.writeText(original.replace(oldText, newText), Charsets.UTF_8)
        return "Edited $path: replaced ${oldText.length} characters with ${newText.length} characters." to true
    }

    private fun deleteFile(root: File, path: String): Pair<String, Boolean> {
        if (path.isBlank()) return "Error: delete_file needs a path." to false
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (target.path == root.canonicalFile.path) {
            return "Error: refusing to delete the whole workspace root." to false
        }
        if (!target.exists()) return "Error: '$path' does not exist in the workspace." to false
        val fileCount = if (target.isDirectory) {
            target.walkTopDown().count { it.isFile }
        } else {
            1
        }
        val snapshotted = snapshotBeforeChange(root, DELETE_FILE_TOOL_NAME, path, target)
        val deleted = target.deleteRecursively()
        return if (deleted) {
            "Deleted $path ($fileCount file(s))." +
                if (!snapshotted) " Note: no undo snapshot was saved (too large), so this delete cannot be undone." else ""
                to true
        } else {
            "Error: could not fully delete '$path'." to false
        }
    }

    private fun moveFile(root: File, from: String, to: String): Pair<String, Boolean> {
        if (from.isBlank() || to.isBlank()) return "Error: move_file needs both from and to." to false
        val source = resolveInside(root, from) ?: return "Error: 'from' escapes the workspace." to false
        val dest = resolveInside(root, to) ?: return "Error: 'to' escapes the workspace." to false
        if (!source.exists()) return "Error: '$from' does not exist in the workspace." to false
        if (dest.exists()) return "Error: '$to' already exists — delete it first or choose another name." to false
        dest.parentFile?.mkdirs()
        snapshotMove(root, from, to)
        val moved = source.renameTo(dest) || (source.copyRecursively(dest, overwrite = false) && source.deleteRecursively())
        return if (moved) {
            "Moved $from to $to." to true
        } else {
            "Error: could not move '$from' to '$to'." to false
        }
    }

    // ---- Undo snapshots -------------------------------------------------------
    // Before a write/edit/delete, the target's current state is copied into
    // <root>/.snapshots/<timestamp>/ with a meta.json (tool, path, kind:
    // absent | file | tree | move). undo_last_change restores the newest
    // snapshot and drops it, so repeated undos walk backwards. The folder is
    // hidden from list/search and skipped by git. Moves snapshot only their
    // from/to (a move never destroys content, so renaming back IS the undo).

    private fun snapshotsDir(root: File): File = File(root, SNAPSHOTS_DIR_NAME)

    private fun newSnapshotDir(root: File): File {
        val dir = File(
            snapshotsDir(root),
            "${System.currentTimeMillis()}-${System.nanoTime() % 100_000}"
        )
        dir.mkdirs()
        return dir
    }

    private fun pruneSnapshots(root: File) {
        val dirs = snapshotsDir(root).listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            .orEmpty()
        if (dirs.size > MAX_SNAPSHOTS_KEPT) {
            dirs.take(dirs.size - MAX_SNAPSHOTS_KEPT).forEach { it.deleteRecursively() }
        }
    }

    private fun snapshotBeforeChange(root: File, tool: String, relPath: String, target: File): Boolean {
        return runCatching {
            val kind = when {
                !target.exists() -> "absent"
                target.isDirectory -> "tree"
                else -> "file"
            }
            if (kind == "file" && target.length() > MAX_SNAPSHOT_BYTES) return false
            if (kind == "tree") {
                var total = 0L
                target.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
                if (total > MAX_SNAPSHOT_BYTES) return false
            }
            val dir = newSnapshotDir(root)
            when (kind) {
                "file" -> target.copyTo(File(dir, "content"), overwrite = true)
                "tree" -> target.copyRecursively(File(dir, "tree"), overwrite = true)
            }
            val meta = JSONObject()
                .put("tool", tool)
                .put("path", relPath)
                .put("kind", kind)
            File(dir, "meta.json").writeText(meta.toString(), Charsets.UTF_8)
            pruneSnapshots(root)
            true
        }.getOrDefault(false)
    }

    private fun snapshotMove(root: File, from: String, to: String) {
        runCatching {
            val dir = newSnapshotDir(root)
            val meta = JSONObject()
                .put("tool", MOVE_FILE_TOOL_NAME)
                .put("path", from)
                .put("kind", "move")
                .put("from", from)
                .put("to", to)
            File(dir, "meta.json").writeText(meta.toString(), Charsets.UTF_8)
            pruneSnapshots(root)
        }
    }

    private fun newestSnapshot(root: File): Pair<File, JSONObject>? {
        val dirs = snapshotsDir(root).listFiles()
            ?.filter { it.isDirectory && File(it, "meta.json").isFile }
            ?.sortedBy { it.name }
            .orEmpty()
        val newest = dirs.lastOrNull() ?: return null
        val meta = runCatching {
            JSONObject(File(newest, "meta.json").readText(Charsets.UTF_8))
        }.getOrNull() ?: return null
        return newest to meta
    }

    private fun undoLastChange(root: File): Pair<String, Boolean> {
        val (snapshot, meta) = newestSnapshot(root)
            ?: return "Nothing to undo — no saved snapshots." to true
        val tool = meta.optString("tool")
        val relPath = meta.optString("path")
        val target = resolveInside(root, relPath)
            ?: return "Error: the snapshot path escapes the workspace." to false
        val restored = runCatching {
            when (meta.optString("kind")) {
                "absent" -> target.deleteRecursively()
                "file" -> {
                    target.deleteRecursively()
                    target.parentFile?.mkdirs()
                    File(snapshot, "content").copyTo(target, overwrite = true)
                }
                "tree" -> {
                    target.deleteRecursively()
                    File(snapshot, "tree").copyRecursively(target, overwrite = true)
                }
                "move" -> {
                    val from = resolveInside(root, meta.optString("from"))
                    val to = resolveInside(root, meta.optString("to"))
                    if (from != null && to != null && to.exists() && !from.exists()) {
                        from.parentFile?.mkdirs()
                        if (!to.renameTo(from)) {
                            to.copyRecursively(from, overwrite = false)
                            to.deleteRecursively()
                        }
                    }
                }
                else -> return "Error: unknown snapshot kind in the newest snapshot." to false
            }
            true
        }.getOrDefault(false)
        if (!restored) return "Error: could not restore the snapshot for $relPath." to false
        snapshot.deleteRecursively()
        return "Undid $tool on $relPath — restored to how it was before that change." to true
    }

    private fun describeNewestSnapshot(root: File): String? {
        val (_, meta) = newestSnapshot(root) ?: return null
        return "the last change (${meta.optString("tool")} on ${meta.optString("path")})"
    }

    /**
     * Human-readable "what will change" text for the approval sheet, shown
     * instead of the raw arguments JSON for workspace write tools. Pure file
     * reads over the workspace; null when no preview applies.
     */
    fun buildApprovalPreview(root: File, toolName: String, argumentsJson: String): String? {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return null
        return runCatching {
            when (toolName) {
                WRITE_FILE_TOOL_NAME -> {
                    val path = args.optString("path")
                    val content = args.optString("content")
                    val target = resolveInside(root, path)
                    val header = if (target?.isFile == true) {
                        "Overwrites $path (currently ${target.length()} bytes). New content:"
                    } else {
                        "Creates $path. Content:"
                    }
                    val lines = content.split("\n")
                    header + "\n" + lines.take(40).joinToString("\n") +
                        if (lines.size > 40) "\n… (${lines.size} lines, ${content.length} characters total)" else ""
                }
                EDIT_FILE_TOOL_NAME -> {
                    val path = args.optString("path")
                    val oldText = args.optString("old_text")
                    val newText = args.optString("new_text")
                    val target = resolveInside(root, path)
                    val current = if (target?.isFile == true) {
                        target.readText(Charsets.UTF_8)
                    } else {
                        null
                    }
                    val occurrences = if (current != null && oldText.isNotEmpty()) {
                        current.split(oldText).size - 1
                    } else {
                        0
                    }
                    buildString {
                        appendLine("In $path, replace:")
                        appendLine(oldText.take(600).lines().joinToString("\n") { "- $it" })
                        appendLine("with:")
                        appendLine(newText.take(600).lines().joinToString("\n") { "+ $it" })
                        if (occurrences != 1) {
                            appendLine("Note: that exact text currently appears $occurrences times in the file, so this edit would fail — it must appear exactly once.")
                        }
                    }.trimEnd()
                }
                DELETE_FILE_TOOL_NAME -> {
                    val path = args.optString("path")
                    val target = resolveInside(root, path)
                    when {
                        target == null -> "Deletes $path."
                        !target.exists() -> "$path does not exist — this delete would fail."
                        target.isDirectory -> "Deletes the folder $path and everything in it " +
                            "(${target.walkTopDown().count { it.isFile }} files). A snapshot is saved first when it fits."
                        else -> "Deletes the file $path (${target.length()} bytes). A snapshot is saved first."
                    }
                }
                MOVE_FILE_TOOL_NAME ->
                    "Moves ${args.optString("from")} to ${args.optString("to")}."
                GIT_CLONE_TOOL_NAME ->
                    "Clones ${args.optString("repo")} into the workspace folder " +
                        "'${args.optString("path").ifBlank { "(named after the repo)" }}' — downloads the project's files."
                GIT_COMMIT_PUSH_TOOL_NAME ->
                    WorkspaceGit.changeSummary(root, args.optString("path"))
                        ?: "Commits and pushes the project at '${args.optString("path")}'."
                GIT_PULL_TOOL_NAME -> {
                    val description = WorkspaceGit.describeProject(root, args.optString("path"))
                    "Pulls the latest ${description ?: "version from GitHub"} into " +
                        "'${args.optString("path")}' (fast-forward). Unpushed local changes are " +
                        "kept unless the same file changed on GitHub."
                }
                UNDO_TOOL_NAME ->
                    describeNewestSnapshot(root)?.let { "Restores the workspace to before $it." }
                        ?: "No snapshot is saved — nothing would change."
                else -> null
            }
        }.getOrNull()
    }

    private fun addLesson(root: File, path: String, lesson: String): Pair<String, Boolean> {
        if (path.isBlank()) return "Error: add_lesson needs the project path." to false
        val cleanLesson = lesson.trim().replace("\n", " ")
        if (cleanLesson.isBlank()) return "Error: add_lesson needs the lesson text." to false
        val dir = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (!dir.isDirectory) return "Error: '$path' is not a workspace folder." to false
        val lessonsFile = File(dir, "LESSONS.md")
        if (!lessonsFile.exists()) {
            lessonsFile.writeText(
                "# Project lessons\n\nHard-won notes about this project. Read before working on it.\n\n",
                Charsets.UTF_8
            )
        }
        lessonsFile.appendText("- $cleanLesson\n", Charsets.UTF_8)
        return "Lesson saved to $path/LESSONS.md." to true
    }

    private fun searchFiles(root: File, query: String, path: String): Pair<String, Boolean> {
        if (query.isBlank()) return "Error: search_files needs a query." to false
        val target = resolveInside(root, path) ?: return "Error: path escapes the workspace." to false
        if (!target.exists()) return "Error: '${path.ifBlank { "." }}' does not exist in the workspace." to false
        val files = if (target.isFile) {
            listOf(target)
        } else {
            target.walkTopDown()
                .filter {
                    it.isFile &&
                        !it.path.contains("${File.separator}.git${File.separator}") &&
                        !it.path.contains("${File.separator}$SNAPSHOTS_DIR_NAME${File.separator}")
                }
                .toList()
        }
        val matches = mutableListOf<String>()
        for (file in files) {
            if (matches.size >= MAX_SEARCH_MATCHES) break
            if (file.length() > MAX_SEARCH_FILE_BYTES) continue
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            if (bytes.take(8192).any { it == 0.toByte() }) continue
            val rel = relativeDisplay(root, file)
            bytes.toString(Charsets.UTF_8).split("\n").forEachIndexed { index, line ->
                if (matches.size < MAX_SEARCH_MATCHES && line.contains(query)) {
                    val preview = line.trim().take(160)
                    matches.add("$rel:${index + 1}: $preview")
                }
            }
        }
        if (matches.isEmpty()) return "No matches for \"$query\" in the workspace." to true
        val text = buildString {
            appendLine("Matches for \"$query\" (${matches.size}):")
            matches.forEach { appendLine(it) }
            if (matches.size >= MAX_SEARCH_MATCHES) {
                appendLine("[Stopped at $MAX_SEARCH_MATCHES matches — narrow the search with a path or a longer query.]")
            }
        }
        return text.trimEnd() to true
    }
}
