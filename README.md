# 🎓 AI Teacher — Autonomous Multimodal Pedagogical Engine

An AI teaching platform that is **driven entirely by real user input** — no demo content, no hardcoded lessons, no fake scores.

The student enters a profile (name, education level, language, teaching style, objective, prior knowledge, available study time, desired depth), picks **any topic** or uploads learning material (PDF / TXT / PPT / PPTX), and the AI Teacher:

**Plans → Explains → Demonstrates → Asks → Evaluates → Detects misconceptions → Adapts → Re-teaches → Assesses → Reports → Tracks progress**

and the whole presentation can be **recorded and downloaded** in the browser.

---

## ✨ Feature Status

| Feature | Status | Notes |
|---|---|---|
| Student profile (name, level, language, style, objective) | ✅ | Every value reaches the AI prompt |
| Personalization (prior knowledge, study time, desired depth) | ✅ | All three optional fields reach the lesson/question/assessment prompts and change the generated lesson |
| Any-topic lesson generation | ✅ | Arbitrary user topics, no topic-specific logic |
| Uploaded-material learning (PDF/TXT/PPT/PPTX) | ✅ | Real text extraction via Apache Tika/PDFBox/POI; material is the primary AI source |
| RAG / material grounding | ✅ | Chunk → embed (local, offline) → vector store → similarity retrieval; the most relevant excerpts ground the lesson prompt instead of blind truncation |
| AI lesson planning (title, intro, objectives, sections, examples) | ✅ | Structured, validated JSON from the AI provider |
| Personalization (level, style, objective, language, time estimate) | ✅ | Applied in the generation prompt |
| Teacher presentation (avatar, sections, progress, navigation) | ✅ | Dynamic content, responsive layout |
| Avatar states (IDLE / SPEAKING / PAUSED / TRANSITIONING) | ✅ | CSS/Canvas animation tied to real state |
| Subject-aware visuals | ⚠️ | Decorative animated motifs keyed to each section's `visualHint` (equation, process, timeline, code, diagram); they do **not** invent facts |
| Voice narration (English / Hindi / Kannada) | ✅ | Web Speech API, locale mapping `en-IN` / `hi-IN` / `kn-IN`, play/pause/resume/stop/speed, subtitles |
| Multilingual teaching content | ✅ | Lesson, questions, feedback, adaptation and report are generated in the selected language |
| Interactive questions (MCQ + short answer) | ✅ | Generated from the actual lesson section |
| Answer evaluation | ✅ | MCQ exact-match; short answers semantically evaluated by AI |
| Misconception detection | ✅ | UNDERSTOOD / PARTIAL / MISCONCEPTION / NOT_UNDERSTOOD with severity and recommended approach |
| Adaptive re-teaching | ✅ | Different explanation + example + follow-up question, attempt limit prevents infinite retries |
| Final assessment | ✅ | Dynamically generated, scored, weak areas + revision plan + next topic |
| Learning report | ✅ | Score %, concepts understood, weak areas, incorrect concepts, revision, next topic |
| Progress dashboard / history | ✅ | Real sessions only; proper empty state, no fake records |
| **Video recording + download** | ✅ | Browser-native MediaRecorder + composite canvas of the real lesson; preview, download (WebM/MP4 by browser support), dynamic filename |
| Error handling | ✅ | User-safe messages; no silent failures; proper HTTP status codes |
| Security | ✅ | API key stays server-side; upload validation + size limit + executable-content sniffing; no stack traces leaked |

---

## 🚀 Run

```bash
mvnw spring-boot:run
```

Open **http://localhost:8080** — a student profile form is the starting point.

> ⚠️ The app needs a real AI provider to teach. Without one, the UI shows a clear
> "AI service is currently unavailable" style message instead of fabricating content.

### AI configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `AI_API_KEY` | *(none)* | Bearer token for the AI provider. **Never exposed to the browser.** |
| `AI_BASE_URL` | `https://api.openai.com/v1` | Any OpenAI-compatible chat-completions base URL (OpenAI, Groq, OpenRouter, DeepSeek, Ollama, …) |
| `AI_MODEL` | `gpt-4o-mini` | Model identifier |
| `AI_TIMEOUT_SECONDS` | `90` | Provider read timeout |

