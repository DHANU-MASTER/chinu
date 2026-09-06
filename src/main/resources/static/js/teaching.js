/* ==========================================================================
   AI TEACHER — Phase 5+6+7 teaching screen
   Presents the ACTUAL LessonPlanResponse from POST /api/lesson/plan section
   by section: introduction → learning objectives → sections → question →
   feedback → misconception detection → adaptive re-teaching → finish.
   Nothing here is a hardcoded lesson — every piece of content shown comes
   from the plan object passed to render(). After each section, a question
   is generated, the answer is evaluated, and if the student struggles,
   the system detects misconceptions and adapts the teaching approach.
   ========================================================================== */
window.AiTeacherTeaching = (function () {
  'use strict';

  /* Static UI strings only (navigation labels may stay static per spec).
     Lesson content itself always comes from the plan. */
  var UI = {
    welcomeTitle: 'Welcome!',
    objectivesTitle: 'By the end of this lesson you will be able to:',
    teaching: 'Teaching…',
    speaking: 'Speaking…',
    paused: 'Paused',
    introLabel: 'Introduction',
    next: 'Next →',
    finish: 'Finish Lesson',
    complete: 'Lesson complete 🎉',
    completeBody: 'You finished this lesson.',
    timeLabel: 'Estimated lesson time:',
    minutes: 'min',
    exampleLabel: 'Example',
    sectionFallback: 'Section',
    noText: 'No explanation was provided for this section.',
    noExample: 'No example was provided.',
    loadError: 'Unable to load the lesson. Please generate the lesson again.',
    generatingQuestion: 'Generating question…',
    evaluating: 'Evaluating your answer…',
    analyzing: 'Analyzing your understanding…',
    adapting: 'Preparing a new explanation…',
    questionLoadError: 'Unable to generate a question. Please retry.',
    evaluationLoadError: 'Unable to evaluate your answer. Please try again.',
    misconceptionLoadError: 'Unable to analyze your answer right now. Please try again.',
    adaptLoadError: 'Unable to generate adaptive explanation. Please try again.',
    submitAnswer: 'Submit Answer',
    continueLesson: 'Continue Lesson',
    tryCheck: 'Try a Quick Check',
    selectOption: 'Please select an option.',
    typeAnswer: 'Please type your answer.',
    LetsLookAnotherWay: "Let's look at this another way.",
    LetsSimplify: "Let's simplify this.",
    clarification: "Here's some clarification:",
    maxAdaptationsReached: "You've tried a few times. Let's continue — you can review this concept later.",
    voiceNotSupported: 'Voice narration is not supported in this browser.',
    voiceUnavailable: 'Voice narration is not available for this language.',
    playVoice: 'Play',
    pauseVoice: 'Pause',
    resumeVoice: 'Resume',
    stopVoice: 'Stop'
  };

  var MAX_ADAPTATIONS = 2;
  var VALID_HINTS = ['equation', 'process', 'timeline', 'code', 'diagram'];

  var lesson = null;   // the actual LessonPlanResponse
  var profile = null;  // student profile data for question context
  var step = 0;        // 0 = intro, 1..N = sections, N+1 = finish
  var els = {};
  var transitionTimer = null;
  var currentQuestion = null;  // the generated question for current section
  var isEvaluating = false;    // prevent double submission
  var questionAnswered = false; // whether current section's question has been answered
  var adaptationCount = 0;     // how many times we've adapted for current section
  var currentAdaptation = null; // the current adaptive teaching response
  var lastEvaluation = null;   // the last evaluation response
  var lastMisconception = null; // the last misconception response
  var isAdaptiveMode = false;  // whether we're showing adaptive content

  /* Phase 9: Voice state */
  var speech = window.AiTeacherSpeech || null;
  var currentSpeechRate = 1.0;
  var currentSubtitleText = '';

  function $(id) {
    return document.getElementById(id);
  }  function cacheEls() {
    ['teachingScreen', 'ts-title', 'ts-meta', 'ts-progress-label', 'ts-progress-bar',
      'ts-stage', 'ts-avatar', 'ts-bubble', 'ts-visual', 'ts-motif', 'ts-visual-title',
      'ts-visual-note', 'ts-body', 'ts-text', 'ts-section-title', 'ts-section-example',
      'ts-prev', 'ts-next', 'ts-edit', 'ts-complete',
      'ts-question-area', 'ts-question-loading', 'ts-question-content',
      'ts-question-text', 'ts-mcq-options', 'ts-short-answer', 'ts-answer-input',
      'ts-question-error', 'ts-submit-answer',
      'ts-evaluation-loading', 'ts-feedback', 'ts-feedback-banner',
      'ts-feedback-text', 'ts-feedback-concept', 'ts-continue-lesson',
      'ts-adaptive-area', 'ts-adaptive-loading', 'ts-adaptive-content',
      'ts-adaptive-banner', 'ts-adaptive-explanation', 'ts-adaptive-example',
      'ts-adaptive-followup', 'ts-adaptive-followup-loading',
      'ts-adaptive-followup-content', 'ts-adaptive-followup-text',
      'ts-adaptive-mcq-options', 'ts-adaptive-short-answer', 'ts-adaptive-answer-input',
      'ts-adaptive-submit', 'ts-adaptive-evaluation-loading',
      'ts-adaptive-feedback', 'ts-adaptive-feedback-banner', 'ts-adaptive-feedback-text',
      'ts-adaptive-continue',
      'ts-adaptive-error',
      // Phase 9: Voice controls
      'ts-voice-controls', 'ts-voice-play', 'ts-voice-stop', 'ts-voice-rate',
      'ts-voice-status', 'ts-subtitle'].forEach(function (id) {
      els[id] = $(id);
    });
  }

  function totalSteps() {
    return 1 + lesson.sections.length + 1; // intro + sections + finish
  }

  function isFinish() {
    return step === lesson.sections.length + 1;
  }

  function isIntro() {
    return step === 0;
  }

  function isSection() {
    return step >= 1 && step <= lesson.sections.length;
  }

  /* ---------------- avatar states: idle | teaching | transition --------- */
  function avatarState(state) {
    if (transitionTimer) {
      clearTimeout(transitionTimer);
      transitionTimer = null;
    }
    els['ts-avatar'].setAttribute('data-state', state);

    var statusTextEl = $('avatarStatusText');
    if (statusTextEl) {
      if (state === 'teaching') statusTextEl.textContent = 'Teaching';
      else if (state === 'idle') statusTextEl.textContent = 'Listening';
      else if (state === 'transition') statusTextEl.textContent = 'Transitioning';
      else if (state === 'paused') statusTextEl.textContent = 'Paused';
      else if (state === 'evaluating') statusTextEl.textContent = 'Evaluating';
      else if (state === 'thinking') statusTextEl.textContent = 'Thinking…';
    }

    if (state === 'transition') {
      var figure = els['ts-avatar'].querySelector('.teacher-figure');
      figure.style.animation = 'none';
      void figure.offsetWidth;
      figure.style.animation = '';
      transitionTimer = setTimeout(function () {
        avatarState('teaching');
      }, 520);
    }
  }

  /* ---------------- visual motif (decorative only) --- */
  function motifFor(hint) {
    var h = String(hint || '').toLowerCase().trim();
    return VALID_HINTS.indexOf(h) >= 0 ? h : 'generic';
  }

  function setMotif(hint) {
    els['ts-motif'].className = 'visual-motif motif-' + motifFor(hint);
  }

  function clearBody() {
    els['ts-text'].textContent = '';
    var example = els['ts-section-example'];
    example.textContent = '';
    example.classList.add('hidden');
  }

  function addParagraph(parent, text, className) {
    var p = document.createElement('p');
    if (className) { p.className = className; }
    p.textContent = text;
    parent.appendChild(p);
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /* ======================== PHASE 9: Voice Controls ======================== */

  function stopSpeech() {
    if (speech) {
      speech.stop();
    }
    updateVoiceUI('idle');
    hideSubtitle();
  }

  function speakText(text, language) {
    if (!speech || !speech.isSupported()) return;
    if (!text || text.trim().length === 0) return;

    var spoken = speech.speak(text, language, {
      rate: currentSpeechRate,
      onEnd: function () {
        updateVoiceUI('idle');
        hideSubtitle();
      }
    });

    if (!spoken) {
      // Voice not available for this language
      showVoiceError(UI.voiceUnavailable);
    }
  }

  function togglePlayPause() {
    if (!speech || !speech.isSupported()) return;

    if (speech.isPaused()) {
      speech.resume();
    } else if (speech.isSpeaking()) {
      speech.pause();
    } else {
      // Play current section content
      var textToSpeak = getCurrentSectionText();
      if (textToSpeak) {
        speakText(textToSpeak, lesson.language || 'English');
      }
    }
  }

  function getCurrentSectionText() {
    if (!lesson) return '';

    if (isIntro()) {
      // Combine introduction and objectives
      var text = lesson.introduction || '';
      if (lesson.learningObjectives && lesson.learningObjectives.length > 0) {
        text += '. ' + UI.objectivesTitle + ' ';
        text += lesson.learningObjectives.join('. ');
      }
      return text;
    } else if (isSection()) {
      var section = lesson.sections[step - 1];
      if (!section) return '';
      var text = '';
      if (section.title) text += section.title + '. ';
      if (section.explanation) text += section.explanation;
      if (section.example) text += '. Example: ' + section.example;
      return text;
    }
    return '';
  }

  function updateVoiceUI(state) {
    var playPauseBtn = els['ts-voice-play'];
    var stopBtn = els['ts-voice-stop'];
    var statusEl = els['ts-voice-status'];

    if (!playPauseBtn) return;

    if (state === 'speaking') {
      playPauseBtn.innerHTML = '<span aria-hidden="true">⏸</span> ' + UI.pauseVoice;
      playPauseBtn.classList.remove('btn-voice-play');
      playPauseBtn.classList.add('btn-voice-pause');
      if (stopBtn) stopBtn.disabled = false;
      if (statusEl) statusEl.textContent = UI.speaking;
      avatarState('teaching');
    } else if (state === 'paused') {
      playPauseBtn.innerHTML = '<span aria-hidden="true">▶</span> ' + UI.resumeVoice;
      playPauseBtn.classList.remove('btn-voice-pause');
      playPauseBtn.classList.add('btn-voice-play');
      if (statusEl) statusEl.textContent = UI.paused;
      avatarState('paused');
    } else {
      // idle
      playPauseBtn.innerHTML = '<span aria-hidden="true">▶</span> ' + UI.playVoice;
      playPauseBtn.classList.remove('btn-voice-pause');
      playPauseBtn.classList.add('btn-voice-play');
      if (stopBtn) stopBtn.disabled = true;
      if (statusEl) statusEl.textContent = '';
      // Don't override avatar state if we're in transition or teaching
    }
  }

  function showSubtitle(text) {
    if (!els['ts-subtitle']) return;
    if (!text || text.trim().length === 0) {
      hideSubtitle();
      return;
    }
    currentSubtitleText = text;
    els['ts-subtitle'].textContent = text;
    els['ts-subtitle'].classList.remove('hidden');
  }

  function hideSubtitle() {
    if (els['ts-subtitle']) {
      els['ts-subtitle'].classList.add('hidden');
      els['ts-subtitle'].textContent = '';
    }
    currentSubtitleText = '';
  }

  function showVoiceError(message) {
    if (els['ts-voice-status']) {
      els['ts-voice-status'].textContent = message;
      els['ts-voice-status'].classList.add('voice-error');
      setTimeout(function () {
        els['ts-voice-status'].classList.remove('voice-error');
        els['ts-voice-status'].textContent = '';
      }, 3000);
    }
  }

  function setSpeechRate(rate) {
    currentSpeechRate = rate;
    if (speech) {
      speech.setRate(rate);
    }
    // Update rate selector UI
    if (els['ts-voice-rate']) {
      var options = els['ts-voice-rate'].querySelectorAll('.rate-option');
      options.forEach(function (opt) {
        opt.setAttribute('aria-pressed', opt.getAttribute('data-rate') === String(rate) ? 'true' : 'false');
      });
    }
  }

  function initVoiceControls() {
    if (!speech || !speech.isSupported()) {
      // Hide voice controls if not supported
      if (els['ts-voice-controls']) {
        els['ts-voice-controls'].classList.add('hidden');
      }
      return;
    }

    // Set up speech state callback
    speech.setOnStateChange(function (state) {
      updateVoiceUI(state);
    });

    // Set up boundary callback for subtitle sync
    speech.setOnBoundary(function (charIndex, charLength) {
      if (currentSubtitleText) {
        // Show a portion of the text around the current position
        var start = Math.max(0, charIndex - 50);
        var end = Math.min(currentSubtitleText.length, charIndex + charLength + 150);
        var displayText = currentSubtitleText.substring(start, end);
        if (start > 0) displayText = '…' + displayText;
        if (end < currentSubtitleText.length) displayText = displayText + '…';
        if (els['ts-subtitle']) {
          els['ts-subtitle'].textContent = displayText;
        }
      }
    });

    // Play/Pause button
    if (els['ts-voice-play']) {
      els['ts-voice-play'].addEventListener('click', function () {
        togglePlayPause();
      });
    }

    // Stop button
    if (els['ts-voice-stop']) {
      els['ts-voice-stop'].addEventListener('click', function () {
        stopSpeech();
      });
    }

    // Rate selector
    if (els['ts-voice-rate']) {
      var rates = speech.getAvailableRates();
      els['ts-voice-rate'].innerHTML = '';
      rates.forEach(function (rate) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'rate-option';
        btn.setAttribute('data-rate', rate.value);
        btn.setAttribute('aria-pressed', rate.value === 1.0 ? 'true' : 'false');
        btn.textContent = rate.label;
        btn.addEventListener('click', function () {
          setSpeechRate(rate.value);
        });
        els['ts-voice-rate'].appendChild(btn);
      });
    }

    // Check voice availability for current language
    updateVoiceAvailability();
  }

  function updateVoiceAvailability() {
    if (!speech || !speech.isSupported()) return;
    var lang = lesson ? (lesson.language || 'English') : 'English';
    var available = speech.isVoiceAvailable(lang);
    if (els['ts-voice-status']) {
      if (!available) {
        els['ts-voice-status'].textContent = UI.voiceUnavailable;
        els['ts-voice-status'].classList.add('voice-error');
      } else {
        els['ts-voice-status'].textContent = '';
        els['ts-voice-status'].classList.remove('voice-error');
      }
    }
  }

  /* ======================== PHASE 6: Question Area ======================== */

  function hideQuestionArea() {
    els['ts-question-area'].classList.add('hidden');
    els['ts-question-loading'].classList.add('hidden');
    els['ts-question-content'].classList.add('hidden');
    els['ts-evaluation-loading'].classList.add('hidden');
    els['ts-feedback'].classList.add('hidden');
    els['ts-question-error'].classList.add('hidden');
    els['ts-mcq-options'].classList.add('hidden');
    els['ts-short-answer'].classList.add('hidden');
  }

  function resetQuestionState() {
    currentQuestion = null;
    questionAnswered = false;
    isEvaluating = false;
    adaptationCount = 0;
    currentAdaptation = null;
    lastEvaluation = null;
    lastMisconception = null;
    isAdaptiveMode = false;
    if (els['ts-answer-input']) {
      els['ts-answer-input'].value = '';
    }
    if (els['ts-submit-answer']) {
      els['ts-submit-answer'].disabled = false;
      els['ts-submit-answer'].textContent = UI.submitAnswer;
    }
  }

  /* ======================== PHASE 7: Adaptive Area ======================== */

  function hideAdaptiveArea() {
    els['ts-adaptive-area'].classList.add('hidden');
    els['ts-adaptive-loading'].classList.add('hidden');
    els['ts-adaptive-content'].classList.add('hidden');
    els['ts-adaptive-followup-loading'].classList.add('hidden');
    els['ts-adaptive-followup-content'].classList.add('hidden');
    els['ts-adaptive-evaluation-loading'].classList.add('hidden');
    els['ts-adaptive-feedback'].classList.add('hidden');
    els['ts-adaptive-error'].classList.add('hidden');
    els['ts-adaptive-mcq-options'].classList.add('hidden');
    els['ts-adaptive-short-answer'].classList.add('hidden');
  }

  function resetAdaptiveState() {
    currentAdaptation = null;
    if (els['ts-adaptive-answer-input']) {
      els['ts-adaptive-answer-input'].value = '';
    }
    if (els['ts-adaptive-submit']) {
      els['ts-adaptive-submit'].disabled = false;
      els['ts-adaptive-submit'].textContent = UI.submitAnswer;
    }
  }

  /* ======================== Generate Question ======================== */

  async function generateQuestion(sectionIndex) {
    var section = lesson.sections[sectionIndex];
    if (!section) return;

    hideQuestionArea();
    hideAdaptiveArea();
    resetQuestionState();
    els['ts-question-area'].classList.remove('hidden');
    els['ts-question-loading'].classList.remove('hidden');

    var requestBody = {
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College',
      teachingStyle: lesson.teachingStyle || 'Simple Explanation',
      objective: lesson.objective || '',
      priorKnowledge: lesson.priorKnowledge || '',
      desiredDepth: lesson.desiredDepth || '',
      sectionTitle: section.title || '',
      sectionContent: section.explanation || section.description || '',
      sectionExample: section.example || ''
    };

    try {
      var res = await fetch('/api/lesson/question', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.questionLoadError);
      }

      currentQuestion = await res.json();
      displayQuestion(currentQuestion);
    } catch (err) {
      els['ts-question-loading'].classList.add('hidden');
      els['ts-question-error'].textContent = err.message || UI.questionLoadError;
      els['ts-question-error'].classList.remove('hidden');
      showContinueButton();
    }
  }

  /* ======================== Display Question ======================== */

  function displayQuestion(q) {
    els['ts-question-loading'].classList.add('hidden');
    els['ts-question-content'].classList.remove('hidden');
    els['ts-question-text'].textContent = q.question;

    if (q.type === 'MCQ' && q.options && q.options.length > 0) {
      renderMcqOptions(els['ts-mcq-options'], q.options, false);
      els['ts-mcq-options'].classList.remove('hidden');
      els['ts-short-answer'].classList.add('hidden');
    } else {
      els['ts-mcq-options'].classList.add('hidden');
      els['ts-short-answer'].classList.remove('hidden');
      els['ts-answer-input'].focus();
    }

    els['ts-bubble'].textContent = 'Check Your Understanding';
    avatarState('teaching');
  }

  function renderMcqOptions(container, options, isAdaptive) {
    container.innerHTML = '';
    var keys = ['A', 'B', 'C', 'D'];
    options.forEach(function (option, index) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'option';
      btn.setAttribute('aria-pressed', 'false');
      btn.innerHTML = '<span class="key">' + keys[index] + '</span><span>' + escapeHtml(option) + '</span>';
      btn.addEventListener('click', function () {
        container.querySelectorAll('.option').forEach(function (o) {
          o.setAttribute('aria-pressed', 'false');
        });
        btn.setAttribute('aria-pressed', 'true');
      });
      container.appendChild(btn);
    });
  }

  function getSelectedMcqAnswer(container) {
    var selected = container.querySelector('.option[aria-pressed="true"]');
    if (!selected) return null;
    return selected.querySelector('span:last-child').textContent;
  }

  /* ======================== Submit Answer (Phase 6) ======================== */

  async function submitAnswer() {
    if (isEvaluating || !currentQuestion) return;

    var studentAnswer = '';
    if (currentQuestion.type === 'MCQ') {
      studentAnswer = getSelectedMcqAnswer(els['ts-mcq-options']);
      if (!studentAnswer) {
        els['ts-question-error'].textContent = UI.selectOption;
        els['ts-question-error'].classList.remove('hidden');
        return;
      }
    } else {
      studentAnswer = (els['ts-answer-input'].value || '').trim();
      if (!studentAnswer) {
        els['ts-question-error'].textContent = UI.typeAnswer;
        els['ts-question-error'].classList.remove('hidden');
        return;
      }
    }

    isEvaluating = true;
    els['ts-question-error'].classList.add('hidden');
    els['ts-question-content'].classList.add('hidden');
    els['ts-evaluation-loading'].classList.remove('hidden');
    els['ts-submit-answer'].disabled = true;
    els['ts-submit-answer'].textContent = UI.evaluating;

    var section = lesson.sections[step - 1] || {};
    var sectionContent = section.explanation || section.description || '';

    var requestBody = {
      question: currentQuestion.question,
      questionType: currentQuestion.type,
      studentAnswer: studentAnswer,
      correctOptionIndex: currentQuestion.correctOptionIndex,
      lessonSection: sectionContent,
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College'
    };

    try {
      var res = await fetch('/api/lesson/evaluate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.evaluationLoadError);
      }

      lastEvaluation = await res.json();
      showFeedback(lastEvaluation);

      // Phase 7: If not correct and adaptations remaining, detect misconception
      if (lastEvaluation.status !== 'CORRECT' && adaptationCount < MAX_ADAPTATIONS) {
        setTimeout(function () {
          detectMisconception(studentAnswer, sectionContent);
        }, 800);
      }
    } catch (err) {
      els['ts-evaluation-loading'].classList.add('hidden');
      els['ts-question-error'].textContent = err.message || UI.evaluationLoadError;
      els['ts-question-error'].classList.remove('hidden');
      els['ts-question-content'].classList.remove('hidden');
      isEvaluating = false;
      els['ts-submit-answer'].disabled = false;
      els['ts-submit-answer'].textContent = UI.submitAnswer;
    }
  }

  /* ======================== Show Evaluation Feedback ======================== */

  function showFeedback(evaluation) {
    els['ts-evaluation-loading'].classList.add('hidden');
    els['ts-feedback'].classList.remove('hidden');

    var banner = els['ts-feedback-banner'];
    banner.className = 'feedback-banner';

    var statusText = '';
    if (evaluation.status === 'CORRECT') {
      banner.classList.add('feedback-correct');
      statusText = '✓ Correct!';
      questionAnswered = true;
    } else if (evaluation.status === 'PARTIALLY_CORRECT') {
      banner.classList.add('feedback-partial');
      statusText = '◐ Partially Correct';
    } else {
      banner.classList.add('feedback-incorrect');
      statusText = '✗ Not Quite';
    }

    banner.textContent = statusText;
    els['ts-feedback-text'].textContent = evaluation.feedback || '';

    if (evaluation.expectedConcept) {
      els['ts-feedback-concept'].textContent = 'Key concept: ' + evaluation.expectedConcept;
      els['ts-feedback-concept'].classList.remove('hidden');
    } else {
      els['ts-feedback-concept'].classList.add('hidden');
    }

    // If correct, show continue button. If not, wait for adaptive flow.
    if (evaluation.status === 'CORRECT') {
      els['ts-continue-lesson'].textContent = UI.continueLesson;
    } else {
      els['ts-continue-lesson'].textContent = UI.continueLesson;
    }

    els['ts-bubble'].textContent = statusText;
    avatarState('idle');
  }

  /* ======================== Phase 7: Detect Misconception ======================== */

  async function detectMisconception(studentAnswer, sectionContent) {
    if (!lastEvaluation || !currentQuestion) return;

    els['ts-adaptive-area'].classList.remove('hidden');
    els['ts-adaptive-loading'].classList.remove('hidden');
    els['ts-adaptive-loading'].querySelector('span:last-child').textContent = UI.analyzing;

    var section = lesson.sections[step - 1] || {};

    var requestBody = {
      question: currentQuestion.question,
      questionType: currentQuestion.type,
      studentAnswer: studentAnswer,
      correctConcept: lastEvaluation.expectedConcept || '',
      evaluationStatus: lastEvaluation.status,
      evaluationFeedback: lastEvaluation.feedback || '',
      lessonSection: sectionContent,
      sectionTitle: section.title || '',
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College',
      teachingStyle: lesson.teachingStyle || 'Simple Explanation',
      objective: lesson.objective || ''
    };

    try {
      var res = await fetch('/api/lesson/misconception', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.misconceptionLoadError);
      }

      lastMisconception = await res.json();

      if (lastMisconception.explanationNeeded) {
        // Generate adaptive teaching
        await generateAdaptation(studentAnswer, sectionContent);
      } else {
        // Student understood, no adaptation needed
        hideAdaptiveArea();
        questionAnswered = true;
        enableContinueButton();
      }
    } catch (err) {
      els['ts-adaptive-loading'].classList.add('hidden');
      els['ts-adaptive-error'].textContent = err.message || UI.misconceptionLoadError;
      els['ts-adaptive-error'].classList.remove('hidden');
      // Allow continuing even if misconception detection fails
      questionAnswered = true;
      enableContinueButton();
    }
  }

  /* ======================== Phase 7: Generate Adaptation ======================== */

  async function generateAdaptation(studentAnswer, sectionContent) {
    els['ts-adaptive-loading'].classList.remove('hidden');
    els['ts-adaptive-loading'].querySelector('span:last-child').textContent = UI.adapting;

    var section = lesson.sections[step - 1] || {};

    var requestBody = {
      understanding: lastMisconception.understanding,
      misconception: lastMisconception.misconception || '',
      recommendedApproach: lastMisconception.recommendedApproach || 'EXAMPLE_BASED',
      originalExplanation: section.explanation || section.description || '',
      originalExample: section.example || '',
      sectionTitle: section.title || '',
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College',
      teachingStyle: lesson.teachingStyle || 'Simple Explanation',
      objective: lesson.objective || '',
      studentAnswer: studentAnswer,
      question: currentQuestion.question,
      adaptationCount: adaptationCount
    };

    try {
      var res = await fetch('/api/lesson/adapt', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.adaptLoadError);
      }

      currentAdaptation = await res.json();
      adaptationCount++;
      displayAdaptiveTeaching(currentAdaptation);
    } catch (err) {
      els['ts-adaptive-loading'].classList.add('hidden');
      els['ts-adaptive-error'].textContent = err.message || UI.adaptLoadError;
      els['ts-adaptive-error'].classList.remove('hidden');
      questionAnswered = true;
      enableContinueButton();
    }
  }

  /* ======================== Phase 7: Display Adaptive Teaching ======================== */

  function displayAdaptiveTeaching(adaptation) {
    els['ts-adaptive-loading'].classList.add('hidden');
    els['ts-adaptive-content'].classList.remove('hidden');
    isAdaptiveMode = true;

    // Banner
    var banner = els['ts-adaptive-banner'];
    var bannerText = '';
    if (lastMisconception && lastMisconception.understanding === 'MISCONCEPTION') {
      bannerText = UI.LetsLookAnotherWay;
    } else if (lastMisconception && lastMisconception.understanding === 'NOT_UNDERSTOOD') {
      bannerText = UI.LetsSimplify;
    } else {
      bannerText = UI.clarification;
    }
    banner.textContent = bannerText + ' (' + adaptation.adaptationType.replace('_', ' ') + ')';

    // Explanation
    els['ts-adaptive-explanation'].textContent = adaptation.reExplanation || '';

    // Example
    if (adaptation.example) {
      els['ts-adaptive-example'].textContent = adaptation.example;
      els['ts-adaptive-example'].classList.remove('hidden');
    } else {
      els['ts-adaptive-example'].classList.add('hidden');
    }

    // Follow-up question
    if (adaptation.followUpQuestion) {
      displayFollowUpQuestion(adaptation);
    } else {
      // No follow-up question, just continue
      questionAnswered = true;
      enableContinueButton();
    }

    els['ts-bubble'].textContent = bannerText;
    avatarState('teaching');
  }

  /* ======================== Phase 7: Follow-up Question ======================== */

  function displayFollowUpQuestion(adaptation) {
    els['ts-adaptive-followup'].classList.remove('hidden');
    els['ts-adaptive-followup-loading'].classList.add('hidden');
    els['ts-adaptive-followup-content'].classList.remove('hidden');
    els['ts-adaptive-followup-text'].textContent = adaptation.followUpQuestion;

    if (adaptation.followUpQuestionType === 'MCQ' && adaptation.followUpOptions && adaptation.followUpOptions.length > 0) {
      renderMcqOptions(els['ts-adaptive-mcq-options'], adaptation.followUpOptions, true);
      els['ts-adaptive-mcq-options'].classList.remove('hidden');
      els['ts-adaptive-short-answer'].classList.add('hidden');
    } else {
      els['ts-adaptive-mcq-options'].classList.add('hidden');
      els['ts-adaptive-short-answer'].classList.remove('hidden');
      els['ts-adaptive-answer-input'].focus();
    }
  }

  /* ======================== Phase 7: Submit Follow-up Answer ======================== */

  async function submitFollowUpAnswer() {
    if (isEvaluating || !currentAdaptation) return;

    var studentAnswer = '';
    if (currentAdaptation.followUpQuestionType === 'MCQ') {
      studentAnswer = getSelectedMcqAnswer(els['ts-adaptive-mcq-options']);
      if (!studentAnswer) {
        els['ts-adaptive-error'].textContent = UI.selectOption;
        els['ts-adaptive-error'].classList.remove('hidden');
        return;
      }
    } else {
      studentAnswer = (els['ts-adaptive-answer-input'].value || '').trim();
      if (!studentAnswer) {
        els['ts-adaptive-error'].textContent = UI.typeAnswer;
        els['ts-adaptive-error'].classList.remove('hidden');
        return;
      }
    }

    isEvaluating = true;
    els['ts-adaptive-error'].classList.add('hidden');
    els['ts-adaptive-followup-content'].classList.add('hidden');
    els['ts-adaptive-evaluation-loading'].classList.remove('hidden');
    els['ts-adaptive-submit'].disabled = true;
    els['ts-adaptive-submit'].textContent = UI.evaluating;

    var section = lesson.sections[step - 1] || {};
    var sectionContent = section.explanation || section.description || '';

    var requestBody = {
      question: currentAdaptation.followUpQuestion,
      questionType: currentAdaptation.followUpQuestionType,
      studentAnswer: studentAnswer,
      correctOptionIndex: currentAdaptation.followUpCorrectOptionIndex,
      lessonSection: sectionContent,
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College'
    };

    try {
      var res = await fetch('/api/lesson/evaluate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.evaluationLoadError);
      }

      var evaluation = await res.json();
      showAdaptiveFeedback(evaluation);

      // If still not correct and adaptations remaining, try again
      if (evaluation.status !== 'CORRECT' && adaptationCount < MAX_ADAPTATIONS) {
        setTimeout(function () {
          adaptAgain(studentAnswer, sectionContent);
        }, 800);
      } else {
        questionAnswered = true;
      }
    } catch (err) {
      els['ts-adaptive-evaluation-loading'].classList.add('hidden');
      els['ts-adaptive-error'].textContent = err.message || UI.evaluationLoadError;
      els['ts-adaptive-error'].classList.remove('hidden');
      els['ts-adaptive-followup-content'].classList.remove('hidden');
      isEvaluating = false;
      els['ts-adaptive-submit'].disabled = false;
      els['ts-adaptive-submit'].textContent = UI.submitAnswer;
    }
  }

  /* ======================== Phase 7: Adapt Again ======================== */

  async function adaptAgain(studentAnswer, sectionContent) {
    // Update the misconception with the new answer
    lastEvaluation = {
      status: 'INCORRECT',
      feedback: 'Student still struggling after adaptation',
      expectedConcept: currentAdaptation.misconception || ''
    };
    lastMisconception = {
      understanding: 'MISCONCEPTION',
      misconception: currentAdaptation.misconception || '',
      recommendedApproach: currentAdaptation.adaptationType,
      explanationNeeded: true
    };

    els['ts-adaptive-evaluation-loading'].classList.add('hidden');
    hideAdaptiveArea();
    await generateAdaptation(studentAnswer, sectionContent);
  }

  /* ======================== Phase 7: Show Adaptive Feedback ======================== */

  function showAdaptiveFeedback(evaluation) {
    els['ts-adaptive-evaluation-loading'].classList.add('hidden');
    els['ts-adaptive-feedback'].classList.remove('hidden');

    var banner = els['ts-adaptive-feedback-banner'];
    banner.className = 'feedback-banner';

    var statusText = '';
    if (evaluation.status === 'CORRECT') {
      banner.classList.add('feedback-correct');
      statusText = '✓ Correct! Great progress!';
    } else if (evaluation.status === 'PARTIALLY_CORRECT') {
      banner.classList.add('feedback-partial');
      statusText = '◐ Getting closer!';
    } else {
      banner.classList.add('feedback-incorrect');
      if (adaptationCount >= MAX_ADAPTATIONS) {
        statusText = UI.maxAdaptationsReached;
      } else {
        statusText = '✗ Still not quite right';
      }
    }

    banner.textContent = statusText;
    els['ts-adaptive-feedback-text'].textContent = evaluation.feedback || '';

    // Enable continue after feedback
    questionAnswered = true;
    enableContinueButton();

    els['ts-bubble'].textContent = statusText;
    avatarState('idle');
  }

  /* ======================== Continue Button Helpers ======================== */

  function enableContinueButton() {
    els['ts-next'].disabled = false;
  }

  function showContinueButton() {
    els['ts-question-content'].classList.remove('hidden');
    els['ts-submit-answer'].classList.add('hidden');
    var continueBtn = document.createElement('button');
    continueBtn.type = 'button';
    continueBtn.className = 'btn btn-primary btn-block';
    continueBtn.textContent = UI.continueLesson;
    continueBtn.addEventListener('click', function () {
      hideQuestionArea();
      hideAdaptiveArea();
      questionAnswered = true;
      advanceToNextStep();
    });
    els['ts-question-content'].appendChild(continueBtn);
  }

  /* ======================== Advance to Next Step ======================== */

  function advanceToNextStep() {
    // Phase 9: Stop speech when navigating
    stopSpeech();

    hideQuestionArea();
    hideAdaptiveArea();
    if (step === lesson.sections.length) {
      step = lesson.sections.length + 1; // finish
    } else if (step < lesson.sections.length) {
      step++;
    }
    showStep();
  }

  /* ======================== Per-Step Rendering ======================== */

  function renderInteractiveWidget(hint, title) {
    var container = $('interactiveVisualWidget');
    if (!container) return;
    container.innerHTML = '';

    var fullContext = (String(hint || '') + ' ' + String(title || '') + ' ' + (lesson ? String(lesson.topic || '') : '')).toLowerCase();

    // 1. DNA / Genetics / Biology / Cell / Life
    if (fullContext.indexOf('dna') > -1 || fullContext.indexOf('gene') > -1 || fullContext.indexOf('bio') > -1 || fullContext.indexOf('cell') > -1 || fullContext.indexOf('replicat') > -1) {
      var dnaContainer = document.createElement('div');
      dnaContainer.className = 'dna-helix-3d';
      dnaContainer.innerHTML =
        '<div class="dna-pair"><span class="dna-node dna-node-a">A</span><div class="dna-bond"></div><span class="dna-node dna-node-t">T</span></div>' +
        '<div class="dna-pair"><span class="dna-node dna-node-c">C</span><div class="dna-bond"></div><span class="dna-node dna-node-g">G</span></div>' +
        '<div class="dna-pair"><span class="dna-node dna-node-t">T</span><div class="dna-bond"></div><span class="dna-node dna-node-a">A</span></div>' +
        '<div class="dna-pair"><span class="dna-node dna-node-g">G</span><div class="dna-bond"></div><span class="dna-node dna-node-c">C</span></div>';
      container.appendChild(dnaContainer);
    }
    // 2. Programming / Code / Python / Java / Recursion
    else if (fullContext.indexOf('code') > -1 || fullContext.indexOf('python') > -1 || fullContext.indexOf('java') > -1 || fullContext.indexOf('recursion') > -1 || fullContext.indexOf('algorithm') > -1) {
      var terminal = document.createElement('div');
      terminal.className = 'widget-code-terminal';
      terminal.innerHTML = '<code>// Interactive 3D Call Stack Sandbox\nfunction solve(topic) {\n  return "Mastered " + topic;\n}\nconsole.log(solve("' + escapeHtml(title || 'Concept') + '"));</code>';

      var runBtn = document.createElement('button');
      runBtn.type = 'button';
      runBtn.className = 'widget-run-btn';
      runBtn.textContent = '▶ Run Code';
      runBtn.onclick = function () {
        var output = document.createElement('div');
        output.style.color = '#31d0ff';
        output.style.marginTop = '6px';
        output.textContent = 'Output ➔ "Mastered ' + (title || 'Concept') + '"';
        terminal.appendChild(output);
        if (window.showToast) window.showToast('Executed code in sandbox!', 'success');
      };
      terminal.appendChild(runBtn);
      container.appendChild(terminal);
    }
    // 3. Physics / Motion / Newton / Force / Velocity
    else if (fullContext.indexOf('physic') > -1 || fullContext.indexOf('motion') > -1 || fullContext.indexOf('force') > -1 || fullContext.indexOf('newton') > -1 || fullContext.indexOf('gravity') > -1 || fullContext.indexOf('velocity') > -1) {
      var sandbox = document.createElement('div');
      sandbox.className = 'widget-vector-sandbox';
      ['Gravity 🌐', 'Velocity 🚀', 'Friction ⚙️'].forEach(function (btnLabel, idx) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'vector-toggle-btn' + (idx === 0 ? ' active' : '');
        btn.textContent = btnLabel;
        btn.onclick = function () {
          sandbox.querySelectorAll('.vector-toggle-btn').forEach(function(b){ b.classList.remove('active'); });
          btn.classList.add('active');
          if (window.showToast) window.showToast('Simulating: ' + btnLabel, 'info');
        };
        sandbox.appendChild(btn);
      });
      container.appendChild(sandbox);
    }
    // 4. Mathematics / Geometry / Equation / Calculus
    else if (fullContext.indexOf('math') > -1 || fullContext.indexOf('equation') > -1 || fullContext.indexOf('pythagoras') > -1 || fullContext.indexOf('calculus') > -1 || fullContext.indexOf('geometry') > -1) {
      var stepGroup = document.createElement('div');
      stepGroup.className = 'widget-step-buttons';
      ['Step 1: Formula', 'Step 2: Substitution', 'Step 3: Calculation'].forEach(function (label) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'step-calc-btn';
        btn.textContent = label;
        btn.onclick = function () {
          if (window.showToast) window.showToast('Calculation: ' + label, 'info');
        };
        stepGroup.appendChild(btn);
      });
      container.appendChild(stepGroup);
    }
    // 5. Universal Fallback: 3D Atomic Orbit & Concept Network
    else {
      var atomicContainer = document.createElement('div');
      atomicContainer.className = 'atomic-orbit-3d';
      atomicContainer.innerHTML = '<div class="atomic-ring"></div><div class="atomic-nucleus">⚛️</div>';
      container.appendChild(atomicContainer);
    }
  }

  function applyTeacherPersonaStyling() {
    var personaVal = (profile && profile.persona) ? profile.persona : 'chopper';
    var iconEl = $('animeAvatarIconDisplay');
    var nameEl = $('animeAvatarNameDisplay');
    var particlesEl = $('ts-bubble-particles');
    var avatarBox = els['ts-avatar'];

    if (avatarBox) {
      avatarBox.classList.remove('aura-blue', 'aura-red', 'aura-green');
      if (personaVal === 'shanks') {
        avatarBox.classList.add('aura-red');
        if (iconEl) iconEl.textContent = '🏴‍☠️';
        if (nameEl) nameEl.textContent = 'Shanks';
        if (particlesEl) particlesEl.textContent = '⚡';
      } else if (personaVal === 'lucky_roux') {
        avatarBox.classList.add('aura-green');
        if (iconEl) iconEl.textContent = '🍗';
        if (nameEl) nameEl.textContent = 'Lucky Roux';
        if (particlesEl) particlesEl.textContent = '✨';
      } else {
        avatarBox.classList.add('aura-blue');
        if (iconEl) iconEl.textContent = '🧪';
        if (nameEl) nameEl.textContent = 'Chopper';
        if (particlesEl) particlesEl.textContent = '🌸';
      }
    }
  }

  function renderIntro() {
    clearBody();
    applyTeacherPersonaStyling();

    els['ts-section-title'].textContent = UI.welcomeTitle;
    els['ts-section-title'].className = 'teach-body-heading';
    addParagraph(els['ts-text'], lesson.introduction, 'muted');

    var objectivesTitle = document.createElement('h4');
    objectivesTitle.className = 'teach-objectives-title';
    objectivesTitle.textContent = UI.objectivesTitle;
    els['ts-text'].appendChild(objectivesTitle);

    var list = document.createElement('ul');
    list.className = 'teach-objectives';
    (lesson.learningObjectives || []).forEach(function (objective) {
      var li = document.createElement('li');
      li.textContent = objective;
      list.appendChild(li);
    });
    els['ts-text'].appendChild(list);

    els['ts-visual-title'].textContent = lesson.lessonTitle;
    els['ts-visual-note'].textContent = UI.teaching;
    renderInteractiveWidget('generic', lesson.lessonTitle);

    var bubbleText = els['ts-bubble'].querySelector('#ts-bubble-text');
    if (bubbleText) bubbleText.textContent = UI.welcomeTitle;

    var introText = lesson.introduction || '';
    if (lesson.learningObjectives && lesson.learningObjectives.length > 0) {
      introText += ' ' + UI.objectivesTitle + ' ' + lesson.learningObjectives.join('. ');
    }
    showSubtitle(introText);
  }

  function renderSection(index) {
    clearBody();
    applyTeacherPersonaStyling();

    var section = lesson.sections[index] || {};
    var title = section.title || (UI.sectionFallback + ' ' + (index + 1));
    var explanation = section.explanation || section.description || '';
    var example = section.example;

    els['ts-section-title'].textContent = title;
    els['ts-section-title'].className = 'teach-body-heading';
    addParagraph(els['ts-text'], explanation || UI.noText, 'muted');

    if (example) {
      var label = document.createElement('h4');
      label.className = 'teach-example-title';
      label.textContent = UI.exampleLabel;
      els['ts-text'].appendChild(label);
      var block = document.createElement('div');
      block.className = 'teach-example-block';
      block.textContent = example;
      els['ts-text'].appendChild(block);
    }

    els['ts-visual-title'].textContent = title;
    els['ts-visual-note'].textContent = UI.teaching;
    renderInteractiveWidget(section.visualHint, title);

    var bubbleText = els['ts-bubble'].querySelector('#ts-bubble-text');
    if (bubbleText) bubbleText.textContent = UI.teaching;

    var sectionText = '';
    if (title) sectionText += title + '. ';
    if (explanation) sectionText += explanation;
    if (example) sectionText += '. Example: ' + example;
    showSubtitle(sectionText);
  }

  function renderFinish() {
    clearBody();
    hideQuestionArea();
    hideAdaptiveArea();
    els['ts-stage'].classList.add('hidden');
    els['ts-body'].classList.add('hidden');
    els['ts-complete'].classList.remove('hidden');

    els['ts-complete'].querySelector('.complete-title').textContent = UI.complete;
    els['ts-complete'].querySelector('.complete-text').textContent =
      UI.completeBody + ' ' + lesson.lessonTitle;

    var stats = els['ts-complete'].querySelector('.complete-stats');
    stats.textContent = '';
    addParagraph(stats, lesson.sections.length + ' sections');
    addParagraph(stats, UI.timeLabel + ' ' + lesson.estimatedMinutes + ' ' + UI.minutes);
    addParagraph(stats, (lesson.learningObjectives || []).length + ' learning objectives');

    // Phase 8: Add assessment button
    var assessmentBtn = document.createElement('button');
    assessmentBtn.className = 'btn btn-primary btn-block';
    assessmentBtn.textContent = 'Take Final Assessment';
    assessmentBtn.addEventListener('click', function () {
      startAssessment();
    });
    stats.appendChild(assessmentBtn);

    els['ts-bubble'].textContent = UI.complete;
    avatarState('idle');
  }

  function startAssessment() {
    // Hide the teaching screen and show the assessment screen
    els['teachingScreen'].classList.add('hidden');
    if (window.AiTeacherAssessment) {
      window.AiTeacherAssessment.init(lesson, profile);
    }
  }

  function showStep() {
    var sections = lesson.sections.length;

    // restore stage/body visibility (finish hides them)
    els['ts-stage'].classList.remove('hidden');
    els['ts-body'].classList.remove('hidden');
    els['ts-complete'].classList.add('hidden');
    hideQuestionArea();
    hideAdaptiveArea();
    resetQuestionState();

    if (isIntro()) {
      renderIntro();
    } else if (isFinish()) {
      renderFinish();
    } else {
      renderSection(step - 1);
      // After rendering a section, generate a question for it
      setTimeout(function () {
        generateQuestion(step - 1);
      }, 300);
    }

    // progress indicator
    els['ts-progress-label'].textContent = isIntro()
      ? UI.introLabel
      : (isFinish() ? UI.complete : 'Section ' + step + ' of ' + sections);
    els['ts-progress-bar'].style.width = ((step / (totalSteps() - 1)) * 100).toFixed(1) + '%';

    // navigation — hide Next until question is answered (for sections)
    els['ts-prev'].disabled = isIntro();
    if (isFinish()) {
      els['ts-next'].classList.add('hidden');
    } else {
      els['ts-next'].textContent = step === sections ? UI.finish : UI.next;
      els['ts-next'].classList.remove('hidden');
      // For sections, disable Next until question is answered
      if (isSection()) {
        els['ts-next'].disabled = true;
      } else {
        els['ts-next'].disabled = false;
      }
      avatarState('transition');
    }

    // Phase 9: Update voice availability for current language
    updateVoiceAvailability();

    els['teachingScreen'].scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /* ======================== Public API ==================================== */

  function render(plan, studentProfile) {
    cacheEls();

    if (!plan || !plan.lessonTitle || !plan.introduction ||
        !Array.isArray(plan.sections) || plan.sections.length === 0) {
      els['ts-body'].classList.remove('hidden');
      els['ts-stage'].classList.add('hidden');
      els['ts-complete'].classList.add('hidden');
      els['ts-text'].textContent = '';
      els['ts-section-title'].textContent = '';
      addParagraph(els['ts-text'], UI.loadError, 'muted');
      els['ts-prev'].disabled = true;
      els['ts-next'].classList.add('hidden');
      els['teachingScreen'].classList.remove('hidden');
      return;
    }

    lesson = plan;
    profile = studentProfile || {};
    step = 0;

    els['teachingScreen'].classList.remove('hidden');
    var profileForm = $('profileForm');
    if (profileForm) { profileForm.classList.add('hidden'); }
    els['ts-title'].textContent = plan.lessonTitle;
    var metaParts = [plan.topic, plan.language, plan.educationLevel, plan.teachingStyle]
      .filter(function (v) { return v; });
    els['ts-meta'].textContent = metaParts.join(' · ') +
      (plan.estimatedMinutes ? ' · ' + UI.timeLabel + ' ' + plan.estimatedMinutes + ' ' + UI.minutes : '');

    // teaching-style emphasis
    var style = String(plan.teachingStyle || '').toLowerCase();
    els['teachingScreen'].className = 'card card-pad stack teaching-screen' +
      (style.indexOf('visual') >= 0 ? ' teach-style-visual' : '') +
      (style.indexOf('example') >= 0 ? ' teach-style-example' : '') +
      (style.indexOf('step') >= 0 ? ' teach-style-step' : '') +
      (style.indexOf('simple') >= 0 ? ' teach-style-simple' : '');

    // assign handlers
    els['ts-edit'].onclick = function () {
      // Phase 9: Stop speech when editing profile
      stopSpeech();
      // Phase 10: Stop any recording when leaving the lesson
      if (window.AiTeacherRecorder) {
        window.AiTeacherRecorder.endLesson();
      }
      els['teachingScreen'].classList.add('hidden');
      hideQuestionArea();
      hideAdaptiveArea();
      var form = $('profileForm');
      if (form) { form.classList.remove('hidden'); }
    };

    els['ts-prev'].onclick = function () {
      if (step > 0) {
        step--;
        showStep();
      }
    };

    els['ts-next'].onclick = function () {
      if (isSection() && !questionAnswered) {
        return; // must answer question first
      }
      advanceToNextStep();
    };

    els['ts-submit-answer'].onclick = function () {
      submitAnswer();
    };

    els['ts-continue-lesson'].onclick = function () {
      advanceToNextStep();
    };

    // Phase 7: adaptive follow-up handlers
    els['ts-adaptive-submit'].onclick = function () {
      submitFollowUpAnswer();
    };

    els['ts-adaptive-continue'].onclick = function () {
      advanceToNextStep();
    };

    // Phase 9: Initialize voice controls
    initVoiceControls();

    // Phase 10: Activate the lesson video recorder for this real lesson
    if (window.AiTeacherRecorder) {
      window.AiTeacherRecorder.beginLesson(plan, profile);
    }

    showStep();
  }

  /** Test hook: exposes the current navigation state. */
  function state() {
    if (!lesson) { return null; }
    return {
      step: step,
      totalSections: lesson.sections.length,
      totalSteps: totalSteps(),
      isIntro: isIntro(),
      isFinish: isFinish(),
      currentTitle: isIntro() ? lesson.lessonTitle
        : (isFinish() ? null : (lesson.sections[step - 1] || {}).title || null),
      questionAnswered: questionAnswered,
      currentQuestion: currentQuestion,
      adaptationCount: adaptationCount,
      isAdaptiveMode: isAdaptiveMode
    };
  }

  return { render: render, state: state };
})();
