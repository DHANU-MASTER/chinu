# 🎓 AI Teacher — Personalized Multimodal AI Learning Platform

An AI teaching platform designed to provide a human-like, adaptive pedagogical experience — driven by real user profile data, document extraction (PDF / TXT / PPT / PPTX), and generative AI models.

The system accepts any **topic** or **uploaded study document**, creates a structured lesson, narrates content with AI voice & animated avatars, asks interactive section questions, detects student misconceptions, re-explains adaptively, generates final assessments, produces printable learning reports with concept roadmaps, and saves student history in a persistent database.

---

## ✨ Key Features & MVP Architecture

### 🔑 1. Student Authentication & Profile Management
- **Email & Password Auth:** Full registration (`POST /api/auth/register`) and sign-in (`POST /api/auth/login`) with accounts persisted in H2 Database.
- **In-App Profile & Avatar Settings:** Edit full name, reset/overwrite password, pick from preset avatars (`🧑‍🎓`, `👨‍🎓`, `👩‍🎓`, `⚡`, `🚀`, `🧠`), or upload a custom image file (automatically compressed via canvas thumbnail scaling to Data URL).

### 🧙‍♂️ 2. 2-Step Personalized Learning Wizard
- **Step 1 (Student Profile):** Educational Level, Preferred Language (English / Hindi / Kannada), Teaching Style (Simple Explanation, Visual, Example-Based, Step-by-Step).
- **Step 2 (Learning Content & Goals):**
  - **⚡ 1-Click Quick-Demo Sample Chips:** Instant topic fills for `⚡ Newton's Laws`, `🧬 DNA Replication`, `💻 Python Recursion`, `📐 Pythagoras Theorem`.
  - Custom topic input OR document file upload.
  - Learning Objective, Available Study Time (10m, 20m, 30m, 45m), Desired Depth, and Prior Knowledge.

### 📄 3. Document Extraction & RAG Pipeline
- **File Text Extraction:** Extracts readable content from PDF (PDFBox), PPT/PPTX (Apache POI), and plain text files.
- **In-Memory RAG Grounding:** Paragraph chunking, local hash embedding, vector similarity store, and query retrieval to ground AI prompts with relevant document excerpts.

### 🧑‍🏫 4. AI Teacher Stage & Voice Narration
- **Live Avatar Visual Status:** Live badge indicating AI state (`Listening`, `Thinking...`, `Teaching`, `Evaluating`).
- **Web Speech Synthesis (TTS):** Browser-native speech synthesis mapped to `en-IN`, `hi-IN`, and `kn-IN` with rate controls (0.8x, 1.0x, 1.2x) and word boundary subtitle synchronization.
- **Subject-Specific Visual Cards:** Decorative animated motifs for Math (`equation`), Processes (`process`), Code (`code`), Timelines (`timeline`), and Diagrams (`diagram`).

### 🧠 5. Interactive Section Q&A & Adaptive Re-Teaching
- **Dynamic Questions:** MCQ and short-answer questions generated per section.
- **Misconception Detection:** Semantic analysis categorizing understanding levels (`UNDERSTOOD`, `PARTIALLY_UNDERSTOOD`, `MISCONCEPTION`, `NOT_UNDERSTOOD`).
- **Adaptive Re-Teaching:** Generates simplified explanations, alternative examples, and follow-up checks (up to 2 adaptation cycles per section).

### 📝 6. Assessment, Printable Report & Concept Roadmap
- **Final Quiz & Grading:** 5-question comprehensive assessment.
- **Interactive Score Circle:** Dynamic score badge (% correct) with strength/weakness analysis and recommended revision.
- **🖨️ Printable Learning Report:** `Print / Save Report` trigger (`window.print()`) for physical/PDF export.
- **🗺️ Concept Learning Path Roadmap:** Structured step-by-step topic progression map.

### 🎥 7. Lesson Video Recording & Download
- **MediaRecorder API:** Captures 30 FPS composite canvas rendering of avatar, subtitles, progress bar, and visual stage into downloadable `.webm`/`.mp4` video files.

### 📊 8. Personal History & Progress Dashboard
- **Session Tracking:** Tracks completed sessions, scores, weak areas, and question logs per student in file-backed H2 database (`jdbc:h2:file:./data/aiteacherdb`).

### 🎨 9. UI/UX Polish & Arrow Navigation
- **🌙 / ☀️ Light & Dark Theme Toggle:** Instant client-side theme switching.
- **🔔 Floating Toast Notifications:** Real-time feedback alerts.
- **🏹 Arrow Mark Navigation:** Clear `← Back` and `Next →` navigation across all screens.

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+**
- **Maven** (wrapper included)

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
