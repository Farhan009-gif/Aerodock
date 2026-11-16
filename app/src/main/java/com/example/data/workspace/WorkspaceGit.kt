package com.example.data.workspace

import android.util.Base64
import com.example.data.git.GitRepoCloner
import com.example.data.local.McpToolEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Git for the Workspace, implemented over the GitHub API — the app carries
 * no git binary and no JGit, and a phone does not need one: clone is the
 * zipball download (the same trick GitRepoCloner uses for skill repos), and
 * commit+push is the Git Data API (blob -> tree -> commit -> ref), which
 * produces a perfectly ordinary git commit on GitHub.
 *
 * A cloned project folder holds a manifest, `.aerodock-repo.json`: owner,
 * repo, branch, the base commit/tree SHAs, and every file's git blob SHA at
 * clone (or last push) time. Status and diff are computed by re-hashing the
 * local files and comparing — no server round-trip needed for status.
 * Commit+push uploads only changed blobs, builds one tree on the base tree,
 * one commit on the base commit, and moves the branch ref. If the branch
 * moved on GitHub since the clone, the ref update is rejected and the tool
 * says so (pull/merge is a later step; re-clone is the sync path today).
 *
 * Everything in the project folder is committed except the manifest itself
 * and build-output folders (.git, build, .gradle, node_modules); files over
 * 10 MB are skipped with a note. All file modes are 100644.
 *
 * Identity/schemas live here next to WorkspaceTools (same built-in server);
 * execution needs the shared OkHttp client and the user's GitHub PAT from
 * the vault, both passed in by OmniRepository.
 */
object WorkspaceGit {

    const val MANIFEST_NAME = ".aerodock-repo.json"

    private const val API_ROOT = "https://api.github.com"
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private const val MAX_EXTRACT_BYTES = 100L * 1024 * 1024
    private const val MAX_DIFF_FILES = 5
    private const val MAX_DIFF_LINES = 1_200
    private val SKIPPED_DIRS = setOf(".git", "build", ".gradle", "node_modules")

    private val GIT_TOOL_NAMES = setOf(
        WorkspaceTools.GIT_CLONE_TOOL_NAME,
        WorkspaceTools.GIT_STATUS_TOOL_NAME,
        WorkspaceTools.GIT_DIFF_TOOL_NAME,
        WorkspaceTools.GIT_COMMIT_PUSH_TOOL_NAME,
        WorkspaceTools.GIT_PULL_TOOL_NAME,
        WorkspaceTools.BUILD_STATUS_TOOL_NAME
    )

    fun isGitTool(toolName: String): Boolean = toolName in GIT_TOOL_NAMES

