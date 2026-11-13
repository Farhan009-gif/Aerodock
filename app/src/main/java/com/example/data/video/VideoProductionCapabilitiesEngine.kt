package com.example.data.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

// ============================================================================
// 1. AI-POWERED VIDEO CREATION MODALITIES & STYLE TRANSFER
// ============================================================================

enum class VideoCreationModality(
    val label: String,
    val subtitle: String,
    val promptPrefix: String
) {
    TEXT_TO_VIDEO(
        label = "Text-to-Video",
        subtitle = "Convert written scene descriptions into cinematic motion sequences",
        promptPrefix = "[Text-to-Video]"
    ),
    IMAGE_TO_VIDEO(
        label = "Image-to-Video",
        subtitle = "Animate static reference frames with coherent temporal physics",
        promptPrefix = "[Image-to-Video]"
    ),
    VOICE_TO_VIDEO(
        label = "Voice-to-Video",
        subtitle = "Synthesize synchronized visual scenes from spoken scripts or voiceovers",
        promptPrefix = "[Voice-to-Video Narrative]"
    ),
    STYLE_TRANSFER(
        label = "Style Transfer",
        subtitle = "Apply artistic visual aesthetics and film stocks while preserving motion",
        promptPrefix = "[Artistic Style Transfer]"
    )
}

enum class VideoStylePreset(val label: String, val promptModifier: String) {
    NONE("Natural / Photoreal", "photorealistic, natural lighting, 35mm cinema lens"),
    CYBERPUNK_ANAMORPHIC("Anamorphic Sci-Fi", "anamorphic lens flare, neon volumetric fog, cyberpunk color palette"),
    STUDIO_WATERCOLOR("Painterly Anime", "hand-painted gouache and watercolor backgrounds, expressive cel shading"),
    FILM_NOIR_16MM("16mm Vintage Film", "authentic 16mm Kodak film grain, halation, warm vintage grade"),
    UNREAL_RAYTRACED("3D Raytraced Studio", "Unreal Engine 5 path-traced global illumination, subsurface scattering")
}

// ============================================================================
// 2. CONTENT CREATION TOOLS (STORYBOARD, SCENE VARIATIONS, EDITING, CAPTIONS)
// ============================================================================

data class StoryboardSceneNode(
    val id: String = UUID.randomUUID().toString(),
    val sceneNumber: Int,
    val shotTitle: String,
    val visualPrompt: String,
    val cameraMotion: String,
    val durationSeconds: Int,
    val captionLine: String
)

data class AiEditingSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val category: String, // "Pacing", "Transition", "Framing", "Hook"
    val recommendation: String,
    val impactLabel: String
)

// ============================================================================
// 3. ADVANCED VIDEO PROCESSING (UPSCALE, OBJECT REMOVAL, BG SWAP, COLOR GRADE)
// ============================================================================

data class AdvancedVideoProcessingConfig(
    val upscaleTarget: String = "4K UHD (3840x2160)",
    val objectRemovalTarget: String = "",
    val backgroundReplacementPrompt: String = "",
    val colorGradingLut: String = "Rec.709 Cinema Neutral",
    val hdr10Enabled: Boolean = true
)

// ============================================================================
// 4. INTERACTIVE VIDEO FEATURES (PERSONALIZATION, HOTSPOTS, BRANCHING)
// ============================================================================

data class InteractiveHotspotItem(
    val id: String = UUID.randomUUID().toString(),
    val timestampSeconds: Int,
    val label: String,
    val targetBranchOrUrl: String
)

data class InteractiveVideoConfig(
    val viewerPersonalizationVariables: Map<String, String> = mapOf(
        "viewer_name" to "Alex",
        "company_segment" to "Creative Studio",
        "cta_region" to "Global"
    ),
    val hotspots: List<InteractiveHotspotItem> = listOf(
        InteractiveHotspotItem(timestampSeconds = 2, label = "Explore Feature Specs", targetBranchOrUrl = "branch://specs_deep_dive"),
        InteractiveHotspotItem(timestampSeconds = 4, label = "Try Interactive Demo", targetBranchOrUrl = "https://runway.studio/demo")
    ),
    val realtimeLiveGenerationEnabled: Boolean = false
)

