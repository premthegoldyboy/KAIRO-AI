# KAIRO — Sovereign Intelligence for Android

KAIRO is a modular Android personal-assistant project. The current repository contains an evolving v0.1 development build with local/cloud orchestration and permission-scoped skills. It is not yet a fully autonomous or always-listening assistant.

## Implemented foundation

- Dark Compose chat interface, Android speech recognition initiated by the user, and Android text-to-speech.
- Provider-independent AIEngine interface.
- Configurable DeepSeek/xAI-compatible Chat Completions and Anthropic Messages adapters.
- Sequential cloud-provider failover (availability and quotas remain provider-dependent).
- Adaptive local/cloud routing through KairoBrain.
- Optional LiteRT-LM local model integration using a separately downloaded Qwen3 0.6B INT4 model.
- Encrypted Android-Keystore-backed provider credentials and local memories.
- Persistent encrypted recent conversation history, with a clear-history control.
- Recent chat context and relevant saved memories supplied to the model.
- Task planning prompts for build, debug, plan, compare, and explain requests.
- Specialist-agent registry for software development, research, planning, analysis, tutoring, translation, requirements, and general assistance. These profiles use the selected AI engine; they are not separate hosted models.
- Deterministic calculator and device date/time tools.
- App launching only after the user confirms the requested action.
- Telugu-English command recognition for selected memory skills.
- User-selected TXT, Markdown, CSV, JSON, XML, and source-code text import, bounded to 96 KB and shown in the composer for review before sending.

## Important limitations

- Optional live web search is available through a user-configured Tavily key. It is disabled in LOCAL mode; cloud mode can retrieve dated source URLs for synthesis. Search quotas and availability depend on Tavily.
- Cloud responses support streaming; local inference currently returns its completed response as one chunk.
- Specialist roles are prompt-routed through the selected model; independent autonomous agents and cross-model collaboration are not implemented.
- PDF extraction and arbitrary binary-document parsing are not implemented; the document picker accepts bounded plain-text formats only.
- The local model's runtime performance and compatibility must be verified on a physical device.
- Wake-word / always-listening detection is not implemented.
- Memory auto-learning is deliberately narrow and should not be treated as a complete semantic memory system.
- Cloud API keys, usage limits, and possible charges are controlled by each provider. Failover does not provide unlimited or free inference.
- Recent conversation context and user prompts are sent to the selected cloud provider when cloud inference is used. Local chat history is encrypted on-device; users can clear it from the app.

## Build

The GitHub Actions workflow runs on pushes to main and can also be started manually:

1. Open the repository's Actions tab.
2. Select Build KAIRO Android APK.
3. Open the latest successful run.
4. Download the KAIRO-debug-apk artifact.

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.0 and Android API 36.

## Project structure

- app/ — Android application
- AIEngine.kt — provider adapters and failover
- KairoBrain.kt — adaptive local/cloud routing
- KairoTaskPlanner.kt — task classification and planning prompts
- KairoAgentRegistry.kt — specialist role definitions and task routing
- DocumentTextReader.kt — bounded user-selected text import
- KairoSkillRegistry.kt / KairoToolExecutor.kt — supported deterministic skills
- MemoryStore.kt / ConversationStore.kt — encrypted local memory and chat history
- .github/workflows/android.yml — automated debug APK build

## Privacy and permissions

Microphone access is requested only when the user taps the voice-input button. Provider credentials and local memory/history use Android Keystore-backed AES-GCM encryption. Never commit API keys. Review the cloud provider's data policies before configuring a key.

## Roadmap

1. Secure, reliable provider layer with streaming and better failure handling.
2. Live web research with dated sources and transparent citations.
3. More robust multilingual understanding and user-controlled memory.
4. Expanded permissioned Android skills with explicit confirmation for consequential actions.

KAIRO is intended to be free/open-source-first. Third-party services, model hosting, and device capabilities may have their own limits or costs.
