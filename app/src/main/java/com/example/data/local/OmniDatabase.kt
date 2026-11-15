package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProviderInstanceEntity::class,
        ModelDescriptorEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        PromptTemplateEntity::class,
        UsageRecordEntity::class,
        GeneratedImageEntity::class,
        VideoJobEntity::class,
        IntegrationAuditLogEntity::class,
        SkillEntity::class,
        ClonedRepoEntity::class,
        McpServerEntity::class,
        McpToolEntity::class,
        ToolCallLogEntity::class,
        // Step (1): Runway Data Layer entities (prefixed runway_*)
        RunwayMessageNodeEntity::class,
        RunwayConversationMetaEntity::class,
        RunwayFolderEntity::class,
        RunwayDocumentEntity::class,
        RunwayDocumentChunkEntity::class,
        RunwayPromptEntity::class,
        RunwayParameterPresetEntity::class,
        RunwayUsageLogEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun omniDao(): OmniDao

    companion object {
        @Volatile
        private var INSTANCE: OmniDatabase? = null

        /**
         * Non-destructive migration from v7 -> v8:
         * Creates all `runway_*` tables while preserving 100% of existing rows in
         * `conversations`, `messages`, `prompt_templates`, `usage_records`, `provider_instances`, etc.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_message_nodes` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `parentId` TEXT,
                        `childrenIdsJson` TEXT NOT NULL,
                        `activeChildId` TEXT,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `modelUsed` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `promptTokens` INTEGER NOT NULL,
                        `completionTokens` INTEGER NOT NULL,
                        `costUsd` REAL NOT NULL,
                        `isEstimatedCost` INTEGER NOT NULL,
                        `sourcesJson` TEXT NOT NULL,
                        `citationsJson` TEXT NOT NULL,
                        `attachmentUri` TEXT,
                        `attachmentMimeType` TEXT,
                        `isStreaming` INTEGER NOT NULL,
                        `errorText` TEXT,
                        `comparisonGroupId` TEXT,
                        `routedFromAuto` INTEGER NOT NULL,
                        `autoRoutingReason` TEXT,
                        `fallbackFromProvider` TEXT,
                        `latencyMs` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_message_nodes_conversationId` ON `runway_message_nodes` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_message_nodes_parentId` ON `runway_message_nodes` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_message_nodes_createdAt` ON `runway_message_nodes` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_conversation_meta` (
                        `conversationId` TEXT NOT NULL,
                        `folderId` TEXT,
                        `rootMessageIdsJson` TEXT NOT NULL,
                        `activeRootMessageId` TEXT,
                        `activeLeafMessageId` TEXT,
                        `parameterPresetId` TEXT,
                        `frequencyPenalty` REAL NOT NULL,
                        `presencePenalty` REAL NOT NULL,
                        `stopSequencesCsv` TEXT NOT NULL,
                        `webSearchEnabled` INTEGER NOT NULL,
                        `pinModelNoFallback` INTEGER NOT NULL,
                        `attachedDocumentIdsJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`conversationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_conversation_meta_folderId` ON `runway_conversation_meta` (`folderId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_folders` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_folders_sortOrder` ON `runway_folders` (`sortOrder`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_documents` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `totalTokens` INTEGER NOT NULL,
                        `parseStatus` TEXT NOT NULL,
                        `parseProgressPercent` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `fullText` TEXT NOT NULL,
                        `isSmallInlineDoc` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_documents_conversationId` ON `runway_documents` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_documents_createdAt` ON `runway_documents` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_document_chunks` (
                        `id` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `pageNumber` INTEGER NOT NULL,
                        `tokenCount` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `termFreqJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_document_chunks_documentId` ON `runway_document_chunks` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_document_chunks_conversationId` ON `runway_document_chunks` (`conversationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_prompts` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `defaultModelId` TEXT,
                        `defaultPresetId` TEXT,
                        `systemPromptOverride` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isStarter` INTEGER NOT NULL,
                        `lastVariableValuesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_prompts_isFavorite` ON `runway_prompts` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_prompts_createdAt` ON `runway_prompts` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_parameter_presets` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `maxTokens` INTEGER NOT NULL,
                        `topP` REAL NOT NULL,
                        `frequencyPenalty` REAL NOT NULL,
                        `presencePenalty` REAL NOT NULL,
                        `stopSequencesCsv` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_parameter_presets_isDefault` ON `runway_parameter_presets` (`isDefault`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runway_usage_logs` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT,
                        `messageId` TEXT,
                        `providerInstanceId` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `modality` TEXT NOT NULL,
                        `promptTokens` INTEGER NOT NULL,
                        `completionTokens` INTEGER NOT NULL,
                        `webSearchCount` INTEGER NOT NULL,
                        `documentTokens` INTEGER NOT NULL,
                        `costUsd` REAL NOT NULL,
                        `isEstimated` INTEGER NOT NULL,
                        `latencyMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_usage_logs_conversationId` ON `runway_usage_logs` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_usage_logs_providerInstanceId` ON `runway_usage_logs` (`providerInstanceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_runway_usage_logs_createdAt` ON `runway_usage_logs` (`createdAt`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloned_repos` (
                        `id` TEXT NOT NULL,
                        `owner` TEXT NOT NULL,
                        `repoName` TEXT NOT NULL,
                        `repoUrl` TEXT NOT NULL,
                        `branch` TEXT NOT NULL,
                        `defaultBranch` TEXT NOT NULL,
                        `commitSha` TEXT,
                        `localDirPath` TEXT NOT NULL,
                        `fileCount` INTEGER NOT NULL,
                        `skillCount` INTEGER NOT NULL,
                        `discoveredSkillsJson` TEXT NOT NULL,
                        `isPrivate` INTEGER NOT NULL,
                        `lastSyncStatus` TEXT NOT NULL,
                        `lastSyncMessage` TEXT NOT NULL,
                        `clonedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloned_repos_owner_repoName` ON `cloned_repos` (`owner`, `repoName`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is unchanged between v9 and v10; spending limits are stored in TinkSecretVault.
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Conversation archive flag for the history sheet's Archived segment.
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): OmniDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniDatabase::class.java,
                    "omniai_studio.db"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // WARNING: fallbackToDestructiveMigration() MUST NOT be used for schema changes
                    // that need to preserve existing user data. Always provide an explicit Migration.
                    // Future Migration Checklist:
                    // 1. Increment @Database(version = N + 1) above.
                    // 2. Define MIGRATION_N_(N+1) with exact SQL matching the updated Room Entity definitions
                    //    (including NOT NULL constraints, default values, primary keys, and indices).
                    // 3. Register the new Migration in .addMigrations(...) above.
                    // 4. Verify the migration preserves existing rows and matches Room's expected TableInfo.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