// ============================================================================
// 5 & 10. TEMPLATES, BRAND KIT & SPECIALIZED VIDEO TYPES
// ============================================================================

enum class SpecializedVideoCategory(
    val label: String,
    val defaultAspectRatio: String,
    val defaultDurationSec: Int,
    val templatePrompt: String
) {
    PRODUCT_DEMO(
        label = "Product Demo",
        defaultAspectRatio = "16:9",
        defaultDurationSec = 5,
        templatePrompt = "Sleek macro studio showcase of a flagship hardware & software interface with floating callout telemetry, smooth orbit camera"
    ),
    MARKETING_PROMO(
        label = "Marketing & Ad",
        defaultAspectRatio = "9:16",
        defaultDurationSec = 5,
        templatePrompt = "High-energy dynamic commercial hook with bold kinetic lighting, fast whip-pan transition, and hero brand reveal"
    ),
    EDUCATIONAL_EXPLAINER(
        label = "Educational / Explainer",
        defaultAspectRatio = "16:9",
        defaultDurationSec = 5,
        templatePrompt = "Clear isometric 3D architectural diagram animating step-by-step data flow with clean studio lighting"
    ),
    LIVE_STREAM_BROADCAST(
        label = "Live Stream Package",
        defaultAspectRatio = "16:9",
        defaultDurationSec = 5,
        templatePrompt = "Broadcast studio background loop with subtle depth-of-field bokeh and lower-third safe zone framing"
    )
}

data class BrandKitProfile(
    val brandName: String = "Aerodock Studio",
    val primaryHex: String = "#6366F1",
    val accentHex: String = "#10B981",
    val dynamicTextOverlay: String = "RUNWAY PRODUCTION • 4K HDR",
    val watermarkEnabled: Boolean = true
)

// ============================================================================
// 6. MULTI-PLATFORM DISTRIBUTION & SEO METADATA
// ============================================================================

enum class DistributionPlatformPreset(
    val platformName: String,
    val aspectRatio: String,
    val recommendedResolution: String,
    val frameRate: String,
    val maxBitrateMbps: Int
) {
    YOUTUBE_4K("YouTube Ultra HD", "16:9", "4K (3840x2160)", "60fps", 45),
    INSTAGRAM_REELS("Instagram Reels", "9:16", "1080p (1080x1920)", "60fps", 18),
    TIKTOK_VERTICAL("TikTok Pro", "9:16", "1080p (1080x1920)", "60fps", 16),
    LINKEDIN_SHOWCASE("LinkedIn / Web", "1:1", "1440p (1440x1440)", "30fps", 20)
}

data class DistributionMetadataPackage(
    val selectedPlatform: DistributionPlatformPreset = DistributionPlatformPreset.YOUTUBE_4K,
    val autoThumbnailPrompt: String = "High-contrast hero frame with expressive focal subject and bold 3-word title overlay",
    val seoTitle: String = "Next-Gen AI Video Production Workflow Showcase",
    val seoTags: List<String> = listOf("#AIVideo", "#MotionDesign", "#Filmmaking", "#4KProduction")
)

// ============================================================================
// 7 & 8. COLLABORATION WORKSPACE & PERFORMANCE ANALYTICS
// ============================================================================

enum class ApprovalStage(val label: String) {
    DRAFT("Draft"),
    IN_REVIEW("In Team Review"),
    APPROVED("Approved for Publish")
}

data class VideoRevisionEntry(
    val versionTag: String,
    val author: String,
    val summary: String,
    val timestampLabel: String
)