    fun buildToolEntities(): List<McpToolEntity> {
        fun entity(name: String, description: String, schema: String, readOnly: Boolean) =
            McpToolEntity(
                id = "${WorkspaceTools.WORKSPACE_SERVER_ID}:$name",
                serverId = WorkspaceTools.WORKSPACE_SERVER_ID,
                serverName = WorkspaceTools.WORKSPACE_SERVER_NAME,
                toolName = name,
                description = description,
                inputSchemaJson = schema,
                isReadOnlyHint = readOnly,
                isEnabled = true
            )
        return listOf(
            entity(
                WorkspaceTools.GIT_CLONE_TOOL_NAME,
                "Clone a GitHub repository into the workspace so you can work on its files. " +
                    "Accepts \"owner/repo\" or a full GitHub URL. The clone lands in a folder " +
                    "named after the repo unless path says otherwise. Private repos need the " +
                    "user's GitHub token (Settings). The user approves every clone.",
                """{"type":"object","properties":{"repo":{"type":"string","description":"Repository as owner/repo or a GitHub URL."},"path":{"type":"string","description":"Optional destination folder inside the workspace."},"branch":{"type":"string","description":"Optional branch; defaults to the repo's default branch."}},"required":["repo"]}""",
                readOnly = false
            ),
            entity(
                WorkspaceTools.GIT_STATUS_TOOL_NAME,
                "Show what changed in a cloned workspace project since the clone or the last " +
                    "push: added, modified and deleted files. The path is the project folder " +
                    "inside the workspace.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Project folder inside the workspace, e.g. \"Aerodock\"."}},"required":["path"]}""",
                readOnly = true
            ),
            entity(
                WorkspaceTools.GIT_DIFF_TOOL_NAME,
                "Show the line-by-line changes of modified files in a cloned workspace project, " +
                    "against the version on GitHub. Use it to review edits before pushing.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Project folder inside the workspace."}},"required":["path"]}""",
                readOnly = true
            ),
            entity(
                WorkspaceTools.GIT_COMMIT_PUSH_TOOL_NAME,
                "Commit every change in a cloned workspace project and push it to GitHub as one " +
                    "commit (this app commits and pushes in a single step). Everything in the " +
                    "project folder is included except build-output folders and files over " +
                    "10 MB. The user approves every push.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Project folder inside the workspace."},"message":{"type":"string","description":"The commit message."}},"required":["path","message"]}""",
                readOnly = false
            ),
            entity(
                WorkspaceTools.GIT_PULL_TOOL_NAME,
                "Update a cloned workspace project to the latest commit on GitHub " +
                    "(fast-forward only). Files that changed on GitHub are downloaded; files " +
                    "deleted there are removed. Unpushed local changes are kept — unless the " +
                    "same file also changed on GitHub, in which case the pull is refused and " +
                    "says why. The user approves every pull.",
                """{"type":"object","properties":{"path":{"type":"string","description":"Project folder inside the workspace."}},"required":["path"]}""",
                readOnly = false
            ),
            entity(
                WorkspaceTools.BUILD_STATUS_TOOL_NAME,
                "Check the latest GitHub Actions build for a repository: pass repo as " +
                    "\"owner/repo\", or pass the workspace path of a cloned project. When the " +
                    "latest run failed, the failing job, step and the tail of its log come back " +
                    "so you can fix the cause.",
                """{"type":"object","properties":{"repo":{"type":"string","description":"Repository as owner/repo or a GitHub URL. Optional when path is given."},"path":{"type":"string","description":"Workspace project folder to read the repo from. Optional when repo is given."},"branch":{"type":"string","description":"Optional branch filter."}}}""",
                readOnly = true
            )
        )
    }

    fun execute(
        root: File,
        client: OkHttpClient,
        token: String?,
        toolName: String,
        argumentsJson: String
    ): Pair<String, Boolean> {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return "Error: arguments were not valid JSON." to false
        return runCatching {
            when (toolName) {
                WorkspaceTools.GIT_CLONE_TOOL_NAME -> gitClone(root, client, token, args)
                WorkspaceTools.GIT_STATUS_TOOL_NAME -> gitStatus(root, args)
                WorkspaceTools.GIT_DIFF_TOOL_NAME -> gitDiff(root, client, token, args)
                WorkspaceTools.GIT_COMMIT_PUSH_TOOL_NAME -> gitCommitPush(root, client, token, args)
                WorkspaceTools.GIT_PULL_TOOL_NAME -> gitPull(root, client, token, args)
                WorkspaceTools.BUILD_STATUS_TOOL_NAME -> buildStatus(root, client, token, args)
                else -> "Error: unknown git tool '$toolName'." to false
            }
        }.getOrElse { e ->
            "Error: ${e.message ?: e.javaClass.simpleName}" to false
        }
    }

    // ---- HTTP helpers -------------------------------------------------------

    private fun requestBuilder(token: String?, url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "OmniAI-Studio-Android")
            .header("Accept", "application/vnd.github+json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    /** Returns (HTTP code, body text). Never throws for HTTP errors. */
    private fun apiCall(
        client: OkHttpClient,
        token: String?,
        method: String,
        url: String,
        jsonBody: JSONObject? = null
    ): Pair<Int, String> {
        val builder = requestBuilder(token, url)
        val body = jsonBody?.toString()?.toRequestBody("application/json".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "{}".toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch(body ?: "{}".toRequestBody("application/json".toMediaType()))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string() ?: "")
        }
    }

    private fun githubErrorHint(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return when {
            code == 401 || code == 403 -> "GitHub refused the request (HTTP $code" +
                (message?.let { ": $it" } ?: "") +
                "). The repo may be private — the user can add a GitHub token in Settings."
            message != null -> "GitHub error (HTTP $code): $message"
            else -> "GitHub error (HTTP $code)."
        }
    }

    // ---- Manifest + local state ---------------------------------------------

    private fun blobSha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}".toByteArray(Charsets.US_ASCII))
        digest.update(0)
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class ProjectState(
        val dir: File,
        val owner: String,
        val repo: String,
        val branch: String,
        val baseCommit: String,
        val baseTree: String,
        val fileShas: Map<String, String>
    )

    private fun loadProject(root: File, path: String): ProjectState? {
        val dir = WorkspaceTools.resolveInside(root, path) ?: return null
        val manifestFile = File(dir, MANIFEST_NAME)
        if (!manifestFile.isFile) return null
        val manifest = runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
            ?: return null
        val filesJson = manifest.optJSONObject("files") ?: JSONObject()
        val shas = mutableMapOf<String, String>()
        val keys = filesJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            shas[key] = filesJson.optString(key)
        }
        return ProjectState(
            dir = dir,
            owner = manifest.optString("owner"),
            repo = manifest.optString("repo"),
            branch = manifest.optString("branch", "main"),
            baseCommit = manifest.optString("baseCommit"),
            baseTree = manifest.optString("baseTree"),
            fileShas = shas
        )
    }

    private fun saveManifest(dir: File, state: ProjectState) {
        val filesJson = JSONObject()
        state.fileShas.toSortedMap().forEach { (path, sha) -> filesJson.put(path, sha) }
        val manifest = JSONObject()
            .put("owner", state.owner)
            .put("repo", state.repo)
            .put("branch", state.branch)
            .put("baseCommit", state.baseCommit)
            .put("baseTree", state.baseTree)
            .put("files", filesJson)
        File(dir, MANIFEST_NAME).writeText(manifest.toString(), Charsets.UTF_8)
    }

    /** Current working files of a project: relative path -> file. */
    private fun workingFiles(dir: File): Map<String, File> {
        val result = mutableMapOf<String, File>()
        dir.walkTopDown()
            .onEnter { folder -> folder.name !in SKIPPED_DIRS || folder.path == dir.path }
            .filter { it.isFile && it.name != MANIFEST_NAME }
            .forEach { file ->
                val rel = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                result[rel] = file
            }
        return result
    }

    private class ChangeSet(
        val added: List<String>,
        val modified: List<String>,
        val deleted: List<String>,
        val currentShas: Map<String, String>
    ) {
        val isEmpty: Boolean get() = added.isEmpty() && modified.isEmpty() && deleted.isEmpty()
    }

    private fun computeChanges(state: ProjectState): ChangeSet {
        val currentShas = mutableMapOf<String, String>()
        for ((rel, file) in workingFiles(state.dir)) {
            currentShas[rel] = blobSha(file.readBytes())
        }
        val added = currentShas.keys.filter { it !in state.fileShas }.sorted()
        val modified = currentShas.keys.filter {
            it in state.fileShas && state.fileShas[it] != currentShas[it]
        }.sorted()
        val deleted = state.fileShas.keys.filter { it !in currentShas }.sorted()
        return ChangeSet(added, modified, deleted, currentShas)
    }

    private fun requireProject(root: File, path: String): ProjectState {
        if (path.isBlank()) throw IllegalArgumentException("a project path is required.")
        return loadProject(root, path)
            ?: throw IllegalArgumentException(
                "'$path' is not a cloned repo project (no $MANIFEST_NAME). Clone it with git_clone first."
            )
    }

    // ---- Tools ----------------------------------------------------------------

    private fun gitClone(
        root: File,
        client: OkHttpClient,
        token: String?,
        args: JSONObject
    ): Pair<String, Boolean> {
        val target = GitRepoCloner.parseRepoUrl(args.optString("repo"))
            ?: return "Error: could not parse the repo. Give owner/repo or a GitHub URL." to false
        val destPath = args.optString("path").ifBlank { target.repo }
        val dest = WorkspaceTools.resolveInside(root, destPath)
            ?: return "Error: destination escapes the workspace." to false
        if (dest.exists() && (dest.listFiles()?.isNotEmpty() == true)) {
            return "Error: '$destPath' already exists and is not empty. Delete it first, or clone under another path." to false
        }
        var branch = args.optString("branch").ifBlank { target.branch ?: "" }
        if (branch.isBlank()) {
            val (code, body) = apiCall(client, token, "GET", "$API_ROOT/repos/${target.owner}/${target.repo}")
            if (code != 200) return githubErrorHint(code, body) to false
            branch = JSONObject(body).optString("default_branch", "main").ifBlank { "main" }
        }
        // Download the zipball and extract it (wrapper folder stripped).
        val zipUrl = "$API_ROOT/repos/${target.owner}/${target.repo}/zipball/$branch"
        val response = client.newCall(requestBuilder(token, zipUrl).get().build()).execute()
        if (!response.isSuccessful || response.body == null) {
            val code = response.code
            response.close()
            return "Error: download failed (HTTP $code). Check the repo name, branch '$branch', and access token." to false
        }
        dest.mkdirs()
        var fileCount = 0
        var totalBytes = 0L
        response.body!!.byteStream().use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val stripped = entry.name.substringAfter("/", "")
                    if (stripped.isNotEmpty() && !entry.isDirectory) {
                        val outFile = File(dest, stripped)
                        val destCanon = dest.canonicalPath
                        if (outFile.canonicalPath.startsWith(destCanon + File.separator)) {
                            outFile.parentFile?.mkdirs()
                            val bytes = zip.readBytes()
                            totalBytes += bytes.size
                            if (bytes.size <= MAX_FILE_BYTES && totalBytes <= MAX_EXTRACT_BYTES) {
                                outFile.writeBytes(bytes)
                                fileCount++
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        response.close()
        if (fileCount == 0) {
            dest.deleteRecursively()
            return "Error: the archive for ${target.owner}/${target.repo}@$branch was empty or unreadable." to false
        }
        // Record the branch head so status/diff/push have a base.
        val (refCode, refBody) = apiCall(
            client, token, "GET",
            "$API_ROOT/repos/${target.owner}/${target.repo}/git/ref/heads/$branch"
        )
        if (refCode != 200) return githubErrorHint(refCode, refBody) to false
        val commitSha = JSONObject(refBody).getJSONObject("object").getString("sha")
        val (commitCode, commitBody) = apiCall(
            client, token, "GET",
            "$API_ROOT/repos/${target.owner}/${target.repo}/git/commits/$commitSha"
        )
        if (commitCode != 200) return githubErrorHint(commitCode, commitBody) to false
        val treeSha = JSONObject(commitBody).getJSONObject("tree").getString("sha")
        val shas = mutableMapOf<String, String>()
        for ((rel, file) in workingFiles(dest)) {
            shas[rel] = blobSha(file.readBytes())
        }
        saveManifest(
            dest,
            ProjectState(dest, target.owner, target.repo, branch, commitSha, treeSha, shas)
        )
        return "Cloned ${target.owner}/${target.repo} (branch $branch) into '$destPath': " +
            "$fileCount files, head ${commitSha.take(7)}. If the project has a LESSONS.md, " +
            "read it before working. Use the file tools on '$destPath', " +
            "then git_commit_push to ship changes." to true
    }

    private fun gitStatus(root: File, args: JSONObject): Pair<String, Boolean> {
        val state = requireProject(root, args.optString("path"))
        val changes = computeChanges(state)
        if (changes.isEmpty) {
            return "Working tree clean — '${args.optString("path")}' matches " +
                "${state.owner}/${state.repo}@${state.branch} at ${state.baseCommit.take(7)}." to true
        }
        val text = buildString {
            appendLine("Changes in '${args.optString("path")}' vs ${state.owner}/${state.repo}@${state.branch} (${state.baseCommit.take(7)}):")
            if (changes.added.isNotEmpty()) {
                appendLine("Added (${changes.added.size}):")
                changes.added.forEach { appendLine("  + $it") }
            }
            if (changes.modified.isNotEmpty()) {
                appendLine("Modified (${changes.modified.size}):")
                changes.modified.forEach { appendLine("  ~ $it") }
            }
            if (changes.deleted.isNotEmpty()) {
                appendLine("Deleted (${changes.deleted.size}):")
                changes.deleted.forEach { appendLine("  - $it") }
            }
        }
        return text.trimEnd() to true
    }

    private fun gitDiff(
        root: File,
        client: OkHttpClient,
        token: String?,
        args: JSONObject
    ): Pair<String, Boolean> {
        val state = requireProject(root, args.optString("path"))
        val changes = computeChanges(state)
        if (changes.modified.isEmpty()) {
            val extra = if (changes.added.isEmpty() && changes.deleted.isEmpty()) {
                "nothing has changed."
            } else {
                "no modified files (only additions/deletions — see git_status)."
            }
            return "No diff to show: $extra" to true
        }
        val output = StringBuilder()
        var shown = 0
        for (rel in changes.modified) {
            if (shown >= MAX_DIFF_FILES) {
                output.appendLine("[${changes.modified.size - shown} more modified file(s) not shown.]")
                break
            }
            val encodedPath = rel.split("/").joinToString("/") {
                java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val (code, body) = apiCall(
                client, token, "GET",
                "$API_ROOT/repos/${state.owner}/${state.repo}/contents/$encodedPath?ref=${state.branch}"
            )
            if (code != 200) {
                output.appendLine("--- $rel: could not fetch the GitHub version (HTTP $code).")
                continue
            }
            val base64 = JSONObject(body).optString("content").replace("\n", "").replace(" ", "")
            val baseText = runCatching {
                String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            val localText = File(state.dir, rel).readText(Charsets.UTF_8)
            if (baseText == null || baseText.contains('\u0000') || localText.contains('\u0000')) {
                output.appendLine("--- $rel: binary or unreadable file, no line diff.")
                continue
            }
            output.appendLine("--- a/$rel")
            output.appendLine("+++ b/$rel")
            output.appendLine(lineDiff(baseText, localText))
            shown++
        }
        val text = output.toString().trimEnd()
        return (if (text.length > 12_000) text.substring(0, 12_000) + "\n[Diff truncated.]" else text) to true
    }

    /** Minimal LCS line diff: unchanged runs longer than 6 lines collapse. */
    private fun lineDiff(baseText: String, localText: String): String {
        val base = baseText.split("\n")
        val local = localText.split("\n")
        if (base.size > MAX_DIFF_LINES || local.size > MAX_DIFF_LINES) {
            return "[File too large for a line diff (${base.size} -> ${local.size} lines).]"
        }
        val n = base.size
        val m = local.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (base[i] == local[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val ops = mutableListOf<Pair<Char, String>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                base[i] == local[j] -> { ops.add(' ' to base[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { ops.add('-' to base[i]); i++ }
                else -> { ops.add('+' to local[j]); j++ }
            }
        }
        while (i < n) { ops.add('-' to base[i]); i++ }
        while (j < m) { ops.add('+' to local[j]); j++ }
        val out = StringBuilder()
        var k = 0
        while (k < ops.size) {
            if (ops[k].first == ' ') {
                var runEnd = k
                while (runEnd < ops.size && ops[runEnd].first == ' ') runEnd++
                val runLength = runEnd - k
                if (runLength > 6) {
                    // Keep 2 context lines at each edge of a long unchanged run,
                    // except at the very start/end of the file.
                    if (k > 0) repeat(2) { out.appendLine("  ${ops[k + it].second}") }
                    out.appendLine("  ... (${runLength - (if (k > 0) 2 else 0) - (if (runEnd < ops.size) 2 else 0)} unchanged lines)")
                    if (runEnd < ops.size) repeat(2) { out.appendLine("  ${ops[runEnd - 2 + it].second}") }
                } else {
                    for (x in k until runEnd) out.appendLine("  ${ops[x].second}")
                }
                k = runEnd
            } else {
                out.appendLine("${ops[k].first} ${ops[k].second}")
                k++
            }
        }
        return out.toString().trimEnd()
    }

    private fun gitCommitPush(
        root: File,
        client: OkHttpClient,
        token: String?,
        args: JSONObject
    ): Pair<String, Boolean> {
        val state = requireProject(root, args.optString("path"))
        val message = args.optString("message").ifBlank { "Update from Aerodock" }
        val changes = computeChanges(state)
        if (changes.isEmpty) {
            return "Nothing to push — '${args.optString("path")}' already matches " +
                "${state.owner}/${state.repo}@${state.branch}." to true
        }
        val skipped = mutableListOf<String>()
        val treeEntries = JSONArray()
        val files = workingFiles(state.dir)
        for (rel in changes.added + changes.modified) {
            val file = files[rel] ?: continue
            if (file.length() > MAX_FILE_BYTES) {
                skipped.add(rel)
                continue
            }
            val contentB64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            val (code, body) = apiCall(
                client, token, "POST",
                "$API_ROOT/repos/${state.owner}/${state.repo}/git/blobs",
                JSONObject().put("content", contentB64).put("encoding", "base64")
            )
            if (code !in 200..299) return githubErrorHint(code, body) to false
            val blobShaCreated = JSONObject(body).getString("sha")
            treeEntries.put(
                JSONObject()
                    .put("path", rel)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", blobShaCreated)
            )
        }
        for (rel in changes.deleted) {
            treeEntries.put(
                JSONObject()
                    .put("path", rel)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", JSONObject.NULL)
            )
        }
        if (treeEntries.length() == 0) {
            return "Nothing pushable — every change was a file over the 10 MB limit: ${skipped.joinToString(", ")}" to false
        }
        val (treeCode, treeBody) = apiCall(
            client, token, "POST",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/trees",
            JSONObject().put("base_tree", state.baseTree).put("tree", treeEntries)
        )
        if (treeCode !in 200..299) return githubErrorHint(treeCode, treeBody) to false
        val newTreeSha = JSONObject(treeBody).getString("sha")
        val (commitCode, commitBody) = apiCall(
            client, token, "POST",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/commits",
            JSONObject()
                .put("message", message)
                .put("tree", newTreeSha)
                .put("parents", JSONArray().put(state.baseCommit))
        )
        if (commitCode !in 200..299) return githubErrorHint(commitCode, commitBody) to false
        val commitJson = JSONObject(commitBody)
        val newCommitSha = commitJson.getString("sha")
        val commitUrl = commitJson.optString("html_url")
        val (refCode, refBody) = apiCall(
            client, token, "PATCH",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/refs/heads/${state.branch}",
            JSONObject().put("sha", newCommitSha).put("force", false)
        )
        if (refCode !in 200..299) {
            return "Error: the commit was created but the branch refused to move " +
                "(HTTP $refCode) — the branch likely changed on GitHub since the clone. " +
                "Re-clone the project to sync, then make the changes again. " +
                runCatching { JSONObject(refBody).optString("message") }.getOrNull().orEmpty() to false
        }
        saveManifest(
            state.dir,
            ProjectState(state.dir, state.owner, state.repo, state.branch, newCommitSha, newTreeSha, changes.currentShas)
        )
        val summary = buildString {
            append("Pushed commit ${newCommitSha.take(7)} to ${state.owner}/${state.repo}@${state.branch}: \"$message\" ")
            append("(${changes.added.size} added, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
            if (skipped.isNotEmpty()) append("; skipped over 10 MB: ${skipped.joinToString(", ")}")
            append(").")
            if (commitUrl.isNotBlank()) append(" $commitUrl")
        }
        return summary to true
    }

    private fun gitPull(
        root: File,
        client: OkHttpClient,
        token: String?,
        args: JSONObject
    ): Pair<String, Boolean> {
        val state = requireProject(root, args.optString("path"))
        val (refCode, refBody) = apiCall(
            client, token, "GET",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/ref/heads/${state.branch}"
        )
        if (refCode != 200) return githubErrorHint(refCode, refBody) to false
        val remoteCommit = JSONObject(refBody).getJSONObject("object").getString("sha")
        if (remoteCommit == state.baseCommit) {
            return "'${args.optString("path")}' is already up to date with " +
                "${state.owner}/${state.repo}@${state.branch} (${remoteCommit.take(7)})." to true
        }
        val (commitCode, commitBody) = apiCall(
            client, token, "GET",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/commits/$remoteCommit"
        )
        if (commitCode != 200) return githubErrorHint(commitCode, commitBody) to false
        val remoteTree = JSONObject(commitBody).getJSONObject("tree").getString("sha")
        val (treeCode, treeBody) = apiCall(
            client, token, "GET",
            "$API_ROOT/repos/${state.owner}/${state.repo}/git/trees/$remoteTree?recursive=1"
        )
        if (treeCode != 200) return githubErrorHint(treeCode, treeBody) to false
        val treeJson = JSONObject(treeBody)
        if (treeJson.optBoolean("truncated", false)) {
            return "Error: the repo tree is too large to pull safely. Delete the local project and git_clone it again." to false
        }
        val remoteFiles = mutableMapOf<String, String>()
        val entries = treeJson.optJSONArray("tree")
        if (entries != null) {
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                if (entry.optString("type") == "blob") {
                    val entryPath = entry.optString("path")
                    val topDir = entryPath.substringBefore("/")
                    if (topDir !in SKIPPED_DIRS) {
                        remoteFiles[entryPath] = entry.optString("sha")
                    }
                }
            }
        }
        val localChanges = computeChanges(state)
        val locallyTouched = (localChanges.added + localChanges.modified + localChanges.deleted).toSet()
        val remoteChanged = remoteFiles.keys.filter { state.fileShas[it] != remoteFiles[it] }.toSet()
        val remoteDeleted = state.fileShas.keys.filter { it !in remoteFiles }.toSet()
        val conflicts = locallyTouched.intersect(remoteChanged + remoteDeleted).sorted()
        if (conflicts.isNotEmpty()) {
            return "Pull refused — these files changed BOTH locally and on GitHub: " +
                conflicts.joinToString(", ") +
                ". Push the local changes first (git_commit_push), or delete the local project and re-clone." to false
        }
        var updated = 0
        var deleted = 0
        val skippedLarge = mutableListOf<String>()
        for (rel in remoteChanged.sorted()) {
            val sha = remoteFiles[rel] ?: continue
            val (blobCode, blobBody) = apiCall(
                client, token, "GET",
                "$API_ROOT/repos/${state.owner}/${state.repo}/git/blobs/$sha"
            )
            if (blobCode != 200) return githubErrorHint(blobCode, blobBody) to false
            val blobJson = JSONObject(blobBody)
            if (blobJson.optLong("size") > MAX_FILE_BYTES) {
                skippedLarge.add(rel)
                continue
            }
            val bytes = Base64.decode(
                blobJson.optString("content").replace("\n", "").replace(" ", ""),
                Base64.DEFAULT
            )
            val outFile = File(state.dir, rel)
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(bytes)
            updated++
        }
        for (rel in remoteDeleted.sorted()) {
            if (File(state.dir, rel).delete()) deleted++
        }
        saveManifest(
            state.dir,
            ProjectState(state.dir, state.owner, state.repo, state.branch, remoteCommit, remoteTree, remoteFiles)
        )
        val kept = locallyTouched.size
        val text = buildString {
            append("Pulled ${state.owner}/${state.repo}@${state.branch}: ${state.baseCommit.take(7)} -> ${remoteCommit.take(7)}. ")
            append("$updated file(s) updated, $deleted deleted.")
            if (kept > 0) append(" $kept unpushed local change(s) kept.")
            if (skippedLarge.isNotEmpty()) {
                append(" Skipped (over 10 MB): ${skippedLarge.joinToString(", ")}.")
            }
        }
        return text to true
    }

    private fun buildStatus(
        root: File,
        client: OkHttpClient,
        token: String?,
        args: JSONObject
    ): Pair<String, Boolean> {
        var owner = ""
        var repo = ""
        var branch = args.optString("branch")
        val repoArg = args.optString("repo")
        if (repoArg.isNotBlank()) {
            val target = GitRepoCloner.parseRepoUrl(repoArg)
                ?: return "Error: could not parse the repo. Give owner/repo or a GitHub URL." to false
            owner = target.owner
            repo = target.repo
            if (branch.isBlank()) branch = target.branch ?: ""
        } else if (args.optString("path").isNotBlank()) {
            val state = requireProject(root, args.optString("path"))
            owner = state.owner
            repo = state.repo
            if (branch.isBlank()) branch = state.branch
        } else {
            return "Error: build_status needs a repo (owner/repo) or a workspace project path." to false
        }
        val runsUrl = buildString {
            append("$API_ROOT/repos/$owner/$repo/actions/runs?per_page=3")
            if (branch.isNotBlank()) append("&branch=$branch")
        }
        val (code, body) = apiCall(client, token, "GET", runsUrl)
        if (code != 200) return githubErrorHint(code, body) to false
        val runs = JSONObject(body).optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return "No GitHub Actions runs found for $owner/$repo${if (branch.isNotBlank()) " on branch $branch" else ""}." to true
        }
        val latest = runs.getJSONObject(0)
        val runId = latest.optLong("id")
        val runName = latest.optString("name", "build")
        val status = latest.optString("status")
        val conclusion = latest.optString("conclusion").ifBlank { "—" }
        val runUrl = latest.optString("html_url")
        val headSha = latest.optString("head_sha").take(7)
        val text = StringBuilder(
            "Latest Actions run for $owner/$repo: #$runId \"$runName\" — status $status, " +
                "conclusion $conclusion, commit $headSha. $runUrl"
        )
        if (conclusion == "failure" && runId > 0) {
            val (jobsCode, jobsBody) = apiCall(client, token, "GET", "$API_ROOT/repos/$owner/$repo/actions/runs/$runId/jobs")
            if (jobsCode == 200) {
                val jobs = JSONObject(jobsBody).optJSONArray("jobs")
                if (jobs != null) {
                    for (index in 0 until jobs.length()) {
                        val job = jobs.getJSONObject(index)
                        if (job.optString("conclusion") != "failure") continue
                        val jobName = job.optString("name", "job")
                        var failedStep = ""
                        val steps = job.optJSONArray("steps")
                        if (steps != null) {
                            for (s in 0 until steps.length()) {
                                val step = steps.getJSONObject(s)
                                if (step.optString("conclusion") == "failure") {
                                    failedStep = step.optString("name")
                                    break
                                }
                            }
                        }
                        text.append("\nFailed job: $jobName")
                        if (failedStep.isNotBlank()) text.append(" — failed step: \"$failedStep\"")
                        val jobId = job.optLong("id")
                        if (jobId > 0) {
                            val logResponse = runCatching {
                                client.newCall(
                                    requestBuilder(token, "$API_ROOT/repos/$owner/$repo/actions/jobs/$jobId/logs").get().build()
                                ).execute()
                            }.getOrNull()
                            val logText = logResponse?.use { resp ->
                                if (resp.isSuccessful) resp.body?.string() else null
                            }
                            if (!logText.isNullOrBlank()) {
                                val tail = logText.trimEnd().lines().takeLast(60).joinToString("\n")
                                text.append("\nLog tail:\n")
                                text.append(if (tail.length > 4_000) tail.substring(tail.length - 4_000) else tail)
                            }
                        }
                        break
                    }
                }
            }
        }
        return text.toString() to true
    }
}
