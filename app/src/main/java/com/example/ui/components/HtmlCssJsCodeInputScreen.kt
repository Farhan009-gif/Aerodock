package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.mcp.HtmlCssJsTemplate
import com.example.data.mcp.HtmlCssJsTemplateCatalog
import com.example.data.mcp.HtmlCssJsTemplateCategory
import com.example.data.mcp.JsSandboxExecutor
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

/**
 * Template Selector component for the HTML/CSS/JS code input screen.
 *
 * Features:
 * - Quick-access starter chips for common boilerplates ('Centered Card', 'CSS Grid Layout',
 *   'Interactive Button', etc.)
 * - Expandable "More & Online Templates" catalog with search, category filters, and 15+
 *   built-in + online curated templates
 * - Live URL / GitHub / Gist template importer so users can fetch additional HTML/CSS/JS
 *   boilerplates directly from online sources.
 */
@Composable
fun HtmlCssJsTemplateSelector(
    selectedTemplateId: String?,
    onSelectTemplate: (HtmlCssJsTemplate) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpandedOnlineCatalog: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val customOnlineTemplates = remember { mutableStateListOf<HtmlCssJsTemplate>() }
    var showFullCatalog by rememberSaveable { mutableStateOf(initiallyExpandedOnlineCatalog) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(HtmlCssJsTemplateCategory.ALL) }
    var onlineUrlInput by rememberSaveable { mutableStateOf("") }
    var isFetchingOnline by remember { mutableStateOf(false) }
    var onlineFetchStatus by remember { mutableStateOf<String?>(null) }
    var isOnlineFetchError by remember { mutableStateOf(false) }

    val quickTemplates = remember(customOnlineTemplates.size) {
        HtmlCssJsTemplateCatalog.quickStarterTemplates() + customOnlineTemplates
    }

    val filteredCatalog = remember(searchQuery, selectedCategory, customOnlineTemplates.size) {
        HtmlCssJsTemplateCatalog.filterTemplates(
            query = searchQuery,
            category = selectedCategory,
            customOnlineTemplates = customOnlineTemplates
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("html_css_js_template_selector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Templates",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Starter & Online Templates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pick 'Centered Card', 'CSS Grid Layout', 'Interactive Button', or browse online boilerplates",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showFullCatalog = !showFullCatalog },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_online_template_catalog_button")
                ) {
                    Icon(
                        imageVector = if (showFullCatalog) Icons.Default.ExpandLess else Icons.Default.CloudDownload,
                        contentDescription = if (showFullCatalog) "Hide online templates" else "Browse online templates",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showFullCatalog) "Hide Library" else "Online & More (${HtmlCssJsTemplateCatalog.allTemplates(customOnlineTemplates).size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Quick Starter Boilerplate Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("quick_template_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickTemplates.forEach { template ->
                    val isSelected = selectedTemplateId == template.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTemplate(template) },
                        label = {
                            Text(
                                text = template.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("template_chip_${template.id}")
                    )
                }

                FilterChip(
                    selected = showFullCatalog && selectedCategory == HtmlCssJsTemplateCategory.ONLINE_HUB,
                    onClick = {
                        selectedCategory = HtmlCssJsTemplateCategory.ONLINE_HUB
                        showFullCatalog = true
                    },
                    label = {
                        Text(
                            text = "+ Explore Online Templates",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("template_chip_explore_online")
                )
            }

            // Expandable Full Template Browser + Live Online URL Fetcher
            AnimatedVisibility(visible = showFullCatalog) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expanded_online_template_catalog"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HtmlCssJsTemplateCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("template_category_chip_${category.name.lowercase()}")
                            )
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search boilerplates (e.g. Grid, Card, Button, Kanban, Chart)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search templates",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("template_search_input")
                    )

                    // Fetch from Online URL / GitHub Raw / Gist
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Import Boilerplate from Online URL (Raw GitHub / Gist / HTML / JSON)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = onlineUrlInput,
                                    onValueChange = { onlineUrlInput = it },
                                    placeholder = { Text("https://raw.githubusercontent.com/.../template.html") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("online_template_url_input")
                                )
                                Button(
                                    onClick = {
                                        val urlToFetch = onlineUrlInput.trim()
                                        if (urlToFetch.isEmpty()) return@Button
                                        isFetchingOnline = true
                                        onlineFetchStatus = null
                                        coroutineScope.launch {
                                            val result = HtmlCssJsTemplateCatalog.fetchOnlineTemplatesFromUrl(urlToFetch)
                                            isFetchingOnline = false
                                            result.fold(
                                                onSuccess = { fetchedList ->
                                                    customOnlineTemplates.addAll(0, fetchedList)
                                                    val first = fetchedList.first()
                                                    onSelectTemplate(first)
                                                    isOnlineFetchError = false
                                                    onlineFetchStatus = "Loaded '${first.title}' (${fetchedList.size} template(s)) from online."
                                                    onlineUrlInput = ""
                                                },
                                                onFailure = { err ->
                                                    isOnlineFetchError = true
                                                    onlineFetchStatus = err.message ?: "Failed to fetch online template."
                                                }
                                            )
                                        }
                                    },
                                    enabled = onlineUrlInput.isNotBlank() && !isFetchingOnline,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fetch_online_template_button")
                                ) {
                                    if (isFetchingOnline) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Fetch online template",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetch")
                                    }
                                }
                            }
                            if (!onlineFetchStatus.isNullOrBlank()) {
                                Text(
                                    text = onlineFetchStatus.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnlineFetchError) CoralError else EmeraldConnected,
                                    modifier = Modifier.testTag("online_template_fetch_status")
                                )
                            }
                        }
                    }

                    // Filtered Template List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filteredCatalog.isEmpty()) {
                            Text(
                                text = "No templates match '$searchQuery'. Try clearing the search or importing from a URL above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            filteredCatalog.forEach { template ->
                                val isCurrent = selectedTemplateId == template.id
                                Surface(
                                    color = if (isCurrent) {
                                        ElectricIndigo.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) ElectricIndigo else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectTemplate(template) }
                                        .testTag("catalog_template_card_${template.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = template.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                BadgePill(
                                                    text = if (template.isOnlineCurated) "ONLINE" else template.category.label.uppercase(),
                                                    color = if (template.isOnlineCurated) CyberCyan else ElectricIndigo
                                                )
                                            }
                                            Text(
                                                text = template.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Button(
                                            onClick = { onSelectTemplate(template) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrent) EmeraldConnected else ElectricIndigo
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("use_template_${template.id}")
                                        ) {
                                            Text(
                                                text = if (isCurrent) "Applied" else "Use",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Code input screen for authoring, templating, previewing, and running HTML, CSS, and JavaScript.
 *
 * Includes:
 * - [HtmlCssJsTemplateSelector] with 'Centered Card', 'CSS Grid Layout', 'Interactive Button',
 *   and 15+ additional built-in and online templates + live URL importer.
 * - Tabbed or stacked HTML, CSS, and JS code input editors.
 * - Live secured [WebViewPreviewer] preview panel and "Run Code" confirmation trigger.
 */
@Composable
fun HtmlCssJsCodeInputScreen(
    modifier: Modifier = Modifier,
    initialHtml: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().html,
    initialCss: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().css,
    initialJs: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().js,
    onRunInSandboxWithConfirmation: ((html: String, css: String, js: String) -> Unit)? = null,
    onInsertIntoChat: ((formattedMarkdown: String) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>("centered_card") }
    var selectedTemplateTitle by rememberSaveable { mutableStateOf("Centered Card") }
    var htmlInput by rememberSaveable { mutableStateOf(initialHtml) }
    var cssInput by rememberSaveable { mutableStateOf(initialCss) }
    var jsInput by rememberSaveable { mutableStateOf(initialJs) }

    // 0 = All (HTML + CSS + JS), 1 = HTML, 2 = CSS, 3 = JS
    var activeEditorPane by rememberSaveable { mutableStateOf(0) }
    var showLivePreview by rememberSaveable { mutableStateOf(true) }

    val totalBytes = remember(htmlInput, cssInput, jsInput) {
        htmlInput.toByteArray(StandardCharsets.UTF_8).size +
            cssInput.toByteArray(StandardCharsets.UTF_8).size +
            jsInput.toByteArray(StandardCharsets.UTF_8).size
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("html_css_js_code_input_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "HTML CSS JS Code Input",
                    tint = ElectricIndigo,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "HTML / CSS / JS Code Input & Sandbox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Start from a template or write custom HTML, CSS, and JS ($totalBytes bytes / 4 MB cap)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClose != null) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("close_code_input_screen_button")
                ) {
                    Text("Close")
                }
            }
        }

        // 1. Template Selector ('Centered Card', 'CSS Grid Layout', 'Interactive Button', + Online)
        HtmlCssJsTemplateSelector(
            selectedTemplateId = selectedTemplateId,
            onSelectTemplate = { template ->
                selectedTemplateId = template.id
                selectedTemplateTitle = template.title
                htmlInput = template.html
                cssInput = template.css
                jsInput = template.js
            }
        )

        // 2. Editor Pane Filter Chips (All | HTML | CSS | JS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val panes = listOf(
                0 to "All (HTML + CSS + JS)",
                1 to "HTML (${htmlInput.lines().size}L)",
                2 to "CSS (${cssInput.lines().size}L)",
                3 to "JS (${jsInput.lines().size}L)"
            )
            panes.forEach { (idx, label) ->
                FilterChip(
                    selected = activeEditorPane == idx,
                    onClick = { activeEditorPane = idx },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("code_input_pane_tab_$idx")
                )
            }
        }

        // 3. HTML / CSS / JS Code Input Fields
        if (activeEditorPane == 0 || activeEditorPane == 1) {
            OutlinedTextField(
                value = htmlInput,
                onValueChange = {
                    htmlInput = it
                    selectedTemplateId = null
                },
                label = { Text("HTML Markup") },
                minLines = if (activeEditorPane == 1) 8 else 4,
                maxLines = if (activeEditorPane == 1) 14 else 7,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_input_html_field")
            )
        }

        if (activeEditorPane == 0 || activeEditorPane == 2) {
            OutlinedTextField(
                value = cssInput,
                onValueChange = {
                    cssInput = it
                    selectedTemplateId = null
                },
                label = { Text("CSS Stylesheet") },
                minLines = if (activeEditorPane == 2) 8 else 4,
                maxLines = if (activeEditorPane == 2) 14 else 7,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_input_css_field")
            )
        }

        if (activeEditorPane == 0 || activeEditorPane == 3) {
            OutlinedTextField(
                value = jsInput,
                onValueChange = {
                    jsInput = it
                    selectedTemplateId = null
                },
                label = { Text("JavaScript (ES6+)") },
                minLines = if (activeEditorPane == 3) 8 else 4,
                maxLines = if (activeEditorPane == 3) 14 else 7,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_input_js_field")
            )
        }

        // 4. Action Buttons Row (Preview, Run with Confirmation, Insert into Chat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showLivePreview = !showLivePreview },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("code_input_toggle_preview_button")
            ) {
                Text(if (showLivePreview) "Hide Preview" else "Show Preview")
            }

            if (onRunInSandboxWithConfirmation != null) {
                Button(
                    onClick = {
                        onRunInSandboxWithConfirmation(htmlInput, cssInput, jsInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("code_input_run_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run code",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run in Sandbox", color = Color.White)
                }
            }

            if (onInsertIntoChat != null) {
                OutlinedButton(
                    onClick = {
                        val combinedDoc = JsSandboxExecutor.buildFullHtmlDocument(
                            html = htmlInput,
                            css = cssInput,
                            js = jsInput
                        )
                        onInsertIntoChat("```html\n$combinedDoc\n```")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("code_input_insert_chat_button")
                ) {
                    Text("Insert in Chat")
                }
            }
        }

        // 5. Secured WebViewPreviewer Panel
        AnimatedVisibility(visible = showLivePreview) {
            WebViewPreviewer(
                html = htmlInput,
                css = cssInput,
                js = jsInput,
                title = "$selectedTemplateTitle • Secured WebView Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .testTag("code_input_webview_previewer")
            )
        }
    }
}

/**
 * Modal Bottom Sheet wrapper for [HtmlCssJsCodeInputScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlCssJsCodeInputBottomSheet(
    onDismiss: () -> Unit,
    initialHtml: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().html,
    initialCss: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().css,
    initialJs: String = HtmlCssJsTemplateCatalog.BUILT_IN_TEMPLATES.first().js,
    onRunInSandboxWithConfirmation: ((html: String, css: String, js: String) -> Unit)? = null,
    onInsertIntoChat: ((formattedMarkdown: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var clearSheetIme by remember { mutableStateOf<(() -> Unit)?>(null) }
    val safeDismiss: () -> Unit = {
        clearSheetIme?.invoke()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = safeDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("html_css_js_code_input_sheet")
    ) {
        DialogWindowFocusGuard(onRegisterCleaner = { clearSheetIme = it })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            HtmlCssJsCodeInputScreen(
                initialHtml = initialHtml,
                initialCss = initialCss,
                initialJs = initialJs,
                onRunInSandboxWithConfirmation = onRunInSandboxWithConfirmation?.let { callback ->
                    { html, css, js ->
                        clearSheetIme?.invoke()
                        callback(html, css, js)
                    }
                },
                onInsertIntoChat = onInsertIntoChat?.let { callback ->
                    { markdown ->
                        clearSheetIme?.invoke()
                        callback(markdown)
                    }
                },
                onClose = safeDismiss
            )
        }
    }
}