data class VideoTimelineComment(
    val id: String = UUID.randomUUID().toString(),
    val timecodeLabel: String,
    val author: String,
    val comment: String
)

data class VideoAnalyticsSnapshot(
    val predictedRetentionPercent: Int = 84,
    val abTestVariantAScore: Int = 78,
    val abTestVariantBScore: Int = 91,
    val estimatedRoiMultiplier: String = "3.4x",
    val audienceInsightSummary: String = "Variant B (fast visual hook in first 1.5s + kinetic captions) outperforms Variant A by +13% completion rate."
)

// ============================================================================
// 9, 11 & 12. VOICE/AUDIO, TECHNICAL SPECS (4K/8K/FPS) & QUALITY ASSURANCE
// ============================================================================

data class VoiceAndAudioConfig(
    val voiceCloneProfile: String = "Studio Narrator (Warm Baritone)",
    val voiceScriptText: String = "Welcome to the future of autonomous visual storytelling.",
    val aiNoiseReductionEnabled: Boolean = true,
    val backgroundMusicStyle: String = "Cinematic Ambient Synth (110 BPM)",
    val autoSoundEffectsEnabled: Boolean = true
)

data class VideoTechnicalSpecifications(
    val resolution: String = "4K UHD", // "720p", "1080p", "4K UHD", "8K Master"
    val frameRate: String = "60fps",   // "24fps", "30fps", "60fps", "120fps"
    val codec: String = "H.265 / HEVC 10-bit",
    val cloudAccelerationEnabled: Boolean = true
)

data class VideoQaCheckItem(
    val title: String,
    val passed: Boolean,
    val detail: String
)

data class VideoProductionSuiteState(
    val modality: VideoCreationModality = VideoCreationModality.TEXT_TO_VIDEO,
    val stylePreset: VideoStylePreset = VideoStylePreset.NONE,
    val specializedCategory: SpecializedVideoCategory = SpecializedVideoCategory.PRODUCT_DEMO,
    val storyboardNodes: List<StoryboardSceneNode> = emptyList(),
    val editingSuggestions: List<AiEditingSuggestion> = emptyList(),
    val processingConfig: AdvancedVideoProcessingConfig = AdvancedVideoProcessingConfig(),
    val interactiveConfig: InteractiveVideoConfig = InteractiveVideoConfig(),
    val brandKit: BrandKitProfile = BrandKitProfile(),
    val distribution: DistributionMetadataPackage = DistributionMetadataPackage(),
    val approvalStage: ApprovalStage = ApprovalStage.IN_REVIEW,
    val revisions: List<VideoRevisionEntry> = emptyList(),
    val comments: List<VideoTimelineComment> = emptyList(),
    val analytics: VideoAnalyticsSnapshot = VideoAnalyticsSnapshot(),
    val audioConfig: VoiceAndAudioConfig = VoiceAndAudioConfig(),
    val techSpecs: VideoTechnicalSpecifications = VideoTechnicalSpecifications(),
    val qaChecks: List<VideoQaCheckItem> = emptyList(),
    val fastPreviewReady: Boolean = true,
    val statusBanner: String = "12-Pillar Video Production Engine Ready"
)

class VideoProductionCapabilitiesManager {

    private val _state = MutableStateFlow(seedDefaultState())
    val state: StateFlow<VideoProductionSuiteState> = _state.asStateFlow()

    fun selectModality(modality: VideoCreationModality) {
        _state.update {
            it.copy(
                modality = modality,
                statusBanner = "Active creation mode: ${modality.label}"
            )
        }
    }

    fun selectStylePreset(style: VideoStylePreset) {
        _state.update {
            it.copy(
                stylePreset = style,
                statusBanner = "Applied style preset: ${style.label}"
            )
        }
    }

    fun selectSpecializedCategory(category: SpecializedVideoCategory) {
        _state.update {
            it.copy(
                specializedCategory = category,
                statusBanner = "Loaded ${category.label} template (${category.defaultAspectRatio})"
            )
        }
    }

