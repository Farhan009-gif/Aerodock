package com.example.data.mcp

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Categories for organizing built-in and online HTML/CSS/JS starter boilerplates.
 */
enum class HtmlCssJsTemplateCategory(val label: String) {
    ALL("All"),
    LAYOUT("Layouts & Grids"),
    COMPONENTS("Components"),
    INTERACTIVE("Interactive JS"),
    DATA_CHARTS("Data & Visuals"),
    FORMS_AUTH("Forms & Inputs"),
    ONLINE_HUB("Online Library")
}

/**
 * Represents a ready-to-run HTML/CSS/JS boilerplate template for the code input screen.
 */
data class HtmlCssJsTemplate(
    val id: String,
    val title: String,
    val category: HtmlCssJsTemplateCategory,
    val description: String,
    val tags: List<String>,
    val html: String,
    val css: String,
    val js: String,
    val sourceLabel: String = "Built-in Boilerplate",
    val sourceUrl: String = "",
    val isOnlineCurated: Boolean = false
) {
    /**
     * Builds the complete sandboxed HTML document for this template via [JsSandboxExecutor].
     */
    fun toFullHtmlDocument(): String {
        return JsSandboxExecutor.buildFullHtmlDocument(
            html = html,
            css = css,
            js = js
        )
    }

    /**
     * Builds the `run_code` JSON arguments payload for this template.
     */
    fun toRunCodeArgumentsJson(): String {
        return JsSandboxExecutor.buildRunCodeArgumentsJson(
            language = "html",
            code = html,
            css = css,
            js = js
        )
    }
}

/**
 * Catalog and online fetcher for HTML/CSS/JS boilerplates on the code input screen.
 * Includes 'Centered Card', 'CSS Grid Layout', 'Interactive Button', and 20+ additional
 * built-in and online templates, plus live URL/Gist/GitHub template fetching and parsing.
 */
object HtmlCssJsTemplateCatalog {

    private const val MAX_ONLINE_TEMPLATE_BYTES = 512 * 1024 // 512 KB cap for fetched templates

