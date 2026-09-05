/* ==========================================================================
   AI TEACHER — Phase 8 assessment + learning report
   Presents a final assessment based on the ACTUAL lesson content. Questions
   are dynamically generated, answers are evaluated with AI, and a learning
   report shows the student's performance. No hardcoded questions, no fake
   scores — everything comes from real data.
   ========================================================================== */
window.AiTeacherAssessment = (function () {
  'use strict';

  var UI = {
    assessmentTitle: 'Final Assessment',
    assessmentSubtitle: 'Test your understanding of the lesson',
    generating: 'Generating your assessment…',
    evaluating: 'Evaluating your answers…',
    preparing: 'Preparing your report…',
    submitAssessment: 'Submit Assessment',
    questionOf: 'Question {current} of {total}',
    allQuestionsRequired: 'Please answer all questions before submitting.',
    submitError: 'Unable to submit the assessment. Please try again.',
    generateError: 'Unable to generate the assessment. Please try again.',
    startNewLesson: 'Start New Lesson',
    scoreLabel: 'Your Score',
    conceptsUnderstood: 'Concepts You Understood',
    weakAreas: 'Areas That Need More Practice',
    incorrectConcepts: 'Incorrect Concepts',
    revisionRecommendations: 'Recommended Revision',
    suggestedNextTopic: 'Suggested Next Topic',
    noConcepts: 'No concepts listed',
    noRecommendations: 'No specific recommendations',
    questionNumber: 'Question {number}'
  };

  var assessment = null;      // the generated assessment
  var lesson = null;           // the lesson plan
  var profile = null;          // student profile
  var currentQuestionIndex = 0;
  var answers = [];            // student answers
  var isSubmitting = false;
  var lastResult = null;       // last assessment result for saving progress
  var els = {};

  function $(id) {
    return document.getElementById(id);
  }

  function cacheEls() {
    ['assessmentScreen', 'assessment-title', 'assessment-subtitle',
      'assessment-progress-label', 'assessment-progress-bar',
      'assessment-loading', 'assessment-content', 'assessment-questions',
      'assessment-error', 'assessment-submit', 'assessment-evaluation-loading',
      'reportScreen', 'report-loading', 'report-content',
      'report-score-circle', 'report-score-value',
      'report-score-text', 'report-summary',
      'report-concepts-understood', 'report-concepts-list',
      'report-weak-areas', 'report-weak-list',
      'report-incorrect-concepts', 'report-incorrect-list',
      'report-recommendations', 'report-recommendations-list',
      'report-next-topic', 'report-next-topic-text',
      'report-new-lesson', 'report-view-dashboard'].forEach(function (id) {
      els[id] = $(id);
    });
  }

  /* ======================== Assessment Generation ======================== */

  async function generateAssessment(lessonPlan, studentProfile) {
    lesson = lessonPlan;
    profile = studentProfile || {};

    showAssessmentScreen();
    els['assessment-loading'].classList.remove('hidden');
    els['assessment-content'].classList.add('hidden');
    els['assessment-evaluation-loading'].classList.add('hidden');

    var sections = (lesson.sections || []).map(function (s) {
      return {
        title: s.title || '',
        explanation: s.explanation || s.description || '',
        example: s.example || ''
      };
    });

    var requestBody = {
      topic: lesson.topic || lesson.lessonTitle,
      language: lesson.language || 'English',
      educationLevel: lesson.educationLevel || 'College',
      teachingStyle: lesson.teachingStyle || 'Simple Explanation',
      objective: lesson.objective || '',
      priorKnowledge: lesson.priorKnowledge || '',
      availableTime: lesson.availableTime || '',
      desiredDepth: lesson.desiredDepth || '',
      lessonTitle: lesson.lessonTitle || '',
      introduction: lesson.introduction || '',
      sections: sections,
      questionCount: 5
    };

    try {
      var res = await fetch('/api/assessment/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.generateError);
      }

      assessment = await res.json();
      answers = new Array(assessment.questions.length).fill(null);
      currentQuestionIndex = 0;

      els['assessment-loading'].classList.add('hidden');
      els['assessment-content'].classList.remove('hidden');
      els['assessment-title'].textContent = UI.assessmentTitle;
      els['assessment-subtitle'].textContent = lesson.lessonTitle + ' · ' + (lesson.language || 'English');

      renderAssessmentQuestions();
      updateAssessmentProgress();
    } catch (err) {
      els['assessment-loading'].classList.add('hidden');
      els['assessment-error'].textContent = err.message || UI.generateError;
      els['assessment-error'].classList.remove('hidden');
    }
  }

  /* ======================== Render Assessment Questions ======================== */

  function renderAssessmentQuestions() {
    var container = els['assessment-questions'];
    container.innerHTML = '';

    if (!assessment || !assessment.questions) return;

    assessment.questions.forEach(function (q, index) {
      var questionDiv = document.createElement('div');
      questionDiv.className = 'assessment-question';
      questionDiv.setAttribute('data-index', index);

      // Question header
      var header = document.createElement('div');
      header.className = 'assessment-question-header';
      var number = document.createElement('span');
      number.className = 'assessment-question-number';
      number.textContent = UI.questionNumber.replace('{number}', index + 1);
      var type = document.createElement('span');
      type.className = 'assessment-question-type chip';
      type.textContent = q.type;
      header.appendChild(number);
      header.appendChild(type);
      questionDiv.appendChild(header);

      // Question text
      var questionText = document.createElement('p');
      questionText.className = 'assessment-question-text';
      questionText.textContent = q.question;
      questionDiv.appendChild(questionText);

      // Answer input based on type
      if (q.type === 'MCQ' && q.options && q.options.length > 0) {
        var optionsDiv = document.createElement('div');
        optionsDiv.className = 'option-list assessment-options';
        var keys = ['A', 'B', 'C', 'D'];
        q.options.forEach(function (opt, optIndex) {
          var btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'option assessment-option';
          btn.setAttribute('aria-pressed', 'false');
          btn.innerHTML = '<span class="key">' + keys[optIndex] + '</span><span>' + escapeHtml(opt) + '</span>';
          btn.addEventListener('click', function () {
            optionsDiv.querySelectorAll('.option').forEach(function (o) {
              o.setAttribute('aria-pressed', 'false');
            });
            btn.setAttribute('aria-pressed', 'true');
            answers[index] = {
              question: q.question,
              answer: opt,
              questionType: q.type,
              selectedOptionIndex: optIndex
            };
          });
          optionsDiv.appendChild(btn);
        });
        questionDiv.appendChild(optionsDiv);
      } else {
        // Short answer or application
        var textarea = document.createElement('textarea');
        textarea.className = 'textarea assessment-textarea';
        textarea.rows = 3;
        textarea.placeholder = 'Type your answer here…';
        textarea.maxLength = 1000;
        textarea.setAttribute('data-question-index', index);
        textarea.addEventListener('input', function () {
          answers[index] = {
            question: q.question,
            answer: textarea.value.trim(),
            questionType: q.type,
            selectedOptionIndex: null
          };
        });
        questionDiv.appendChild(textarea);
      }

      container.appendChild(questionDiv);
    });

    // Scroll to first question
    if (container.children.length > 0) {
      container.children[0].scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /* ======================== Assessment Progress ======================== */

  function updateAssessmentProgress() {
    if (!assessment) return;
    var total = assessment.questions.length;
    var answered = answers.filter(function (a) { return a !== null; }).length;
    els['assessment-progress-label'].textContent = UI.questionOf
      .replace('{current}', answered)
      .replace('{total}', total);
    els['assessment-progress-bar'].style.width = ((answered / total) * 100).toFixed(1) + '%';
  }

  /* ======================== Submit Assessment ======================== */

  async function submitAssessment() {
    if (isSubmitting || !assessment) return;

    // Validate all questions are answered
    var unanswered = [];
    answers.forEach(function (answer, index) {
      if (answer === null || (answer.answer && answer.answer.trim() === '')) {
        unanswered.push(index + 1);
      }
    });

    if (unanswered.length > 0) {
      els['assessment-error'].textContent = UI.allQuestionsRequired;
      els['assessment-error'].classList.remove('hidden');
      return;
    }

    isSubmitting = true;
    els['assessment-error'].classList.add('hidden');
    els['assessment-content'].classList.add('hidden');
    els['assessment-evaluation-loading'].classList.remove('hidden');
    els['assessment-submit'].disabled = true;
    els['assessment-submit'].textContent = UI.evaluating;

    var requestBody = {
      assessmentId: assessment.assessmentId,
      topic: assessment.topic,
      language: assessment.language,
      educationLevel: assessment.educationLevel,
      questions: assessment.questions,
      answers: answers,
      lessonTitle: lesson.lessonTitle || ''
    };

    try {
      var res = await fetch('/api/assessment/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || UI.submitError);
      }

      var result = await res.json();
      showReport(result);
    } catch (err) {
      els['assessment-evaluation-loading'].classList.add('hidden');
      els['assessment-content'].classList.remove('hidden');
      els['assessment-error'].textContent = err.message || UI.submitError;
      els['assessment-error'].classList.remove('hidden');
      isSubmitting = false;
      els['assessment-submit'].disabled = false;
      els['assessment-submit'].textContent = UI.submitAssessment;
    }
  }

  /* ======================== Show Learning Report ======================== */

  function showReport(result) {
    lastResult = result; // Store for saving progress
    hideAssessmentScreen();
    showReportScreen();
    els['report-loading'].classList.remove('hidden');
    els['report-content'].classList.add('hidden');

    // Simulate a brief loading for UX
    setTimeout(function () {
      els['report-loading'].classList.add('hidden');
      els['report-content'].classList.remove('hidden');
      renderReport(result);

      // Phase 11: Save learning session to progress
      saveProgressToBackend();
    }, 800);
  }

  /* ======================== Phase 11: Save Progress ======================== */

  async function saveProgressToBackend() {
    if (!profile || !lastResult) return;

    if (window.AiTeacherProgress) {
      await window.AiTeacherProgress.save(profile, lastResult);
    }
  }

  function renderReport(result) {
    // Score circle
    var percentage = Math.round(result.percentage || 0);
    els['report-score-value'].textContent = percentage;

    // Score text
    els['report-score-text'].textContent = UI.scoreLabel + ': ' + result.score + '/' + result.total;

    // Summary
    els['report-summary'].textContent = result.performanceSummary || '';

    // Student Mastery Power Meter
    var powerFill = $('powerMeterBarFill');
    var powerRank = $('powerMeterRank');
    if (powerFill && powerRank) {
      powerFill.style.width = percentage + '%';
      if (percentage >= 85) {
        powerRank.textContent = '⚡ Supreme Haki Master';
        powerRank.style.background = 'var(--ok)';
      } else if (percentage >= 70) {
        powerRank.textContent = '🥇 Master';
        powerRank.style.background = 'var(--brand-2)';
      } else if (percentage >= 50) {
        powerRank.textContent = '🥈 Adept';
        powerRank.style.background = 'var(--warn)';
      } else {
        powerRank.textContent = '🥉 Novice';
        powerRank.style.background = 'var(--bad)';
      }
    }

    // Render Learning Path Roadmap
    var pathContainer = $('report-path-steps');
    if (pathContainer && lesson) {
      pathContainer.innerHTML = '';
      var topicName = lesson.topic || lesson.lessonTitle || 'Topic';
      var sections = lesson.sections || [];
      var pathSteps = [
        '1. Fundamentals & Core Concepts of ' + topicName,
        '2. Practical Applications & Real-world Examples',
        '3. Synthesis & Misconception Resolution'
      ];
      if (result.suggestedNextTopic) {
        pathSteps.push('4. Next Mastery Step: ' + result.suggestedNextTopic);
      }
      pathSteps.forEach(function (stepText, idx) {
        var stepDiv = document.createElement('div');
        stepDiv.className = 'path-step-item';
        stepDiv.innerHTML = '<span class="path-step-badge">' + (idx + 1) + '</span><span class="path-step-title">' + escapeHtml(stepText) + '</span>';
        pathContainer.appendChild(stepDiv);
      });
    }

    // Concepts understood
    var conceptsList = els['report-concepts-list'];
    conceptsList.innerHTML = '';
    if (result.conceptsUnderstood && result.conceptsUnderstood.length > 0) {
      result.conceptsUnderstood.forEach(function (concept) {
        var li = document.createElement('li');
        li.textContent = concept;
        conceptsList.appendChild(li);
      });
    } else {
      var li = document.createElement('li');
      li.className = 'muted';
      li.textContent = UI.noConcepts;
      conceptsList.appendChild(li);
    }

    // Weak areas
    var weakList = els['report-weak-list'];
    weakList.innerHTML = '';
    if (result.weakAreas && result.weakAreas.length > 0) {
      result.weakAreas.forEach(function (area) {
        var li = document.createElement('li');
        li.textContent = area;
        weakList.appendChild(li);
      });
    } else {
      var li = document.createElement('li');
      li.className = 'muted';
      li.textContent = 'No significant weak areas detected';
      weakList.appendChild(li);
    }

    // Incorrect concepts
    var incorrectList = els['report-incorrect-list'];
    incorrectList.innerHTML = '';
    if (result.incorrectConcepts && result.incorrectConcepts.length > 0) {
      result.incorrectConcepts.forEach(function (concept) {
        var li = document.createElement('li');
        li.textContent = concept;
        incorrectList.appendChild(li);
      });
    } else {
      var li = document.createElement('li');
      li.className = 'muted';
      li.textContent = 'No incorrect concepts';
      incorrectList.appendChild(li);
    }

    // Revision recommendations
    var recsContainer = els['report-recommendations-list'];
    recsContainer.innerHTML = '';
    if (result.revisionRecommendations && result.revisionRecommendations.length > 0) {
      result.revisionRecommendations.forEach(function (rec) {
        var recDiv = document.createElement('div');
        recDiv.className = 'revision-card';
        var conceptP = document.createElement('p');
        conceptP.className = 'revision-concept';
        conceptP.textContent = rec.concept;
        var recP = document.createElement('p');
        recP.className = 'revision-text';
        recP.textContent = rec.recommendation;
        recDiv.appendChild(conceptP);
        recDiv.appendChild(recP);
        recsContainer.appendChild(recDiv);
      });
    } else {
      var p = document.createElement('p');
      p.className = 'muted';
      p.textContent = UI.noRecommendations;
      recsContainer.appendChild(p);
    }

    // Suggested next topic
    els['report-next-topic-text'].textContent = result.suggestedNextTopic || 'No suggestion available';

    // Set score circle color based on percentage
    var circle = els['report-score-circle'];
    circle.className = 'score-circle';
    if (percentage >= 80) {
      circle.classList.add('score-excellent');
    } else if (percentage >= 60) {
      circle.classList.add('score-good');
    } else if (percentage >= 40) {
      circle.classList.add('score-fair');
    } else {
      circle.classList.add('score-needs-work');
    }
  }

  /* ======================== Screen Management ======================== */

  function showAssessmentScreen() {
    hideAllScreens();
    els['assessmentScreen'].classList.remove('hidden');
    els['assessmentScreen'].scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function hideAssessmentScreen() {
    els['assessmentScreen'].classList.add('hidden');
  }

  function showReportScreen() {
    els['reportScreen'].classList.remove('hidden');
    els['reportScreen'].scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function hideReportScreen() {
    els['reportScreen'].classList.add('hidden');
  }

  function hideAllScreens() {
    var profileForm = $('profileForm');
    if (profileForm) profileForm.classList.add('hidden');
    var teachingScreen = $('teachingScreen');
    if (teachingScreen) teachingScreen.classList.add('hidden');
    hideAssessmentScreen();
    hideReportScreen();
  }

  /* ======================== Start New Lesson ======================== */

  function startNewLesson() {
    hideAllScreens();
    // Phase 10: stop any active recording when starting a new lesson
    if (window.AiTeacherRecorder) {
      window.AiTeacherRecorder.endLesson();
    }
    assessment = null;
    lesson = null;
    profile = null;
    currentQuestionIndex = 0;
    answers = [];
    isSubmitting = false;

    var profileForm = $('profileForm');
    if (profileForm) {
      profileForm.classList.remove('hidden');
      profileForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /* ======================== Utility ======================== */

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /* ======================== Public API ==================================== */

  function init(lessonPlan, studentProfile) {
    cacheEls();

    // Assign handlers
    els['assessment-submit'].onclick = function () {
      submitAssessment();
    };

    els['report-new-lesson'].onclick = function () {
      startNewLesson();
    };

    var printBtn = $('reportPrintBtn');
    if (printBtn) {
      printBtn.onclick = function () {
        window.print();
      };
    }

    // Phase 11: View Dashboard handler
    if (els['report-view-dashboard']) {
      els['report-view-dashboard'].onclick = function () {
        hideReportScreen();
        if (window.AiTeacherProgress && profile) {
          window.AiTeacherProgress.init(profile.name);
        }
      };
    }

    // Generate assessment
    generateAssessment(lessonPlan, studentProfile);
  }

  function state() {
    return {
      assessment: assessment,
      currentQuestionIndex: currentQuestionIndex,
      answers: answers,
      isSubmitting: isSubmitting
    };
  }

  return { init: init, state: state };
})();