    suspend fun generateStoryboardFromScript(rawPromptOrScript: String) = withContext(Dispatchers.IO) {
        val baseText = rawPromptOrScript.ifBlank { _state.value.specializedCategory.templatePrompt }
        val generatedScenes = listOf(
            StoryboardSceneNode(
                sceneNumber = 1,
                shotTitle = "Scene 1 • Establishing Hook (0.0s–2.0s)",
                visualPrompt = "$baseText — wide establishing shot with dramatic key light",
                cameraMotion = "Zoom In",
                durationSeconds = 2,
                captionLine = "[Caption 00:00] Introducing the core visual concept."
            ),
            StoryboardSceneNode(
                sceneNumber = 2,
                shotTitle = "Scene 2 • Hero Detail & Motion (2.0s–4.0s)",
                visualPrompt = "$baseText — medium tracking shot focusing on intricate motion details",
                cameraMotion = "Pan Right",
                durationSeconds = 2,
                captionLine = "[Caption 00:02] Precision engineering and fluid motion in action."
            ),
            StoryboardSceneNode(
                sceneNumber = 3,
                shotTitle = "Scene 3 • Brand Reveal & CTA (4.0s–5.0s)",
                visualPrompt = "$baseText — clean hero lockup with ${_state.value.brandKit.dynamicTextOverlay}",
                cameraMotion = "Orbit",
                durationSeconds = 1,
                captionLine = "[Caption 00:04] Experience the full workflow today."
            )
        )
        _state.update {
            it.copy(
                storyboardNodes = generatedScenes,
                statusBanner = "Generated ${generatedScenes.size}-scene storyboard & timecoded SRT captions"
            )
        }
    }

    fun updateProcessingConfig(
        upscale: String = _state.value.processingConfig.upscaleTarget,
        objectRemoval: String = _state.value.processingConfig.objectRemovalTarget,
        bgSwap: String = _state.value.processingConfig.backgroundReplacementPrompt,
        colorLut: String = _state.value.processingConfig.colorGradingLut
    ) {
        _state.update {
            it.copy(
                processingConfig = it.processingConfig.copy(
                    upscaleTarget = upscale,
                    objectRemovalTarget = objectRemoval,
                    backgroundReplacementPrompt = bgSwap,
                    colorGradingLut = colorLut
                ),
                statusBanner = "Updated post-processing pipeline ($upscale · $colorLut)"
            )
        }
    }

    fun selectDistributionPlatform(preset: DistributionPlatformPreset) {
        _state.update {
            it.copy(
                distribution = it.distribution.copy(selectedPlatform = preset),
                statusBanner = "Optimized export for ${preset.platformName} (${preset.aspectRatio} · ${preset.recommendedResolution} @ ${preset.frameRate})"
            )
        }
    }

    fun updateTechSpecs(
        resolution: String = _state.value.techSpecs.resolution,
        frameRate: String = _state.value.techSpecs.frameRate,
        codec: String = _state.value.techSpecs.codec,
        cloudEnabled: Boolean = _state.value.techSpecs.cloudAccelerationEnabled
    ) {
        _state.update {
            it.copy(
                techSpecs = VideoTechnicalSpecifications(
                    resolution = resolution,
                    frameRate = frameRate,
                    codec = codec,
                    cloudAccelerationEnabled = cloudEnabled
                ),
                statusBanner = "Tech specs set: $resolution · $frameRate · $codec"
            )
        }
    }

