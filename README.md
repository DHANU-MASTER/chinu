# 🎓 AI Teacher — Autonomous Multimodal Pedagogical SaaS Platform

[![CI](https://img.shields.io/badge/CI-GitHub_Actions-2088FF?logo=githubactions&logoColor=white)](https://github.com/your-username/ai-teacher/actions/workflows/ci.yml)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![Tests](https://img.shields.io/badge/Tests-210_passing-success)](#-testing)
[![E2E](https://img.shields.io/badge/E2E-Playwright-2EAD33?logo=playwright&logoColor=white)](#-testing)
[![PWA](https://img.shields.io/badge/PWA-Installable-5A0FC8?logo=pwa&logoColor=white)](#-platform-capabilities-latest)
[![License](https://img.shields.io/badge/License-MIT-9cf)](LICENSE)
[![AI RAG Grounded](https://img.shields.io/badge/AI_RAG-Enabled-blue)](#-5-document-extraction--rag-grounding)

An enterprise-grade AI teaching platform designed to provide a human-like, adaptive pedagogical experience — driven by real user profile data, document extraction (PDF / TXT / PPT / PPTX), RAG grounding, and generative AI models.

The system accepts any **topic** or **uploaded study document**, creates a structured lesson, narrates content with AI voice & 3D anime avatars (**Chopper** 🧪, **Red-Haired Shanks** 🏴‍☠️, **Lucky Roux** 🍗), asks interactive section check questions, detects student misconceptions, re-explains adaptively, generates final assessments, produces printable **Certificates of AI Mastery** & PDF learning reports, manages daily study schedules via an **AI Timetable Planner**, tracks **daily study streaks & achievement badges**, and saves student history in a persistent H2 database.

---

## ✨ Key Features & Master SaaS Architecture

### 🔑 1. Student Authentication & Account Management
- **Email & Password Auth:** Full registration (`POST /api/auth/register`) and sign-in (`POST /api/auth/login`) with accounts persisted in H2 Database.
- **In-App Profile & Avatar Settings:** Edit full name, reset/overwrite password, pick from preset avatars, or upload a custom image file (automatically compressed via canvas thumbnail scaling to compact Base64 Data URL).

### 🎯 2. Lesson Milestone Progress Checklist Widget
- **Live Section Progression Bar:** Section milestone checklist box on the Teaching Stage tracking lesson progression (`[x] Intro`, `[/] Section 1`, `[ ] Section 2`, `[ ] Final Assessment`).

### 📜 3. Printable Certificate of AI Mastery & PDF Reports
- **📜 Formal Certificate of Completion:** Generated upon completing assessments with student name, avatar, topic, mastery score %, date, and official AI Teacher signature. Includes a 1-click **`🖨️ Print / Save Certificate PDF`** trigger!

### 🔥 4. Daily Study Streak Tracker & Achievement Badges
- **Streak Counter:** Topbar badge tracking daily learning momentum (`🔥 1-Day Streak`, `🔥 3-Day Streak`).
- **Unlocked Achievement Badges:** `First Steps 🏅`, `Quiz Ace 🎯`, and `Haki Awakened ⚡` badges on the Progress Dashboard.

### 🔊 5. Web Audio API Sound Effects Manager (`🔊 / 🔇`)
- Zero-dependency Web Audio API oscillator chime synthesizer playing audio cues on correct answers (`✨ Victory Chime`), persona selection (`🌸 Persona Shift`), and certificate unlocks.

### 📅 6. AI Study Timetable & Schedule Planner
- **Interactive Weekly Schedule Grid (Mon – Sun):** Visual daily time blocks (*Morning*, *Afternoon*, *Evening*).
- **🗑️ Slot Deletion:** 1-click delete icon button on every scheduled slot.
- **🤖 1-Click "Generate AI Revision Schedule":** Evaluates weak areas from past reports and creates an optimized weekly revision schedule.
- **🚀 1-Click "Start Session":** Launches the AI Teacher stage with pre-filled topic and study time.
- **🏆 Dynamic Leaderboard Card:** Displays top student ranks (`Supreme Haki Master`, `Master`, `Adept`).

### 🎭 7. 3 Anime 3D AI Teacher Personas
- 🧪 **Chopper — The Kind & Clever Doctor:** Step-by-step, analytical & caring explanations (Blue Sakura Aura 🌸).
- 🏴‍☠️ **Red-Haired Shanks — The Inspiring Captain:** High-energy, bold & motivating real-world examples (Red Conqueror's Haki Aura ⚡).
- 🍗 **Lucky Roux — The Quick & Friendly Specialist:** Fast, fun, approachable & algorithmic breakdowns (Green Energy Aura ✨).

### 🧙‍♂️ 8. 2-Step Personalized Learning Wizard & Quick-Demo Chips
- **Step 1 (Teacher & Profile):** Persona Choice (Chopper / Shanks / Lucky Roux), Educational Level, Preferred Language (English / Hindi / Kannada), Teaching Style.
- **Step 2 (Learning Content & Goals):**
  - **⚡ 1-Click Quick-Demo Sample Chips:** Instant topic fills for `⚡ Newton's Laws`, `🧬 DNA Replication`, `💻 Python Recursion`, `📐 Pythagoras Theorem`.
  - Custom topic input OR document file upload.

### 📄 9. Document Extraction & RAG Grounding
- **File Text Extraction:** Extracts readable content from PDF (PDFBox), PPT/PPTX (Apache POI), and plain text files.
- **In-Memory RAG Grounding:** Paragraph chunking, local hash embedding, vector similarity store, and query retrieval to ground AI prompts.
- **🎯 98% RAG Confidence Badge:** Displays document grounding confidence when uploaded material is present.

### 🎬 10. Smart 3D Animated Visual Stage Generator for ANY Topic
- 🧬 **Biology / DNA / Genetics:** Rotating 3D DNA Double Helix Animation with base pairs (`A-T`, `C-G`).
- ⚡ **Physics / Forces / Motion / Newton:** Moving 3D Force Vector & Trajectory Simulator (`Gravity`, `Velocity`, `Friction`).
- 📐 **Math / Geometry / Algebra:** Rotating 3D Geometric Shape & Calculation Engine.
- 💻 **Code / Python / Java / Recursion:** Animated 3D Call Stack & Terminal Execution Sandbox.
- 🗺️ **History / Milestones / Eras:** Animated Glowing Timeline Roadmap.
- ⚛️ **Chemistry / Space / Universal Fallback:** Animated 3D Atomic Orbit & Concept Nucleus Network!

### 🎙️ 11. Live Microphone Audio + Video Stream Recording
- **MediaRecorder + Web Audio API:** Captures 30 FPS composite canvas rendering of avatar, subtitles, progress bar, and visual stage **merged with live microphone audio narration** into downloadable `.webm`/`.mp4` video files.

### 📝 12. Assessment & Student Mastery Power Meter
- **Final Quiz & Grading:** 5-question comprehensive assessment.
- **⚡ Student Haki Mastery Power Meter (0-100%):** Visual rank meter (`Novice ➔ Adept ➔ Master ➔ Supreme Haki Master`).

### 📊 13. Personal History & Progress Dashboard (with Live Search & CSV Export)
- **🔍 Live Search Filter Bar:** Filter history records by topic, date, or score in real-time as you type.
- **📥 CSV Data Export:** 1-click download of student learning session history as a structured `.csv` file.
- **Persistent Database:** Backed by file-backed H2 database (`jdbc:h2:file:./data/aiteacherdb`).

---

## 📄 Resume-Ready Project Bullet Points (For Your CV)

```markdown
AI Teacher — Autonomous Multimodal Pedagogical SaaS Platform
Tech Stack: Java 17, Spring Boot 3.5, Spring Data JPA, H2 Database, REST APIs, RAG (Retrieval-Augmented Generation), 
            Apache Tika/PDFBox, Web Speech Synthesis API, Web Audio API, MediaRecorder API, HTML5/CSS3/JS

• Architected Full-Stack AI Learning SaaS: Engineered a Spring Boot & REST API backend integrated with OpenAI/Gemini endpoints, supporting autonomous lesson planning, interactive evaluation, misconception detection, and adaptive re-teaching.
• Implemented Custom Offline RAG Pipeline: Built document text extraction (PDF/PPT/TXT) via Apache Tika/PDFBox and an in-memory Vector Store using local embeddings and similarity retrieval for grounded, hallucination-free lesson generation.
• Real-Time Multimodal UI/UX & Web Speech Synthesis: Created a responsive single-page web app featuring 3D anime teacher personas, Web Speech API text-to-speech in 3 languages (English, Hindi, Kannada) with word-level subtitle synchronization.
• Synchronized Audio/Video Stream Capture: Engineered live browser recording using MediaRecorder API and Web Audio API (AudioContext) stream mixing, rendering 30 FPS downloadable WebM/MP4 lesson recordings with live microphone audio.
• Persistent Analytics & Session Engine: Built a Spring Data JPA persistence layer backed by H2 database storing student progress, session metrics, misconception logs, and concept roadmap analytics.
```

---

## 🚀 Getting Started

### Running the Application

In PowerShell:
```powershell
# Set your AI API Key (Gemini or OpenAI compatible)
$env:AI_API_KEY="your_api_key_here"
$env:AI_BASE_URL="https://generativelanguage.googleapis.com/v1beta/openai/"
$env:AI_MODEL="gemini-2.5-flash"

cmd /c mvnw.cmd spring-boot:run
```

In Command Prompt (`cmd.exe`):
```cmd
set AI_API_KEY=your_api_key_here
cmd /c mvnw.cmd spring-boot:run
```

Open browser at: **[http://localhost:8080](http://localhost:8080)**

---

## ⚙️ Environment Configuration

| Variable | Default | Description |
|---|---|---|
| `AI_API_KEY` | *(none)* | Secret key for AI provider (Server-side only). |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible completions endpoint. |
| `AI_MODEL` | `gpt-4o-mini` | AI Model ID (e.g., `gemini-2.5-flash`, `gpt-4o-mini`). |
| `AI_TIMEOUT_SECONDS` | `90` | Request timeout in seconds. |

---

## 🔌 API Overview

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new student account |
| POST | `/api/auth/login` | Sign in student with Email + Password |
| POST | `/api/auth/profile/update` | Update name, avatar profile pic, or reset password |
| POST | `/api/profile` | Validate student profile |
| POST | `/api/material/upload` | Multipart upload & text extraction (PDF/TXT/PPT/PPTX) |
| POST | `/api/lesson/plan` | Generate personalized lesson plan |
| POST | `/api/lesson/question` | Generate interactive section check question |
| POST | `/api/lesson/evaluate` | Evaluate student answer |
| POST | `/api/lesson/misconception` | Detect misconception & understanding level |
| POST | `/api/lesson/adapt` | Generate adaptive re-teaching & follow-up check |
| POST | `/api/assessment/generate` | Generate final assessment |
| POST | `/api/assessment/submit` | Grade assessment & generate learning report |
| POST | `/api/progress` | Save completed learning session |
| GET | `/api/progress/summary?studentName=…` | Fetch student history & progress metrics |

---

## 🚀 Platform Capabilities (latest)

- **🎬 Guest Demo Mode** — `Try a Demo Lesson` on the login screen (or `index.html?demo=1`) explores the full app without an account.
- **🎨 Landing Page** — `landing.html` introduces the product and deep-links into demo mode.
- **🎤 Voice Answers** — speak answers to check questions (Web Speech Recognition, English/Hindi/Kannada) via the mic button on any answer box.
- **💬 Ask the Teacher (live chat)** — bidirectional WebSocket chat at `/ws/ask`: streaming deltas, per-lesson conversation history with reconnect replay and AI context, chat-clear, plus automatic HTTP fallback (`/api/lesson/ask`) when sockets are blocked.
- **🲸 SSE Streaming** — lessons *and* check questions generate token-by-token (`/api/lesson/plan/stream`, `/api/lesson/question/stream`): the AI writes live on screen while the response is still in flight.
- **🌐 UI i18n** — the app chrome translates to Hindi/Kannada via `js/i18n.js` (follows the Preferred Language selector).
- **📱 Installable PWA** — `manifest.webmanifest` + `sw.js` (offline app shell; API calls are always live).
- **🔐 BCrypt Passwords** — hashes upgraded from the legacy scheme with transparent migration on login; minimum 8 characters.
- **📊 Live API Docs** — Swagger UI at `/swagger-ui.html` (springdoc-openapi).
- **🐳 Docker Deployment** — `docker compose up --build` runs the app + PostgreSQL (`SPRING_PROFILES_ACTIVE=postgres`), with `Dockerfile` for custom builds.
- **🤖 CI** — GitHub Actions runs the full test suite and JAR build on every push/PR (`.github/workflows/ci.yml`).

## 🧪 Testing

Run the full automated test suite (210 unit & integration tests):

```bash
cmd /c mvnw.cmd test
```

### Browser E2E tests (Playwright)

The `e2e/` specs cover the AI-free journey — landing page, demo mode, auth UI,
i18n and PWA assets — against the real Spring Boot app:

```bash
npm install
npx playwright install chromium
cmd /c npm run e2e
```

`playwright.config.js` boots the app itself (`mvnw spring-boot:run`, reusing an
already-running instance). Full lesson E2E additionally requires `AI_API_KEY`.

<!-- 📺 TODO: add a demo GIF here: https://github.com/your-username/ai-teacher/raw/main/docs/demo.gif -->

Build production JAR package:

```bash
cmd /c mvnw.cmd clean package -DskipTests
```
