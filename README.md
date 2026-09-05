# 🎓 AI Teacher — Autonomous Multimodal Pedagogical SaaS Platform

An enterprise-grade AI teaching platform designed to provide a human-like, adaptive pedagogical experience — driven by real user profile data, document extraction (PDF / TXT / PPT / PPTX), RAG grounding, and generative AI models.

The system accepts any **topic** or **uploaded study document**, creates a structured lesson, narrates content with AI voice & 3D anime avatars (**Chopper** 🧪, **Red-Haired Shanks** 🏴‍☠️, **Lucky Roux** 🍗), asks interactive section check questions, detects student misconceptions, re-explains adaptively, generates final assessments, produces printable PDF learning reports with concept roadmaps, and saves student history in a persistent H2 database.

---

## ✨ Key Features & Master SaaS Architecture

### 🔑 1. Student Authentication & Account Management
- **Email & Password Auth:** Full registration (`POST /api/auth/register`) and sign-in (`POST /api/auth/login`) with accounts persisted in H2 Database.
- **In-App Profile & Avatar Settings:** Edit full name, reset/overwrite password, pick from preset avatars, or upload a custom image file (automatically compressed via canvas thumbnail scaling to compact Base64 Data URL).

### 🎭 2. 3 Anime 3D AI Teacher Personas
Select your preferred AI Teacher character in Step 1 of the Wizard:
- 🧪 **Chopper — The Kind & Clever Doctor:** Step-by-step, analytical & caring explanations (Blue Sakura Aura 🌸).
- 🏴‍☠️ **Red-Haired Shanks — The Inspiring Captain:** High-energy, bold & motivating real-world examples (Red Conqueror's Haki Aura ⚡).
- 🍗 **Lucky Roux — The Quick & Friendly Specialist:** Fast, fun, approachable & algorithmic breakdowns (Green Energy Aura ✨).

### 🧙‍♂️ 3. 2-Step Personalized Learning Wizard & Quick-Demo Chips
- **Step 1 (Teacher & Profile):** Persona Choice (Chopper / Shanks / Lucky Roux), Educational Level, Preferred Language (English / Hindi / Kannada), Teaching Style.
- **Step 2 (Learning Content & Goals):**
  - **⚡ 1-Click Quick-Demo Sample Chips:** Instant topic fills for `⚡ Newton's Laws`, `🧬 DNA Replication`, `💻 Python Recursion`, `📐 Pythagoras Theorem`.
  - Custom topic input OR document file upload.
  - Learning Objective, Available Study Time (10m, 20m, 30m, 45m), Desired Depth, and Prior Knowledge.

### 📄 4. Document Extraction & RAG Grounding
- **File Text Extraction:** Extracts readable content from PDF (PDFBox), PPT/PPTX (Apache POI), and plain text files.
- **In-Memory RAG Grounding:** Paragraph chunking, local hash embedding, vector similarity store, and query retrieval to ground AI prompts with relevant document excerpts.
- **🎯 98% RAG Confidence Badge:** Displays document grounding confidence when uploaded material is present.

### 🎬 5. Always-Animated 3D Stage Visual Playgrounds
- **💻 Code Execution Sandbox (`code`):** Interactive code box with `▶ Run Code` console, line tracer, terminal output, and copy code button.
- **⚡ Physics Vector Simulator (`process`):** 3D force vector arrows with clickable toggles (`Gravity 🌐`, `Friction ⚙️`, `Velocity 🚀`).
- **📐 Math Step Explorer (`equation`):** Step-by-step calculation buttons (`[Step 1]`, `[Step 2]`, `[Step 3]`).
- **🧬 Process Cycle (`diagram`):** Interactive stage tabs (`1. Intake ➔ 2. Process ➔ 3. Release`).
- **🗺️ Timeline Roadmap (`timeline`):** 3D interactive milestone nodes.

### 🎙️ 6. Live Microphone Audio + Video Stream Recording
- **MediaRecorder + Web Audio API:** Captures 30 FPS composite canvas rendering of avatar, subtitles, progress bar, and visual stage **merged with live microphone audio narration** into downloadable `.webm`/`.mp4` video files.

### 📝 7. Assessment, Printable PDF Report & Power Meter
- **Final Quiz & Grading:** 5-question comprehensive assessment.
- **⚡ Student Haki Mastery Power Meter (0-100%):** Visual rank meter (`Novice ➔ Adept ➔ Master ➔ Supreme Haki Master`).
- **📄 Download PDF Report:** `📄 Download PDF Report` trigger (`window.print()`) for physical/PDF export.
- **🗺️ Concept Learning Path Roadmap:** Structured step-by-step topic progression map.

### 📊 8. Personal History & Progress Dashboard
- **Session Tracking:** Tracks completed sessions, scores, weak areas, and question logs per student in file-backed H2 database (`jdbc:h2:file:./data/aiteacherdb`).

### 🎨 9. UI/UX Polish, Session Recovery & Theme Toggle
- **↺ Auto-Save Session Recovery:** Saves current lesson state to `localStorage` with a `Resume Previous Session` banner.
- **🌙 / ☀️ Light & Dark Theme Toggle:** Instant client-side theme switching.
- **🔔 Floating Toast Notifications:** Real-time feedback alerts.
- **🏹 Arrow Mark Navigation:** Clear `← Back` and `Next →` navigation across all screens.

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

## 🧪 Testing

Run the full automated test suite (185 unit & integration tests):

```bash
cmd /c mvnw.cmd test
```

Build production JAR package:

```bash
cmd /c mvnw.cmd clean package -DskipTests
```