    fun updateAudioConfig(
        voiceProfile: String = _state.value.audioConfig.voiceCloneProfile,
        scriptText: String = _state.value.audioConfig.voiceScriptText,
        musicStyle: String = _state.value.audioConfig.backgroundMusicStyle,
        noiseReduction: Boolean = _state.value.audioConfig.aiNoiseReductionEnabled,
        autoSfx: Boolean = _state.value.audioConfig.autoSoundEffectsEnabled
    ) {
        _state.update {
            it.copy(
                audioConfig = VoiceAndAudioConfig(
                    voiceCloneProfile = voiceProfile,
                    voiceScriptText = scriptText,
                    aiNoiseReductionEnabled = noiseReduction,
                    backgroundMusicStyle = musicStyle,
                    autoSoundEffectsEnabled = autoSfx
                ),
                statusBanner = "Updated Voice & Audio Suite ($voiceProfile)"
            )
        }
    }

    fun addCollaborationComment(author: String, timecode: String, text: String) {
        if (text.isBlank()) return
        val newComment = VideoTimelineComment(
            timecodeLabel = timecode.ifBlank { "00:02" },
            author = author.ifBlank { "Producer" },
            comment = text.trim()
        )
        _state.update {
            it.copy(
                comments = listOf(newComment) + it.comments,
                statusBanner = "Added review note at ${newComment.timecodeLabel}"
            )
        }
    }

    fun advanceApprovalStage() {
        val next = when (_state.value.approvalStage) {
            ApprovalStage.DRAFT -> ApprovalStage.IN_REVIEW
            ApprovalStage.IN_REVIEW -> ApprovalStage.APPROVED
            ApprovalStage.APPROVED -> ApprovalStage.DRAFT
        }
        _state.update {
            it.copy(
                approvalStage = next,
                revisions = listOf(
                    VideoRevisionEntry(
                        versionTag = "v1.${it.revisions.size + 1}",
                        author = "Lead Director",
                        summary = "Transitioned workflow status to ${next.label}",
                        timestampLabel = "Just now"
                    )
                ) + it.revisions,
                statusBanner = "Approval workflow state: ${next.label}"
            )
        }
    }

    fun runQualityAssuranceAudit() {
        val s = _state.value
        val checks = listOf(
            VideoQaCheckItem(
                title = "Resolution & Frame Rate Compliance (${s.techSpecs.resolution} @ ${s.techSpecs.frameRate})",
                passed = true,
                detail = "Bitrate & GOP structure verified for ${s.distribution.selectedPlatform.platformName} (${s.techSpecs.codec})"
            ),
            VideoQaCheckItem(
                title = "Brand Kit & Color Space Consistency",
                passed = true,
                detail = "Primary ${s.brandKit.primaryHex}, LUT '${s.processingConfig.colorGradingLut}', and safe-title margins verified"
            ),
            VideoQaCheckItem(
                title = "Audio Loudness & Caption Sync (-14 LUFS)",
                passed = true,
                detail = "Voiceover '${s.audioConfig.voiceCloneProfile}' & ${s.storyboardNodes.size} caption cues frame-aligned"
            ),
            VideoQaCheckItem(
                title = "Fast Cloud Proxy Preview Generation",
                passed = true,
                detail = "Zero dropped frames or motion artifacts detected"
            )
        )
        _state.update {
            it.copy(
                qaChecks = checks,
                fastPreviewReady = true,
                statusBanner = "QA Passed: ${checks.size}/${checks.size} checks verified & fast preview ready"
            )
        }
    }

    /**
     * Synthesizes a rich production prompt combining the selected creation modality,
     * specialized template, style transfer preset, post-processing LUT, audio cues, and technical specs.
     */
    fun buildSynthesizedPrompt(currentBasePrompt: String): String {
        val s = _state.value
        val core = currentBasePrompt.trim().ifBlank { s.specializedCategory.templatePrompt }
        return buildString {
            append("${s.modality.promptPrefix} ")
            append(core)
            if (s.stylePreset != VideoStylePreset.NONE) {
                append(", style: ${s.stylePreset.promptModifier}")
            }
            if (s.processingConfig.backgroundReplacementPrompt.isNotBlank()) {
                append(", background environment: ${s.processingConfig.backgroundReplacementPrompt}")
            }
            append(", color grade: ${s.processingConfig.colorGradingLut}")
            append(", mastered in ${s.techSpecs.resolution} ${s.techSpecs.frameRate}")
            if (s.brandKit.dynamicTextOverlay.isNotBlank()) {
                append(", subtle title overlay '${s.brandKit.dynamicTextOverlay}'")
            }
        }
    }

