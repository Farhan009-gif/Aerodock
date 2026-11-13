# Runway

**Your AI, your keys, your phone. Nothing else.**

Runway is a privacy-first AI workspace for Android. Bring any AI provider — or run your own model on your own server — and chat, compare models, delegate work to sub-agents, generate images and video, and connect tools, all from one native app that keeps your data on your device and your API keys locked in hardware-backed encryption.

No Runway account. No Runway cloud. Your conversations live in a local database on your phone, and your keys never leave the Android Keystore.

---

## ✨ Features

- **Multi-provider chat, streaming live** — Real-time SSE streaming from OpenAI-compatible endpoints, Anthropic (`/v1/messages`) and Google Gemini (`streamGenerateContent`), with per-message token & estimated-cost footers and conversation-level usage totals.
- **Sub-agents** — The model can delegate work to sub-agents that run in the foreground or the background (up to 12 at once) and post their results back into the chat, or chain steps so each sub-agent builds on the previous one's result. Generous limits: 60 turns and 20 minutes per sub-agent.
- **Work modes** — Per-app control over how agentic Runway is: **Auto**, **Agent only** (tools, no chit-chat drift), or **Model only** (plain chat, no tools).
- **Groups** — Optional, off by default: sign in with Google, create a group or join with a 6-character invite code, and discuss in real time. An admin dashboard tracks group usage. Your API keys are never synced — groups carry messages, not credentials.
- **Connectors & remote MCP** — Discover and connect remote Model Context Protocol servers over Streamable HTTP or SSE (including a GitHub MCP preset), with per-connector and per-tool approval controls — `Always`, `Ask`, `Never`.
- **MCP OAuth sign-in** — Servers that protect themselves with OAuth (dynamic client registration + PKCE) can be connected with a normal browser sign-in — no pasted tokens. Tokens are stored in the encrypted vault, refresh automatically, and are never included in backups or exports. Works with servers like Onshape's FeatureScript MCP.
- **Tool calls that survive real models** — Free and self-hosted models don't always play by the rules. Runway recovers tool calls that arrive as plain text (JSON, Qwen XML and DeepSeek DSML formats), retries through transient rate limits and free-pool errors, and paces requests to shared free endpoints (a 7-second gate for LiteRouter) so chats don't die on the first 429.
- **Gemini, handled properly** — Tool schemas are sanitised to Gemini's restricted subset before sending (so MCP tools from servers like Composio don't trigger HTTP 400s), and thought signatures from Gemini thinking models are captured and replayed so multi-step tool tasks run to completion.
- **Provider failover** — If your main provider goes down mid-chat, Runway automatically falls back to your next configured provider instead of failing.
- **Compare Mode** — Send one prompt to 2–3 models at once and read the answers side by side, with latency, token and cost metrics for each.
- **Use your OWN AI** — Point Runway at any OpenAI-compatible server: a model you host yourself (for example llama.cpp in a GitHub Codespace), a LAN machine, or a phone-local server. Your AI, your rules.
- **Sandboxed code execution** — An isolated JavaScript `run_code` tool with an execution timeout, memory cap, output truncation and user approval before anything runs.
- **Skills & prompt library** — Markdown `SKILL.md` instruction packs with frontmatter parsing and auto-selection, plus reusable prompt templates.
- **Studios** — Built-in image and video generation studios, and a local usage tracker so you always know what your AI habit costs.
- **An interface that gets out of the way** — Home-first workspace with six accent themes (light and dark), a slim chat top bar you can fold away entirely when you want maximum room for the conversation, animated section transitions, inline previews for image links, queued messages while the model is working, and Enter for a new paragraph — the send button sends.

## 🔒 Privacy, by design

- **Keys in the vault** — API keys, connector tokens and OAuth tokens are encrypted with Google Tink, backed by the Android Keystore on your device.
- **Local-first storage** — Chats, settings and usage data live in a Room database on the phone. Delete the app, and it's all gone.
- **Groups are opt-in** — Group chat uses Firebase (Google sign-in + Firestore) only if you turn it on. Only group messages sync; your provider keys and personal chats never leave the device.
- **You approve the tools** — MCP tools and code execution ask first, per tool, with `Always / Ask / Never` controls you set.
- **No middleman** — The app talks directly from your phone to the providers *you* configured. There is no Runway server in between.

## 📲 Download

**Latest release: [Runway 2.3](https://github.com/Farhan009-gif/Runway/releases/tag/v2.3)**

Download `app-debug.apk` from the release and install it (you may need to allow "install from unknown sources" for your browser/files app). The release APK is CI debug-signed — fine for sideloading and sharing, and it installs alongside nothing else under the package `com.example.runway`-lineage build.

Newer development builds (2.4+, with Groups, sub-agents and MCP OAuth) are published as the `Runway-debug-apk` artifact on every [Actions](https://github.com/Farhan009-gif/Runway/actions) run.

> Runway needs your own API key or your own server endpoint — it is a workspace, not a subscription. Free routes work great: a self-hosted open model costs ₹0.

## 🛠️ Build it yourself

**Android Studio**
1. Clone the repo and open the project in Android Studio.
2. **Build → Build APK(s)** — the APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions**
Push to `main`/`master` (or trigger the **Build APK** workflow manually in the Actions tab) and download the APK from the workflow's artifacts.

## 🧱 Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Google Tink (Android Keystore) · OkHttp (SSE streaming) · MCP over Streamable HTTP/SSE · Firebase Auth + Firestore (Groups only)

## 🗺️ Where it's going

The Home-first redesign has shipped. Next up: shared project files inside Groups, then AI participating in group chats — plus deeper offline/local-model support so more of the workspace works with the internet off.

## 📄 License

MIT — see [LICENSE](LICENSE).

---

Built by **Farhan** ([@farhan.now_](https://instagram.com/farhan.now_)) — a student developer in Bokaro, India. Runway is developed in the open; issues and ideas are welcome on GitHub.