Example:

```bash
export AI_API_KEY=sk-...    # macOS/Linux
set AI_API_KEY=sk-...       # Windows cmd
```

---

## 🗂 Project Structure

```
src/main/java/com/aiteacher/
├── ai/            AiChatClient transport (OpenAI-compatible), typed AiException
├── controller/    REST endpoints (profile, lesson plan, interaction, assessment, upload, progress)
├── dto/           Request/response DTOs
├── entity/        JPA entity (LearningSession)
├── extraction/    PDF / TXT / PPT / PPTX text extraction
├── rag/           Chunking, local hash embeddings, in-memory vector store, retrieval service
├── repository/    Spring Data JPA repository
└── service/       AI lesson, question, evaluation, misconception, adaptation, assessment, progress

src/main/resources/
├── static/        index.html + css/ + js/ (speech, recording, teaching, assessment, progress)
└── application.yml
```

The frontend is a single-page app (vanilla HTML/CSS/JS) with screens for profile → teaching → assessment → report → dashboard.

---

## 🔌 API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/profile` | Validate the student profile |
| POST | `/api/material/upload` | Multipart upload: extract text from PDF/TXT/PPT/PPTX |
| POST | `/api/lesson/plan` | Generate the personalized lesson plan |
| POST | `/api/lesson/question` | Generate a question from the current section |
| POST | `/api/lesson/evaluate` | Evaluate the student's answer |
| POST | `/api/lesson/misconception` | Detect misconception / understanding level |
| POST | `/api/lesson/adapt` | Generate adaptive re-teaching + follow-up question |
| POST | `/api/assessment/generate` | Generate the final assessment from the lesson |
| POST | `/api/assessment/submit` | Grade answers and build the learning report |
| POST | `/api/progress` | Save a completed learning session |
| GET | `/api/progress?studentName=…` | Learning history |
| GET | `/api/progress/summary?studentName=…` | Progress summary (strengths, weak areas, next topic) |

H2 console (dev): http://localhost:8080/h2-console — JDBC URL `jdbc:h2:file:./data/aiteacherdb`, user `sa`, empty password. Progress is stored in a file-backed H2 database (`./data/`) so it **survives application restarts**. Only one app instance may run at a time (stop the previous one first).

---

## 🧪 Tests

```bash
mvnw clean test
mvnw clean package
```

The suite (185 tests) covers controllers, AI prompt construction/validation for every AI service, the RAG pipeline (chunking, embeddings, retrieval ranking), document extraction (real PDF/PPT/PPTX/TXT bytes, corrupt/oversized/executable rejections), and progress persistence — all with a fake transport, so no network or API key is needed. Integration tests run against an in-memory H2 so they never touch your real progress file.

---

## ⚠️ Honest Limitations

- **Recording is visual-only.** MediaRecorder cannot capture Web Speech synthesis audio, so the recorded video contains the teacher avatar, lesson visuals and subtitles — **not** narration audio. The app says so explicitly and never fakes audio. Video files are WebM in most browsers (MP4 when the browser supports it), named `ai-teacher_<topic>_<timestamp>.<ext>`.
- **AI teaching requires a configured provider.** With no `AI_API_KEY`, the app refuses politely rather than generating fake content.
- **Progress is stored in a file-backed H2 database** (`jdbc:h2:file:./data/aiteacherdb`) and survives restarts. Only one app instance may run at a time. Delete the `./data/` folder to reset progress.
- **Speech and recording depend on the browser/OS** (Web Speech voices, `MediaRecorder`/`captureStream` support). Unsupported browsers get clear messages and the rest of the app keeps working.
- **Teaching visuals are decorative motifs** keyed to the lesson's visual hint — a lightweight, honest visual treatment rather than invented subject facts.
- **RAG uses offline local embeddings** (deterministic hashing, no model/API). It is a hackathon-grade lexical/semantic-hybrid retriever — strong on topical overlap, not a learned model.