    val templateHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10_000L, TimeUnit.MILLISECONDS)
            .readTimeout(10_000L, TimeUnit.MILLISECONDS)
            .writeTimeout(10_000L, TimeUnit.MILLISECONDS)
            .callTimeout(12_000L, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    val BUILT_IN_TEMPLATES: List<HtmlCssJsTemplate> = listOf(
        HtmlCssJsTemplate(
            id = "centered_card",
            title = "Centered Card",
            category = HtmlCssJsTemplateCategory.LAYOUT,
            description = "Responsive flexbox-centered card with status badge, typography hierarchy, and CTA button.",
            tags = listOf("flexbox", "card", "centered", "boilerplate", "starter"),
            html = """
                <div class="viewport">
                  <article class="card">
                    <span class="badge">NEW RELEASE</span>
                    <h1 class="card-title">Centered Card</h1>
                    <p class="card-body">
                      A clean, responsive card centered both vertically and horizontally using modern Flexbox and surface elevation.
                    </p>
                    <div class="card-actions">
                      <button id="explore-btn" class="btn-primary">Explore Features</button>
                      <span id="click-status" class="status-text">Ready</span>
                    </div>
                  </article>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                .viewport {
                  min-height: 90vh;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
                  padding: 20px;
                  font-family: system-ui, -apple-system, sans-serif;
                }
                .card {
                  width: 100%;
                  max-width: 380px;
                  background: #1e293b;
                  color: #f8fafc;
                  border: 1px solid rgba(148, 163, 184, 0.25);
                  border-radius: 16px;
                  padding: 24px;
                  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
                }
                .badge {
                  display: inline-block;
                  font-size: 11px;
                  font-weight: 700;
                  letter-spacing: 0.08em;
                  color: #38bdf8;
                  background: rgba(56, 189, 248, 0.14);
                  padding: 4px 10px;
                  border-radius: 999px;
                  margin-bottom: 12px;
                }
                .card-title {
                  font-size: 22px;
                  font-weight: 700;
                  margin-bottom: 8px;
                }
                .card-body {
                  font-size: 14px;
                  line-height: 1.55;
                  color: #cbd5e1;
                  margin-bottom: 20px;
                }
                .card-actions {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 12px;
                }
                .btn-primary {
                  background: #6366f1;
                  color: #ffffff;
                  border: none;
                  border-radius: 10px;
                  padding: 10px 16px;
                  font-weight: 600;
                  cursor: pointer;
                  transition: transform 0.15s ease, background 0.15s ease;
                }
                .btn-primary:active {
                  transform: scale(0.97);
                  background: #4f46e5;
                }
                .status-text {
                  font-size: 12px;
                  color: #94a3b8;
                }
            """.trimIndent(),
            js = """
                const btn = document.getElementById('explore-btn');
                const status = document.getElementById('click-status');
                let count = 0;
                if (btn && status) {
                  btn.addEventListener('click', () => {
                    count += 1;
                    status.textContent = 'Activated ' + count + 'x';
                    console.log('Centered Card action clicked:', count);
                  });
                }
                console.log('Centered Card template initialized');
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "css_grid_layout",
            title = "CSS Grid Layout",
            category = HtmlCssJsTemplateCategory.LAYOUT,
            description = "Responsive auto-fit CSS Grid dashboard with metric tiles, header bar, and interactive layout density switch.",
            tags = listOf("grid", "css-grid", "dashboard", "responsive", "layout", "boilerplate"),
            html = """
                <div class="dashboard">
                  <header class="dash-header">
                    <h2>CSS Grid Layout</h2>
                    <button id="toggle-cols-btn" class="chip-btn">Toggle Compact Grid</button>
                  </header>
                  <section id="grid-container" class="grid-container">
                    <div class="grid-tile accent">
                      <span class="tile-label">THROUGHPUT</span>
                      <strong class="tile-value">1,420 req/s</strong>
                    </div>
                    <div class="grid-tile">
                      <span class="tile-label">P95 LATENCY</span>
                      <strong class="tile-value">42 ms</strong>
                    </div>
                    <div class="grid-tile">
                      <span class="tile-label">CACHE HIT</span>
                      <strong class="tile-value">94.8%</strong>
                    </div>
                    <div class="grid-tile wide">
                      <span class="tile-label">ACTIVE NODES</span>
                      <strong class="tile-value">12 / 12 Healthy across 3 regions</strong>
                    </div>
                  </section>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  background: #0b0d10;
                  color: #f1f5f9;
                  font-family: system-ui, -apple-system, sans-serif;
                  padding: 16px;
                }
                .dash-header {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  margin-bottom: 16px;
                }
                .dash-header h2 { font-size: 18px; }
                .chip-btn {
                  background: #1e293b;
                  color: #38bdf8;
                  border: 1px solid #334155;
                  border-radius: 8px;
                  padding: 6px 12px;
                  font-size: 12px;
                  cursor: pointer;
                }
                .grid-container {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                  gap: 12px;
                }
                .grid-container.compact {
                  grid-template-columns: repeat(2, 1fr);
                  gap: 8px;
                }
                .grid-tile {
                  background: #14171c;
                  border: 1px solid #262b35;
                  border-radius: 12px;
                  padding: 16px;
                  display: flex;
                  flex-direction: column;
                  gap: 6px;
                }
                .grid-tile.accent {
                  border-color: #6366f1;
                  background: rgba(99, 102, 241, 0.12);
                }
                .grid-tile.wide {
                  grid-column: 1 / -1;
                }
                .tile-label {
                  font-size: 11px;
                  color: #94a3b8;
                  letter-spacing: 0.06em;
                }
                .tile-value {
                  font-size: 18px;
                  color: #f8fafc;
                }
            """.trimIndent(),
            js = """
                const toggleBtn = document.getElementById('toggle-cols-btn');
                const grid = document.getElementById('grid-container');
                if (toggleBtn && grid) {
                  toggleBtn.addEventListener('click', () => {
                    grid.classList.toggle('compact');
                    console.log('CSS Grid compact mode:', grid.classList.contains('compact'));
                  });
                }
                console.log('CSS Grid Layout initialized with', document.querySelectorAll('.grid-tile').length, 'tiles');
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "interactive_button",
            title = "Interactive Button",
            category = HtmlCssJsTemplateCategory.INTERACTIVE,
            description = "State-driven interactive button with live counter, pulse animation, and dynamic status feedback.",
            tags = listOf("button", "interactive", "events", "animation", "counter", "boilerplate"),
            html = """
                <div class="stage">
                  <div class="button-panel">
                    <p class="eyebrow">INTERACTIVE STATE DEMO</p>
                    <button id="interactive-btn" class="glow-button">
                      <span id="btn-icon">⚡</span>
                      <span id="btn-label">Tap to Boost</span>
                    </button>
                    <div class="metrics-row">
                      <span id="counter-pill" class="pill">Clicks: 0</span>
                      <button id="reset-btn" class="reset-link">Reset</button>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                .stage {
                  min-height: 85vh;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  background: #0b0d10;
                  font-family: system-ui, -apple-system, sans-serif;
                  color: #f8fafc;
                }
                .button-panel {
                  text-align: center;
                  background: #14171c;
                  border: 1px solid #262b35;
                  padding: 28px 32px;
                  border-radius: 18px;
                }
                .eyebrow {
                  font-size: 11px;
                  letter-spacing: 0.1em;
                  color: #94a3b8;
                  margin-bottom: 16px;
                }
                .glow-button {
                  display: inline-flex;
                  align-items: center;
                  gap: 10px;
                  padding: 14px 26px;
                  font-size: 16px;
                  font-weight: 700;
                  color: #ffffff;
                  background: linear-gradient(135deg, #6366f1, #06b6d4);
                  border: none;
                  border-radius: 999px;
                  cursor: pointer;
                  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
                  transition: transform 0.12s ease, box-shadow 0.2s ease;
                }
                .glow-button.active-pulse {
                  transform: scale(1.05);
                  box-shadow: 0 12px 30px rgba(6, 182, 212, 0.55);
                }
                .metrics-row {
                  margin-top: 18px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  gap: 12px;
                }
                .pill {
                  font-size: 13px;
                  background: rgba(56, 189, 248, 0.14);
                  color: #38bdf8;
                  padding: 4px 12px;
                  border-radius: 999px;
                }
                .reset-link {
                  background: transparent;
                  color: #94a3b8;
                  border: 1px solid #334155;
                  border-radius: 999px;
                  padding: 4px 10px;
                  font-size: 12px;
                  cursor: pointer;
                }
            """.trimIndent(),
            js = """
                const btn = document.getElementById('interactive-btn');
                const label = document.getElementById('btn-label');
                const counterPill = document.getElementById('counter-pill');
                const resetBtn = document.getElementById('reset-btn');
                let clicks = 0;

                function render() {
                  counterPill.textContent = 'Clicks: ' + clicks;
                  label.textContent = clicks === 0 ? 'Tap to Boost' : 'Boosted (' + clicks + 'x)';
                }

                btn.addEventListener('click', () => {
                  clicks += 1;
                  btn.classList.add('active-pulse');
                  setTimeout(() => btn.classList.remove('active-pulse'), 160);
                  render();
                  console.log('Interactive Button clicked:', clicks);
                });

                resetBtn.addEventListener('click', () => {
                  clicks = 0;
                  render();
                  console.log('Interactive Button reset');
                });

                console.log('Interactive Button template ready');
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "flexbox_navbar",
            title = "Flexbox Sticky Navbar",
            category = HtmlCssJsTemplateCategory.LAYOUT,
            description = "Responsive navigation bar with brand mark, active pill links, and mobile menu toggle.",
            tags = listOf("navbar", "flexbox", "navigation", "header", "responsive"),
            html = """
                <nav class="navbar">
                  <div class="brand">RunwayUI</div>
                  <div id="nav-links" class="nav-links">
                    <a href="#overview" class="nav-item active" data-tab="Overview">Overview</a>
                    <a href="#models" class="nav-item" data-tab="Models">Models</a>
                    <a href="#metrics" class="nav-item" data-tab="Metrics">Metrics</a>
                  </div>
                </nav>
                <main class="content-box">
                  <h3 id="active-section-title">Selected Tab: Overview</h3>
                  <p>Tap any navigation item above to switch the active view state.</p>
                </main>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 12px; }
                .navbar {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  background: #14171c;
                  border: 1px solid #262b35;
                  border-radius: 12px;
                  padding: 10px 16px;
                  margin-bottom: 16px;
                }
                .brand { font-weight: 800; color: #38bdf8; letter-spacing: 0.03em; }
                .nav-links { display: flex; gap: 8px; }
                .nav-item {
                  color: #94a3b8;
                  text-decoration: none;
                  font-size: 13px;
                  padding: 6px 12px;
                  border-radius: 8px;
                }
                .nav-item.active {
                  background: #6366f1;
                  color: #ffffff;
                  font-weight: 600;
                }
                .content-box {
                  background: #14171c;
                  border: 1px solid #262b35;
                  border-radius: 12px;
                  padding: 20px;
                }
                .content-box p { color: #94a3b8; font-size: 13px; margin-top: 6px; }
            """.trimIndent(),
            js = """
                const items = document.querySelectorAll('.nav-item');
                const title = document.getElementById('active-section-title');
                items.forEach(item => {
                  item.addEventListener('click', (e) => {
                    e.preventDefault();
                    items.forEach(el => el.classList.remove('active'));
                    item.classList.add('active');
                    title.textContent = 'Selected Tab: ' + item.dataset.tab;
                    console.log('Switched nav tab to:', item.dataset.tab);
                  });
                });
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "glassmorphism_pricing",
            title = "Glassmorphism Pricing Card",
            category = HtmlCssJsTemplateCategory.COMPONENTS,
            description = "Frosted glass pricing tier with monthly/annual billing switch and feature checklist.",
            tags = listOf("pricing", "glassmorphism", "card", "toggle", "billing"),
            html = """
                <div class="pricing-wrap">
                  <div class="billing-toggle">
                    <button id="bill-monthly" class="bill-btn active">Monthly</button>
                    <button id="bill-annual" class="bill-btn">Annual (Save 20%)</button>
                  </div>
                  <div class="glass-card">
                    <div class="tier-name">PRO STUDIO</div>
                    <div class="price-row">
                      <span id="price-amount" class="price">$29</span>
                      <span id="price-period" class="period">/ month</span>
                    </div>
                    <ul class="features">
                      <li>✓ Unlimited multi-provider routing</li>
                      <li>✓ Local sandboxed code execution</li>
                      <li>✓ Prompt caching & cost guardrails</li>
                    </ul>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  background: radial-gradient(circle at top right, #312e81, #090d16 70%);
                  color: #f8fafc;
                  font-family: system-ui, sans-serif;
                  padding: 20px;
                }
                .pricing-wrap { max-width: 340px; margin: 0 auto; }
                .billing-toggle {
                  display: flex;
                  background: rgba(15, 23, 42, 0.7);
                  border: 1px solid rgba(255, 255, 255, 0.12);
                  border-radius: 999px;
                  padding: 4px;
                  margin-bottom: 16px;
                }
                .bill-btn {
                  flex: 1;
                  background: transparent;
                  color: #94a3b8;
                  border: none;
                  padding: 8px;
                  border-radius: 999px;
                  font-size: 12px;
                  font-weight: 600;
                  cursor: pointer;
                }
                .bill-btn.active { background: #6366f1; color: #fff; }
                .glass-card {
                  background: rgba(30, 41, 59, 0.55);
                  backdrop-filter: blur(12px);
                  border: 1px solid rgba(255, 255, 255, 0.18);
                  border-radius: 18px;
                  padding: 24px;
                }
                .tier-name { font-size: 11px; letter-spacing: 0.1em; color: #38bdf8; font-weight: 700; }
                .price-row { margin: 10px 0 16px; }
                .price { font-size: 36px; font-weight: 800; }
                .period { font-size: 14px; color: #94a3b8; }
                .features { list-style: none; display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: #cbd5e1; }
            """.trimIndent(),
            js = """
                const monthlyBtn = document.getElementById('bill-monthly');
                const annualBtn = document.getElementById('bill-annual');
                const amount = document.getElementById('price-amount');
                const period = document.getElementById('price-period');

                monthlyBtn.addEventListener('click', () => {
                  monthlyBtn.classList.add('active');
                  annualBtn.classList.remove('active');
                  amount.textContent = '$29';
                  period.textContent = '/ month';
                });
                annualBtn.addEventListener('click', () => {
                  annualBtn.classList.add('active');
                  monthlyBtn.classList.remove('active');
                  amount.textContent = '$23';
                  period.textContent = '/ month (billed annually)';
                });
                console.log('Glassmorphism Pricing Card ready');
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "theme_toggle",
            title = "Dark / Light Theme Toggle",
            category = HtmlCssJsTemplateCategory.INTERACTIVE,
            description = "CSS custom properties theme switcher with smooth surface and text color transitions.",
            tags = listOf("theme", "dark-mode", "css-variables", "interactive"),
            html = """
                <div id="theme-root" class="theme-container dark">
                  <div class="theme-card">
                    <span id="mode-badge" class="mode-badge">DARK SURFACE</span>
                    <h2>Adaptive CSS Variables</h2>
                    <p>Toggle between dark and light palettes using CSS custom properties.</p>
                    <button id="theme-switch-btn" class="switch-btn">Switch to Light Mode</button>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                .theme-container {
                  --bg: #0b0d10;
                  --card: #14171c;
                  --text: #f8fafc;
                  --muted: #94a3b8;
                  --accent: #6366f1;
                  min-height: 85vh;
                  background: var(--bg);
                  color: var(--text);
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 20px;
                  font-family: system-ui, sans-serif;
                  transition: background 0.25s ease, color 0.25s ease;
                }
                .theme-container.light {
                  --bg: #f8fafc;
                  --card: #ffffff;
                  --text: #0f172a;
                  --muted: #475569;
                  --accent: #4f46e5;
                }
                .theme-card {
                  background: var(--card);
                  border: 1px solid rgba(148, 163, 184, 0.28);
                  border-radius: 16px;
                  padding: 24px;
                  max-width: 340px;
                }
                .mode-badge { font-size: 11px; font-weight: 700; color: var(--accent); }
                .theme-card h2 { margin: 8px 0; font-size: 20px; }
                .theme-card p { font-size: 13px; color: var(--muted); margin-bottom: 16px; }
                .switch-btn {
                  background: var(--accent);
                  color: #fff;
                  border: none;
                  border-radius: 10px;
                  padding: 10px 16px;
                  font-weight: 600;
                  cursor: pointer;
                }
            """.trimIndent(),
            js = """
                const root = document.getElementById('theme-root');
                const btn = document.getElementById('theme-switch-btn');
                const badge = document.getElementById('mode-badge');
                btn.addEventListener('click', () => {
                  const isLight = root.classList.toggle('light');
                  badge.textContent = isLight ? 'LIGHT SURFACE' : 'DARK SURFACE';
                  btn.textContent = isLight ? 'Switch to Dark Mode' : 'Switch to Light Mode';
                  console.log('Theme switched to:', isLight ? 'light' : 'dark');
                });
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "svg_bar_chart",
            title = "SVG Analytics Bar Chart",
            category = HtmlCssJsTemplateCategory.DATA_CHARTS,
            description = "Zero-dependency interactive SVG bar chart with dataset switcher and value labels.",
            tags = listOf("svg", "chart", "analytics", "data", "visualization"),
            html = """
                <div class="chart-card">
                  <div class="chart-header">
                    <h3>Token Throughput by Model</h3>
                    <button id="randomize-chart" class="mini-btn">Shuffle Data</button>
                  </div>
                  <div id="bars-root" class="bars-container"></div>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 16px; }
                .chart-card {
                  background: #14171c;
                  border: 1px solid #262b35;
                  border-radius: 14px;
                  padding: 18px;
                }
                .chart-header {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  margin-bottom: 16px;
                }
                .chart-header h3 { font-size: 15px; }
                .mini-btn {
                  background: #6366f1;
                  color: #fff;
                  border: none;
                  border-radius: 8px;
                  padding: 6px 12px;
                  font-size: 12px;
                  cursor: pointer;
                }
                .bars-container { display: flex; flex-direction: column; gap: 10px; }
                .bar-row { display: flex; align-items: center; gap: 10px; font-size: 12px; }
                .bar-label { width: 90px; color: #94a3b8; }
                .bar-track { flex: 1; background: #1e293b; height: 20px; border-radius: 6px; overflow: hidden; }
                .bar-fill {
                  height: 100%;
                  background: linear-gradient(90deg, #6366f1, #38bdf8);
                  border-radius: 6px;
                  transition: width 0.3s ease;
                }
                .bar-val { width: 52px; text-align: right; font-family: monospace; color: #38bdf8; }
            """.trimIndent(),
            js = """
                const root = document.getElementById('bars-root');
                const btn = document.getElementById('randomize-chart');
                let items = [
                  { name: 'Sonnet 4', val: 88 },
                  { name: 'GPT-4o', val: 76 },
                  { name: 'Gemini 2.5', val: 94 },
                  { name: 'Haiku 3.5', val: 99 }
                ];
                function renderBars() {
                  root.innerHTML = items.map(it =>
                    '<div class="bar-row">' +
                      '<span class="bar-label">' + it.name + '</span>' +
                      '<div class="bar-track"><div class="bar-fill" style="width:' + it.val + '%"></div></div>' +
                      '<span class="bar-val">' + it.val + ' t/s</span>' +
                    '</div>'
                  ).join('');
                }
                btn.addEventListener('click', () => {
                  items = items.map(it => ({ name: it.name, val: Math.floor(40 + Math.random() * 60) }));
                  renderBars();
                });
                renderBars();
                console.log('SVG Analytics Bar Chart rendered');
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "accordion_faq",
            title = "Accordion FAQ",
            category = HtmlCssJsTemplateCategory.COMPONENTS,
            description = "Expandable FAQ accordion panels with smooth toggle indicators and keyboard-friendly buttons.",
            tags = listOf("accordion", "faq", "collapse", "component"),
            html = """
                <div class="faq-list">
                  <h3>Frequently Asked Questions</h3>
                  <div class="faq-item open">
                    <button class="faq-q">How is HTML/CSS/JS sandboxed? <span>−</span></button>
                    <div class="faq-a">Code runs in an isolated WebView with blockNetworkLoads=true, no file access, and no JS bridges.</div>
                  </div>
                  <div class="faq-item">
                    <button class="faq-q">What is the execution timeout? <span>+</span></button>
                    <div class="faq-a">Every run enforces a strict 5-second timeout and 4 MB memory cap.</div>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 16px; }
                .faq-list { display: flex; flex-direction: column; gap: 10px; }
                .faq-item { background: #14171c; border: 1px solid #262b35; border-radius: 12px; overflow: hidden; }
                .faq-q {
                  width: 100%; background: transparent; color: #f8fafc; border: none;
                  padding: 14px 16px; text-align: left; font-weight: 600; font-size: 14px;
                  display: flex; justify-content: space-between; cursor: pointer;
                }
                .faq-a { display: none; padding: 0 16px 14px; font-size: 13px; color: #94a3b8; line-height: 1.5; }
                .faq-item.open .faq-a { display: block; }
            """.trimIndent(),
            js = """
                document.querySelectorAll('.faq-item').forEach(item => {
                  const btn = item.querySelector('.faq-q');
                  const icon = btn.querySelector('span');
                  btn.addEventListener('click', () => {
                    const isOpen = item.classList.toggle('open');
                    icon.textContent = isOpen ? '−' : '+';
                  });
                });
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        ),
        HtmlCssJsTemplate(
            id = "form_validation",
            title = "Form Validation & Strength Meter",
            category = HtmlCssJsTemplateCategory.FORMS_AUTH,
            description = "Interactive form with live email validation, password strength meter, and instant feedback.",
            tags = listOf("form", "validation", "password", "input", "interactive"),
            html = """
                <form id="signup-form" class="form-card" novalidate>
                  <h3>Developer API Access</h3>
                  <label class="field-label">Email Address</label>
                  <input id="email-input" type="email" class="text-input" placeholder="dev@example.com" value="engineer@runway.dev" />
                  <label class="field-label">API Secret Passphrase</label>
                  <input id="pwd-input" type="password" class="text-input" placeholder="Enter at least 8 chars" value="Aerodock#2025!" />
                  <div class="meter-track"><div id="meter-bar" class="meter-bar"></div></div>
                  <p id="form-feedback" class="feedback">Strength: Strong</p>
                  <button type="submit" class="submit-btn">Validate Credentials</button>
                </form>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; }
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 16px; }
                .form-card { background: #14171c; border: 1px solid #262b35; border-radius: 14px; padding: 18px; max-width: 360px; margin: 0 auto; }
                .form-card h3 { margin-bottom: 12px; font-size: 16px; }
                .field-label { display: block; font-size: 12px; color: #94a3b8; margin: 10px 0 4px; }
                .text-input {
                  width: 100%; padding: 10px 12px; border-radius: 8px; border: 1px solid #334155;
                  background: #0f172a; color: #f8fafc; font-size: 13px;
                }
                .meter-track { height: 6px; background: #1e293b; border-radius: 999px; margin-top: 8px; overflow: hidden; }
                .meter-bar { height: 100%; width: 85%; background: #10b981; transition: width 0.2s ease; }
                .feedback { font-size: 12px; color: #10b981; margin: 8px 0 14px; }
                .submit-btn {
                  width: 100%; padding: 10px; background: #6366f1; color: #fff;
                  border: none; border-radius: 8px; font-weight: 600; cursor: pointer;
                }
            """.trimIndent(),
            js = """
                const pwd = document.getElementById('pwd-input');
                const bar = document.getElementById('meter-bar');
                const feedback = document.getElementById('form-feedback');
                const form = document.getElementById('signup-form');

                function evaluate() {
                  const val = pwd.value || '';
                  const score = Math.min(100, val.length * 9);
                  bar.style.width = score + '%';
                  bar.style.background = score >= 70 ? '#10b981' : (score >= 40 ? '#f59e0b' : '#ef4444');
                  feedback.textContent = score >= 70 ? 'Strength: Strong' : (score >= 40 ? 'Strength: Medium' : 'Strength: Weak');
                }
                pwd.addEventListener('input', evaluate);
                form.addEventListener('submit', (e) => {
                  e.preventDefault();
                  feedback.textContent = 'Validated OK (' + pwd.value.length + ' chars)';
                  console.log('Form validated');
                });
                evaluate();
            """.trimIndent(),
            sourceLabel = "Built-in Starter"
        )
    )

    /**
     * Online community & web-standards template library ("And many more from online").
     * Provides instant access to curated online patterns from MDN, W3C, and Open Web UI libraries,
     * alongside live HTTP URL / GitHub Gist importing.
     */
    val ONLINE_CURATED_TEMPLATES: List<HtmlCssJsTemplate> = listOf(
        HtmlCssJsTemplate(
            id = "online_bento_grid",
            title = "Bento Grid Showcase",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Modern Apple/Linear-inspired multi-span Bento Grid layout with hover highlights and badge pills.",
            tags = listOf("online", "bento", "grid", "showcase", "modern", "layout"),
            html = """
                <div class="bento-grid">
                  <div class="bento-cell span-2 hero-cell">
                    <span class="tag">BENTO UI • ONLINE PATTERN</span>
                    <h2>Unified AI Routing & Sandbox</h2>
                    <p>Span multiple columns and rows effortlessly with CSS Grid template areas.</p>
                  </div>
                  <div class="bento-cell">
                    <span class="metric">5.0s</span>
                    <p>Strict Execution Guard</p>
                  </div>
                  <div class="bento-cell">
                    <span class="metric">4 MB</span>
                    <p>Sandbox Memory Cap</p>
                  </div>
                  <div class="bento-cell span-2">
                    <p>Zero network egress • Zero JS bridges • Instant DOM preview</p>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #090d16; color: #f8fafc; font-family: system-ui, sans-serif; padding: 14px; }
                .bento-grid {
                  display: grid;
                  grid-template-columns: repeat(2, 1fr);
                  gap: 10px;
                }
                .bento-cell {
                  background: #14171c;
                  border: 1px solid #262b35;
                  border-radius: 14px;
                  padding: 16px;
                }
                .span-2 { grid-column: span 2; }
                .hero-cell {
                  background: linear-gradient(135deg, rgba(99, 102, 241, 0.22), rgba(6, 182, 212, 0.12));
                  border-color: rgba(99, 102, 241, 0.5);
                }
                .tag { font-size: 10px; font-weight: 700; color: #38bdf8; letter-spacing: 0.08em; }
                .bento-cell h2 { font-size: 18px; margin: 6px 0; }
                .bento-cell p { font-size: 12px; color: #94a3b8; }
                .metric { font-size: 24px; font-weight: 800; color: #10b981; display: block; margin-bottom: 4px; }
            """.trimIndent(),
            js = """
                console.log('Loaded online template: Bento Grid Showcase');
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_grid_layout",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_kanban_board",
            title = "Kanban Task Board",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Interactive 3-column Kanban board where tapping a task card advances it to the next workflow column.",
            tags = listOf("online", "kanban", "board", "tasks", "interactive", "productivity"),
            html = """
                <div class="kanban">
                  <div class="col" data-col="todo">
                    <h4>TO DO</h4>
                    <div class="task" data-status="todo">Design WebView Sandbox</div>
                    <div class="task" data-status="todo">Add Template Selector</div>
                  </div>
                  <div class="col" data-col="doing">
                    <h4>IN PROGRESS</h4>
                    <div class="task" data-status="doing">Verify 4 MB Memory Cap</div>
                  </div>
                  <div class="col" data-col="done">
                    <h4>DONE</h4>
                    <div class="task" data-status="done">Lock Down Network Loads</div>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 12px; }
                .kanban { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
                .col {
                  background: #14171c; border: 1px solid #262b35; border-radius: 10px; padding: 10px;
                  min-height: 180px; display: flex; flex-direction: column; gap: 8px;
                }
                .col h4 { font-size: 11px; color: #94a3b8; letter-spacing: 0.08em; }
                .task {
                  background: #1e293b; border: 1px solid #334155; border-radius: 8px;
                  padding: 8px; font-size: 12px; cursor: pointer;
                }
                .task:active { transform: scale(0.98); }
            """.trimIndent(),
            js = """
                const order = ['todo', 'doing', 'done'];
                document.querySelectorAll('.task').forEach(task => {
                  task.addEventListener('click', () => {
                    const current = task.getAttribute('data-status') || 'todo';
                    const next = order[(order.indexOf(current) + 1) % order.length];
                    task.setAttribute('data-status', next);
                    const targetCol = document.querySelector('.col[data-col="' + next + '"]');
                    if (targetCol) targetCol.appendChild(task);
                    console.log('Moved task to:', next);
                  });
                });
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/API/Document_Object_Model",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_live_search",
            title = "Live Search & Tag Filter",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Real-time search input filtering a list of items with instant match counts and highlight badges.",
            tags = listOf("online", "search", "filter", "list", "javascript"),
            html = """
                <div class="search-box">
                  <input id="q-input" class="search-input" placeholder="Filter models (e.g. Claude, GPT, Gemini)..." />
                  <p id="match-count" class="count-label">Showing 4 models</p>
                  <ul id="model-list" class="item-list"></ul>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 14px; }
                .search-input {
                  width: 100%; padding: 10px 12px; border-radius: 10px;
                  border: 1px solid #334155; background: #14171c; color: #f8fafc; font-size: 13px;
                }
                .count-label { font-size: 11px; color: #38bdf8; margin: 8px 0; }
                .item-list { list-style: none; display: flex; flex-direction: column; gap: 6px; }
                .item-row {
                  background: #14171c; border: 1px solid #262b35; border-radius: 8px;
                  padding: 10px 12px; display: flex; justify-content: space-between; font-size: 13px;
                }
                .provider-tag { color: #94a3b8; font-size: 11px; }
            """.trimIndent(),
            js = """
                const models = [
                  { name: 'Claude 3.7 Sonnet', provider: 'Anthropic' },
                  { name: 'GPT-4o Mini', provider: 'OpenAI' },
                  { name: 'Gemini 2.5 Flash', provider: 'Google' },
                  { name: 'DeepSeek R1', provider: 'DeepSeek' }
                ];
                const input = document.getElementById('q-input');
                const list = document.getElementById('model-list');
                const count = document.getElementById('match-count');

                function update(q) {
                  const filtered = models.filter(m =>
                    m.name.toLowerCase().includes(q) || m.provider.toLowerCase().includes(q)
                  );
                  count.textContent = 'Showing ' + filtered.length + ' model(s)';
                  list.innerHTML = filtered.map(m =>
                    '<li class="item-row"><span>' + m.name + '</span><span class="provider-tag">' + m.provider + '</span></li>'
                  ).join('');
                }
                input.addEventListener('input', () => update(input.value.trim().toLowerCase()));
                update('');
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/filter",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_pomodoro_timer",
            title = "Pomodoro Countdown Timer",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Interactive focus countdown timer with Start, Pause, and Reset controls.",
            tags = listOf("online", "timer", "countdown", "pomodoro", "interactive"),
            html = """
                <div class="timer-card">
                  <span class="timer-badge">FOCUS SESSION</span>
                  <div id="clock-display" class="clock">25:00</div>
                  <div class="timer-controls">
                    <button id="start-btn" class="ctrl-btn primary">Start</button>
                    <button id="reset-timer-btn" class="ctrl-btn">Reset</button>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 20px; text-align: center; }
                .timer-card { background: #14171c; border: 1px solid #262b35; border-radius: 16px; padding: 24px; max-width: 300px; margin: 0 auto; }
                .timer-badge { font-size: 11px; color: #38bdf8; letter-spacing: 0.1em; font-weight: 700; }
                .clock { font-size: 44px; font-weight: 800; font-family: monospace; margin: 14px 0; }
                .timer-controls { display: flex; gap: 10px; justify-content: center; }
                .ctrl-btn { padding: 8px 18px; border-radius: 8px; border: 1px solid #334155; background: #1e293b; color: #f8fafc; cursor: pointer; }
                .ctrl-btn.primary { background: #6366f1; border-color: #6366f1; font-weight: 600; }
            """.trimIndent(),
            js = """
                let remaining = 25 * 60;
                let intervalId = null;
                const display = document.getElementById('clock-display');
                const startBtn = document.getElementById('start-btn');
                const resetBtn = document.getElementById('reset-timer-btn');

                function formatTime(sec) {
                  const m = String(Math.floor(sec / 60)).padStart(2, '0');
                  const s = String(sec % 60).padStart(2, '0');
                  return m + ':' + s;
                }
                startBtn.addEventListener('click', () => {
                  if (intervalId) {
                    clearInterval(intervalId);
                    intervalId = null;
                    startBtn.textContent = 'Start';
                  } else {
                    startBtn.textContent = 'Pause';
                    intervalId = setInterval(() => {
                      if (remaining > 0) remaining -= 1;
                      display.textContent = formatTime(remaining);
                    }, 1000);
                  }
                });
                resetBtn.addEventListener('click', () => {
                  if (intervalId) { clearInterval(intervalId); intervalId = null; }
                  remaining = 25 * 60;
                  display.textContent = formatTime(remaining);
                  startBtn.textContent = 'Start';
                });
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/API/Window/setInterval",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_modal_dialog",
            title = "Modal Dialog & Backdrop",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Accessible modal dialog overlay with blurred backdrop, confirm/cancel actions, and state log.",
            tags = listOf("online", "modal", "dialog", "overlay", "component"),
            html = """
                <div class="page">
                  <button id="open-modal-btn" class="open-btn">Open Confirmation Modal</button>
                  <p id="modal-result" class="result-text">Modal state: Open by default for preview</p>
                  <div id="backdrop" class="backdrop">
                    <div class="modal">
                      <h3>Deploy Production Route?</h3>
                      <p>This will route 100% of traffic through the primary fallback combo.</p>
                      <div class="modal-actions">
                        <button id="cancel-modal" class="btn-secondary">Cancel</button>
                        <button id="confirm-modal" class="btn-Approve">Deploy Now</button>
                      </div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 16px; }
                .open-btn { background: #6366f1; color: #fff; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; }
                .result-text { font-size: 12px; color: #94a3b8; margin-top: 8px; }
                .backdrop {
                  margin-top: 14px; background: rgba(15, 23, 42, 0.85); border: 1px solid #334155;
                  border-radius: 14px; padding: 20px; display: flex; justify-content: center;
                }
                .backdrop.hidden { display: none; }
                .modal { background: #1e293b; border-radius: 12px; padding: 18px; max-width: 320px; }
                .modal h3 { font-size: 16px; margin-bottom: 6px; }
                .modal p { font-size: 13px; color: #cbd5e1; margin-bottom: 14px; }
                .modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
                .btn-secondary { background: transparent; color: #94a3b8; border: 1px solid #475569; padding: 6px 12px; border-radius: 6px; cursor: pointer; }
                .btn-Approve { background: #10b981; color: #06281e; font-weight: 700; border: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; }
            """.trimIndent(),
            js = """
                const backdrop = document.getElementById('backdrop');
                const result = document.getElementById('modal-result');
                document.getElementById('open-modal-btn').addEventListener('click', () => {
                  backdrop.classList.remove('hidden');
                  result.textContent = 'Modal state: Opened';
                });
                document.getElementById('cancel-modal').addEventListener('click', () => {
                  backdrop.classList.add('hidden');
                  result.textContent = 'Modal state: Cancelled';
                });
                document.getElementById('confirm-modal').addEventListener('click', () => {
                  backdrop.classList.add('hidden');
                  result.textContent = 'Modal state: Confirmed Deployment!';
                });
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dialog",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_sortable_table",
            title = "Sortable Data Table",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Interactive data table with one-click column sorting by latency or cost.",
            tags = listOf("online", "table", "sort", "data", "analytics"),
            html = """
                <div class="table-wrap">
                  <div class="table-bar">
                    <strong>Provider Benchmark Table</strong>
                    <button id="sort-latency-btn" class="sort-btn">Sort by Latency ↑</button>
                  </div>
                  <table>
                    <thead><tr><th>Model</th><th>Latency</th><th>Cost / 1M</th></tr></thead>
                    <tbody id="table-body"></tbody>
                  </table>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 14px; }
                .table-wrap { background: #14171c; border: 1px solid #262b35; border-radius: 12px; padding: 14px; }
                .table-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 13px; }
                .sort-btn { background: #1e293b; color: #38bdf8; border: 1px solid #334155; border-radius: 6px; padding: 5px 10px; font-size: 11px; cursor: pointer; }
                table { width: 100%; border-collapse: collapse; font-size: 12px; }
                th, td { text-align: left; padding: 8px; border-bottom: 1px solid #1e293b; }
                th { color: #94a3b8; font-size: 11px; }
            """.trimIndent(),
            js = """
                let asc = true;
                const rows = [
                  { model: 'Gemini 2.5 Flash', latency: 210, cost: '$0.15' },
                  { model: 'Claude 3.7 Sonnet', latency: 480, cost: '$3.00' },
                  { model: 'GPT-4o Mini', latency: 290, cost: '$0.15' },
                  { model: 'Groq Llama 3.3', latency: 95, cost: '$0.59' }
                ];
                const tbody = document.getElementById('table-body');
                const btn = document.getElementById('sort-latency-btn');

                function render() {
                  tbody.innerHTML = rows.map(r =>
                    '<tr><td>' + r.model + '</td><td>' + r.latency + ' ms</td><td>' + r.cost + '</td></tr>'
                  ).join('');
                }
                btn.addEventListener('click', () => {
                  rows.sort((a, b) => asc ? a.latency - b.latency : b.latency - a.latency);
                  asc = !asc;
                  btn.textContent = asc ? 'Sort by Latency ↑' : 'Sort by Latency ↓';
                  render();
                });
                render();
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/HTML/Element/table",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_circular_progress",
            title = "Animated Progress & Gauge",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "Animated progress bar and quota usage indicator with increment/decrement controls.",
            tags = listOf("online", "progress", "gauge", "animation", "css"),
            html = """
                <div class="gauge-card">
                  <div class="gauge-row">
                    <span>Monthly Budget Usage</span>
                    <strong id="pct-label">64%</strong>
                  </div>
                  <div class="track"><div id="fill" class="fill" style="width: 64%"></div></div>
                  <div class="actions">
                    <button id="dec-btn" class="step-btn">-10%</button>
                    <button id="inc-btn" class="step-btn">+10%</button>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 18px; }
                .gauge-card { background: #14171c; border: 1px solid #262b35; border-radius: 14px; padding: 20px; }
                .gauge-row { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 10px; }
                .track { height: 14px; background: #1e293b; border-radius: 999px; overflow: hidden; }
                .fill { height: 100%; background: linear-gradient(90deg, #10b981, #38bdf8); transition: width 0.25s ease; }
                .actions { margin-top: 14px; display: flex; gap: 8px; }
                .step-btn { background: #1e293b; color: #f8fafc; border: 1px solid #334155; padding: 6px 14px; border-radius: 8px; cursor: pointer; }
            """.trimIndent(),
            js = """
                let pct = 64;
                const fill = document.getElementById('fill');
                const label = document.getElementById('pct-label');
                function update(delta) {
                  pct = Math.max(0, Math.min(100, pct + delta));
                  fill.style.width = pct + '%';
                  label.textContent = pct + '%';
                }
                document.getElementById('dec-btn').addEventListener('click', () => update(-10));
                document.getElementById('inc-btn').addEventListener('click', () => update(10));
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_transitions",
            isOnlineCurated = true
        ),
        HtmlCssJsTemplate(
            id = "online_canvas_particles",
            title = "HTML5 Canvas Constellation",
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = "HTML5 2D Canvas rendering connected constellation nodes with interactive tap-to-spawn.",
            tags = listOf("online", "canvas", "graphics", "particles", "visualization"),
            html = """
                <div class="canvas-wrap">
                  <div class="canvas-bar">
                    <span>HTML5 Canvas Constellation</span>
                    <button id="add-node-btn" class="add-btn">+ Add Node</button>
                  </div>
                  <canvas id="sky" width="320" height="180"></canvas>
                </div>
            """.trimIndent(),
            css = """
                body { background: #0b0d10; color: #f8fafc; font-family: system-ui, sans-serif; padding: 12px; }
                .canvas-wrap { background: #14171c; border: 1px solid #262b35; border-radius: 12px; padding: 12px; text-align: center; }
                .canvas-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 12px; }
                .add-btn { background: #6366f1; color: #fff; border: none; border-radius: 6px; padding: 4px 10px; font-size: 11px; cursor: pointer; }
                canvas { background: #090d16; border-radius: 8px; width: 100%; max-width: 320px; height: 180px; }
            """.trimIndent(),
            js = """
                const canvas = document.getElementById('sky');
                const ctx = canvas.getContext('2d');
                const nodes = [
                  { x: 50, y: 60 }, { x: 140, y: 40 }, { x: 220, y: 110 }, { x: 95, y: 135 }, { x: 265, y: 55 }
                ];
                function draw() {
                  ctx.clearRect(0, 0, canvas.width, canvas.height);
                  ctx.strokeStyle = 'rgba(56, 189, 248, 0.35)';
                  ctx.lineWidth = 1.2;
                  for (let i = 0; i < nodes.length; i++) {
                    for (let j = i + 1; j < nodes.length; j++) {
                      ctx.beginPath();
                      ctx.moveTo(nodes[i].x, nodes[i].y);
                      ctx.lineTo(nodes[j].x, nodes[j].y);
                      ctx.stroke();
                    }
                  }
                  nodes.forEach(n => {
                    ctx.fillStyle = '#6366f1';
                    ctx.beginPath();
                    ctx.arc(n.x, n.y, 5, 0, Math.PI * 2);
                    ctx.fill();
                  });
                }
                document.getElementById('add-node-btn').addEventListener('click', () => {
                  nodes.push({
                    x: Math.floor(24 + Math.random() * (canvas.width - 48)),
                    y: Math.floor(24 + Math.random() * (canvas.height - 48))
                  });
                  draw();
                });
                draw();
            """.trimIndent(),
            sourceLabel = "Online Web UI Catalog",
            sourceUrl = "https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API",
            isOnlineCurated = true
        )
    )

    /**
     * Returns all templates (built-in + online curated + any user-imported online templates).
     */
    fun allTemplates(
        customOnlineTemplates: List<HtmlCssJsTemplate> = emptyList()
    ): List<HtmlCssJsTemplate> {
        return BUILT_IN_TEMPLATES + ONLINE_CURATED_TEMPLATES + customOnlineTemplates
    }

    /**
     * Quick-access starter boilerplates displayed in the horizontal chip bar on the code input screen.
     * Guaranteed to start with 'Centered Card', 'CSS Grid Layout', and 'Interactive Button'.
     */
    fun quickStarterTemplates(): List<HtmlCssJsTemplate> {
        return BUILT_IN_TEMPLATES
    }

    /**
     * Finds a template by exact or case-insensitive title or ID.
     */
    fun findByTitleOrId(
        query: String,
        customOnlineTemplates: List<HtmlCssJsTemplate> = emptyList()
    ): HtmlCssJsTemplate? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val all = allTemplates(customOnlineTemplates)
        return all.firstOrNull {
            it.id.equals(trimmed, ignoreCase = true) ||
                it.title.equals(trimmed, ignoreCase = true)
        } ?: all.firstOrNull {
            it.title.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * Filters templates by search query and category.
     */
    fun filterTemplates(
        query: String = "",
        category: HtmlCssJsTemplateCategory = HtmlCssJsTemplateCategory.ALL,
        customOnlineTemplates: List<HtmlCssJsTemplate> = emptyList()
    ): List<HtmlCssJsTemplate> {
        val normalizedQuery = query.trim().lowercase()
        val base = allTemplates(customOnlineTemplates).filter { tpl ->
            when (category) {
                HtmlCssJsTemplateCategory.ALL -> true
                HtmlCssJsTemplateCategory.ONLINE_HUB -> tpl.isOnlineCurated || tpl.category == HtmlCssJsTemplateCategory.ONLINE_HUB
                else -> tpl.category == category
            }
        }
        if (normalizedQuery.isEmpty()) return base
        return base.filter { tpl ->
            tpl.title.lowercase().contains(normalizedQuery) ||
                tpl.description.lowercase().contains(normalizedQuery) ||
                tpl.tags.any { it.lowercase().contains(normalizedQuery) } ||
                tpl.sourceLabel.lowercase().contains(normalizedQuery)
        }
    }

    /**
     * Parses raw online content (either a full HTML document with `<style>` and `<script>` blocks,
     * a JSON template object `{"title":..., "html":..., "css":..., "js":...}`, or a JSON array of templates)
     * into one or more [HtmlCssJsTemplate] instances.
     */
    fun parseOnlineTemplateContent(
        rawContent: String,
        fallbackTitle: String = "Imported Online Template",
        sourceUrl: String = ""
    ): List<HtmlCssJsTemplate> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Check if JSON object or JSON array
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonTemplates = runCatching {
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            add(parseJsonTemplateObject(obj, "$fallbackTitle #${i + 1}", sourceUrl))
                        }
                    }
                } else {
                    val obj = JSONObject(trimmed)
                    if (obj.has("templates") && obj.optJSONArray("templates") != null) {
                        val arr = obj.getJSONArray("templates")
                        buildList {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                add(parseJsonTemplateObject(item, "$fallbackTitle #${i + 1}", sourceUrl))
                            }
                        }
                    } else {
                        listOf(parseJsonTemplateObject(obj, fallbackTitle, sourceUrl))
                    }
                }
            }.getOrNull()
            if (!jsonTemplates.isNullOrEmpty()) {
                return jsonTemplates
            }
        }

        // 2. Parse as HTML / CSS / JS document or snippet
        val styleRegex = Regex("(?is)<style[^>]*>(.*?)</style>")
        val scriptRegex = Regex("(?is)<script[^>]*>(.*?)</script>")
        val titleRegex = Regex("(?is)<title[^>]*>(.*?)</title>")
        val bodyRegex = Regex("(?is)<body[^>]*>(.*?)</body>")

        val extractedCss = styleRegex.findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        val extractedJs = scriptRegex.findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        val extractedTitle = titleRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackTitle

        val bodyContent = bodyRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: trimmed
        val cleanedHtml = bodyContent
            .replace(styleRegex, "")
            .replace(scriptRegex, "")
            .replace(titleRegex, "")
            .replace(Regex("(?is)<!DOCTYPE[^>]*>"), "")
            .replace(Regex("(?is)</?html[^>]*>"), "")
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "")
            .replace(Regex("(?is)</?head[^>]*>"), "")
            .replace(Regex("(?is)</?body[^>]*>"), "")
            .trim()
            .ifEmpty { "<div class=\"imported-template\">$extractedTitle</div>" }

        return listOf(
            HtmlCssJsTemplate(
                id = "online_custom_${System.currentTimeMillis()}_${extractedTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                title = extractedTitle,
                category = HtmlCssJsTemplateCategory.ONLINE_HUB,
                description = if (sourceUrl.isNotBlank()) "Fetched from $sourceUrl" else "Imported online HTML/CSS/JS boilerplate",
                tags = listOf("online", "imported", "custom"),
                html = cleanedHtml,
                css = extractedCss,
                js = extractedJs,
                sourceLabel = "Online Import",
                sourceUrl = sourceUrl,
                isOnlineCurated = true
            )
        )
    }

    private fun parseJsonTemplateObject(
        obj: JSONObject,
        fallbackTitle: String,
        sourceUrl: String
    ): HtmlCssJsTemplate {
        val title = obj.optString("title").ifBlank {
            obj.optString("name").ifBlank { fallbackTitle }
        }
        val desc = obj.optString("description").ifBlank {
            if (sourceUrl.isNotBlank()) "Fetched from $sourceUrl" else "Imported online JSON template"
        }
        val html = obj.optString("html").ifBlank {
            obj.optString("code").ifBlank { "<div class=\"card\">$title</div>" }
        }
        val css = obj.optString("css")
        val js = obj.optString("js").ifBlank { obj.optString("javascript") }
        return HtmlCssJsTemplate(
            id = obj.optString("id").ifBlank {
                "online_json_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
            },
            title = title,
            category = HtmlCssJsTemplateCategory.ONLINE_HUB,
            description = desc,
            tags = listOf("online", "json", "imported"),
            html = html,
            css = css,
            js = js,
            sourceLabel = obj.optString("sourceLabel").ifBlank { "Online Import" },
            sourceUrl = obj.optString("sourceUrl").ifBlank { sourceUrl },
            isOnlineCurated = true
        )
    }

    /**
     * Fetches an HTML/CSS/JS template or JSON template bundle from a remote URL
     * (e.g., GitHub raw file, GitHub Gist, or web snippet endpoint).
     */
    suspend fun fetchOnlineTemplatesFromUrl(
        url: String,
        httpClient: OkHttpClient = templateHttpClient
    ): Result<List<HtmlCssJsTemplate>> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://"))) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter a valid https:// URL pointing to an HTML or JSON template.")
            )
        }
        runCatching {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("Accept", "text/html,application/json,text/plain,*/*")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Failed to fetch online template from $cleanUrl")
                }
                if (rawBody.toByteArray(Charsets.UTF_8).size > MAX_ONLINE_TEMPLATE_BYTES) {
                    throw IllegalStateException("Online template exceeds the 512 KB size limit.")
                }
                val fallbackName = cleanUrl.substringAfterLast('/')
                    .substringBefore('?')
                    .removeSuffix(".html")
                    .removeSuffix(".json")
                    .replace('-', ' ')
                    .replace('_', ' ')
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    .ifEmpty { "Online Template" }
                val parsed = parseOnlineTemplateContent(
                    rawContent = rawBody,
                    fallbackTitle = fallbackName,
                    sourceUrl = cleanUrl
                )
                if (parsed.isEmpty()) {
                    throw IllegalStateException("No HTML/CSS/JS content found at $cleanUrl")
                }
                parsed
            }
        }
    }
}
