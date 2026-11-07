# Runway

**Your AI, your keys, your phone. Nothing else.**

Runway is a privacy-first AI workspace for Android. Bring any AI provider — or run your own model on your own server — and chat, compare models, generate images and video, and connect tools, all from one native app that keeps your data on your device and your API keys locked in hardware-backed encryption.

No Runway account. No Runway cloud. Your conversations live in a local database on your phone, and your keys never leave the Android Keystore.

---

## ✨ Features

- **Multi-provider chat, streaming live** — Real-time SSE streaming from OpenAI-compatible endpoints, Anthropic (`/v1/messages`) and Google Gemini (`streamGenerateContent`), with per-message token & estimated-cost footers and conversation-level usage totals.
- **Provider failover** *(new in 2.3)* — If your main provider goes down mid-chat, Runway automatically falls back to your next configured provider instead of failing.
- **Compare Mode** — Send one prompt to 2–3 models at once and read the answers side by side, with latency, token and cost metrics for each.
- **Use your OWN AI** — Point Runway at any OpenAI-compatible server: a model you host yourself (for example llama.cpp in a GitHub Codespace), a LAN machine, or a phone-local server. Your AI, your rules.
- **Connectors & remote MCP** — Discover and connect remote Model Context Protocol servers over Streamable HTTP or SSE (including a GitHub MCP preset), with per-connector and per-tool approval controls — `Always`, `Ask`, `Never`.
- **Sandboxed code execution** — An isolated JavaScript `run_code` tool with an execution timeout, memory cap, output truncation and user approval before anything runs.
- **Skills & prompt library** — Markdown `SKILL.md` instruction packs with frontmatter parsing and auto-selection, plus reusable prompt templates.
- **Studios** — Built-in image and video generation studios, and a local usage tracker so you always know what your AI habit costs.

## 🔒 Privacy, by design

- **Keys in the vault** — API keys and connector tokens are encrypted with Google Tink, backed by the Android Keystore on your device.
- **Local-first storage** — Chats, settings and usage data live in a Room database on the phone. Delete the app, and it's all gone.
- **You approve the tools** — MCP tools and code execution ask first, per tool, with `Always / Ask / Never` controls you set.
- **No middleman** — The app talks directly from your phone to the providers *you* configured. There is no Runway server in between.

## 📲 Download

**Latest release: [Runway 2.3](https://github.com/Farhan009-gif/Runway/releases/tag/v2.3)**

Download `app-debug.apk` from the release and install it (you may need to allow "install from unknown sources" for your browser/files app). The release APK is CI debug-signed — fine for sideloading and sharing, and it installs alongside nothing else under the package `com.example.runway`-lineage build.

> Runway needs your own API key or your own server endpoint — it is a workspace, not a subscription. Free routes work great: a self-hosted open model costs ₹0.

## 🛠️ Build it yourself

**Android Studio**
1. Clone the repo and open the project in Android Studio.
2. **Build → Build APK(s)** — the APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions**
Push to `main`/`master` (or trigger the **Build APK** workflow manually in the Actions tab) and download the APK from the workflow's artifacts.

## 🧱 Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Google Tink (Android Keystore) · OkHttp (SSE streaming) · MCP over Streamable HTTP/SSE

## 🗺️ Where it's going

A full visual redesign is in design review now — mockups first, code after approval. Further out: deeper offline/local-model support so more of the workspace works with the internet off.

---

Built by **Farhan** ([@farhan.now_](https://instagram.com/farhan.now_)) — a student developer in Bokaro, India. Runway is developed in the open; issues and ideas are welcome on GitHub.