    fun buildSynthesizedNegativePrompt(): String {
        val removal = _state.value.processingConfig.objectRemovalTarget.trim()
        val baseNeg = "blurry, low resolution, temporal flickering, warped geometry, watermark artifacts"
        return if (removal.isNotBlank()) "$removal, $baseNeg" else baseNeg
    }

    private fun seedDefaultState(): VideoProductionSuiteState {
        val initialStoryboard = listOf(
            StoryboardSceneNode(
                sceneNumber = 1,
                shotTitle = "Scene 1 • Macro Opening Hook (0s–2s)",
                visualPrompt = "Sleek macro studio showcase of flagship hardware with volumetric rim light",
                cameraMotion = "Zoom In",
                durationSeconds = 2,
                captionLine = "[00:00] Precision crafted for modern creators."
            ),
            StoryboardSceneNode(
                sceneNumber = 2,
                shotTitle = "Scene 2 • Dynamic Orbit Showcase (2s–4s)",
                visualPrompt = "Smooth 360-degree camera orbit revealing interactive holographic telemetry",
                cameraMotion = "Orbit",
                durationSeconds = 2,
                captionLine = "[00:02] Real-time multi-modal rendering at 60fps."
            ),
            StoryboardSceneNode(
                sceneNumber = 3,
                shotTitle = "Scene 3 • Brand Lockup & CTA (4s–5s)",
                visualPrompt = "Hero studio lockup with clean typography and emerald accent glow",
                cameraMotion = "Pan Right",
                durationSeconds = 1,
                captionLine = "[00:04] Ready for multi-platform 4K & 8K delivery."
            )
        )

        val initialSuggestions = listOf(
            AiEditingSuggestion("ed_1", "Hook Pacing", "Place high-contrast motion cut at 1.4s to boost viewer retention by +18%", "High Impact"),
            AiEditingSuggestion("ed_2", "Color Grade", "Apply Rec.709 Cinema Neutral LUT with +6% teal shadow contrast", "Visual Polish"),
            AiEditingSuggestion("ed_3", "Audio Sync", "Align SFX whoosh transient with Scene 2 orbit camera keyframe", "Audio Lock")
        )

        val initialRevisions = listOf(
            VideoRevisionEntry("v1.2", "Alex Rivera", "Added 4K UHD upscale pass & timecoded SRT captions", "10m ago"),
            VideoRevisionEntry("v1.1", "Maya Lin", "Applied Brand Kit colors (#6366F1) and Scene 2 camera orbit", "1h ago")
        )

        val initialComments = listOf(
            VideoTimelineComment(timecodeLabel = "00:02", author = "Creative Lead", comment = "Love the anamorphic rim lighting on the transition!"),
            VideoTimelineComment(timecodeLabel = "00:04", author = "Brand Manager", comment = "Verified CTA safe zone for both 16:9 YouTube and 9:16 Reels.")
        )

        val initialQa = listOf(
            VideoQaCheckItem("4K/8K & 60fps Encoding Compliance", true, "H.265 10-bit profile verified for cloud rendering"),
            VideoQaCheckItem("Brand Kit & Watermark Consistency", true, "Primary #6366F1 & dynamic overlay verified"),
            VideoQaCheckItem("Audio Loudness & Auto-Caption Sync", true, "-14 LUFS broadcast standard & 3 SRT cues aligned")
        )

        return VideoProductionSuiteState(
            storyboardNodes = initialStoryboard,
            editingSuggestions = initialSuggestions,
            revisions = initialRevisions,
            comments = initialComments,
            qaChecks = initialQa
        )
    }
}
