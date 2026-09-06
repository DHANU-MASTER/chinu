/* ==========================================================================
   AI TEACHER — Phase 11: Progress Dashboard + Learning History
   Displays the student's actual learning progress, history, and recommendations.
   All data comes from stored learning sessions — no hardcoded values.
   ========================================================================== */
window.AiTeacherProgress = (function () {
  'use strict';

  var UI = {
    dashboardTitle: 'My Learning',
    noHistory: 'No learning history yet.',
    noHistorySub: 'Complete your first lesson to start tracking progress.',
    lessonsCompleted: 'Lessons Completed',
    averageScore: 'Average Score',
    bestScore: 'Best Score',
    topicsStudied: 'Topics Studied',
    recentActivity: 'Recent Activity',
    yourStrengths: 'Your Strengths',
    needsRevision: 'Needs Revision',
    noWeakAreas: 'No major weak areas detected yet.',
    revisionRecommendations: 'Recommended Revision',
    nextTopic: 'Recommended Next Topic',
    noNextTopic: 'Complete a lesson to receive your next-topic recommendation.',
    topic: 'Topic',
    score: 'Score',
    date: 'Date',
    status: 'Status',
    viewDashboard: 'View Dashboard',
    backToLesson: 'Back to Lesson',
    strong: 'Strong',
    good: 'Good',
    needsRevisionStatus: 'Needs Revision',
    weak: 'Weak',
    percent: '%'
  };

  var els = {};
  var studentName = '';
  var lastSummaryData = null;

  function $(id) {
    return document.getElementById(id);
  }

  function cacheEls() {
    ['progressDashboard', 'progress-summary-cards', 'progress-history-table',
      'progress-history-body', 'progress-weak-areas', 'progress-weak-list',
      'progress-strengths', 'progress-strengths-list',
      'progress-recommendations', 'progress-recommendations-list',
      'progress-next-topic', 'progress-next-topic-text',
      'progress-empty-state', 'progress-loading', 'progress-content',
      'progress-error'].forEach(function (id) {
      els[id] = $(id);
    });
  }

  /* ======================== Load Progress Data ======================== */

  async function loadProgress(name) {
    cacheEls();
    studentName = name;
    showDashboard();
    els['progress-loading'].classList.remove('hidden');
    els['progress-content'].classList.add('hidden');
    els['progress-error'].classList.add('hidden');

    try {
      var res = await fetch('/api/progress/summary?studentName=' + encodeURIComponent(name));
      if (!res.ok) {
        var errorBody;
        try { errorBody = await res.json(); } catch (e) { /* ignore */ }
        throw new Error((errorBody && errorBody.error) || 'Unable to load progress data.');
      }

      var summary = await res.json();
      els['progress-loading'].classList.add('hidden');
      els['progress-content'].classList.remove('hidden');
      renderDashboard(summary);
    } catch (err) {
      els['progress-loading'].classList.add('hidden');
      els['progress-error'].textContent = err.message;
      els['progress-error'].classList.remove('hidden');
    }
  }

  /* ======================== Render Dashboard ======================== */

  function renderDashboard(summary) {
    lastSummaryData = summary;

    // Summary cards
    renderSummaryCards(summary);

    // History table
    renderHistoryTable(summary.recentActivity || []);

    // Weak areas
    renderWeakAreas(summary.weakConcepts || []);

    // Strengths
    renderStrengths(summary.strengths || []);

    // Recommendations
    renderRecommendations(summary.latestRecommendations || []);

    // Next topic
    renderNextTopic(summary.latestNextTopic);

    // Search filter setup
    var searchInput = $('historySearchInput');
    if (searchInput) {
      searchInput.oninput = function () {
        var query = searchInput.value.toLowerCase().trim();
        var filtered = (lastSummaryData && lastSummaryData.recentActivity) ? lastSummaryData.recentActivity.filter(function (item) {
          return String(item.topic || '').toLowerCase().indexOf(query) > -1 ||
                 String(item.percentage || '').indexOf(query) > -1 ||
                 String(item.completedAt || '').toLowerCase().indexOf(query) > -1 ||
                 String(item.status || '').toLowerCase().indexOf(query) > -1;
        }) : [];
        renderHistoryTable(filtered);
      };
    }

    // CSV Export setup
    var exportBtn = $('btnExportHistoryCsv');
    if (exportBtn) {
      exportBtn.onclick = function () {
        if (!lastSummaryData || !lastSummaryData.recentActivity || lastSummaryData.recentActivity.length === 0) {
          if (window.showToast) window.showToast('No history records to export.', 'warn');
          return;
        }
        var csvRows = ['Topic,Score (%),Date,Status'];
        lastSummaryData.recentActivity.forEach(function (row) {
          csvRows.push('"' + String(row.topic).replace(/"/g, '""') + '",' + row.percentage + ',"' + row.completedAt + '","' + row.status + '"');
        });
        var blob = new Blob([csvRows.join('\n')], { type: 'text/csv' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = (studentName || 'learning') + '_history.csv';
        a.click();
        URL.revokeObjectURL(url);
        if (window.showToast) window.showToast('Exported learning history CSV!', 'success');
      };
    }

    // Empty state
    if (summary.totalLessonsCompleted === 0) {
      els['progress-empty-state'].classList.remove('hidden');
      els['progress-summary-cards'].classList.add('hidden');
    } else {
      els['progress-empty-state'].classList.add('hidden');
      els['progress-summary-cards'].classList.remove('hidden');
    }
  }

  function renderSummaryCards(summary) {
    var container = els['progress-summary-cards'];
    container.innerHTML = '';

    // Lessons completed card
    container.appendChild(createSummaryCard(
      UI.lessonsCompleted,
      summary.totalLessonsCompleted.toString(),
      'card-lessons'
    ));

    // Average score card
    container.appendChild(createSummaryCard(
      UI.averageScore,
      Math.round(summary.averageScore) + UI.percent,
      'card-avg'
    ));

    // Best score card
    container.appendChild(createSummaryCard(
      UI.bestScore,
      summary.bestScore + UI.percent,
      'card-best'
    ));

    // Topics studied card
    container.appendChild(createSummaryCard(
      UI.topicsStudied,
      (summary.topicsStudied || []).length.toString(),
      'card-topics'
    ));
  }

  function createSummaryCard(title, value, className) {
    var card = document.createElement('div');
    card.className = 'summary-card ' + className;

    var titleEl = document.createElement('div');
    titleEl.className = 'summary-card-title';
    titleEl.textContent = title;

    var valueEl = document.createElement('div');
    valueEl.className = 'summary-card-value';
    valueEl.textContent = value;

    card.appendChild(titleEl);
    card.appendChild(valueEl);
    return card;
  }

  function renderHistoryTable(activity) {
    var tbody = els['progress-history-body'];
    tbody.innerHTML = '';

    if (!activity || activity.length === 0) {
      var row = document.createElement('tr');
      var cell = document.createElement('td');
      cell.colSpan = 4;
      cell.className = 'muted';
      cell.textContent = 'No recent activity.';
      row.appendChild(cell);
      tbody.appendChild(row);
      return;
    }

    activity.forEach(function (item) {
      var row = document.createElement('tr');

      var topicCell = document.createElement('td');
      topicCell.textContent = item.topic;
      topicCell.className = 'history-topic';

      var scoreCell = document.createElement('td');
      scoreCell.textContent = item.percentage + UI.percent;
      scoreCell.className = 'history-score';

      var dateCell = document.createElement('td');
      dateCell.textContent = item.completedAt;
      dateCell.className = 'history-date muted';

      var statusCell = document.createElement('td');
      var statusBadge = document.createElement('span');
      statusBadge.className = 'status-badge status-' + getStatusClass(item.status);
      statusBadge.textContent = item.status;
      statusCell.appendChild(statusBadge);

      row.appendChild(topicCell);
      row.appendChild(scoreCell);
      row.appendChild(dateCell);
      row.appendChild(statusCell);
      tbody.appendChild(row);
    });
  }

  function getStatusClass(status) {
    if (status === UI.strong) return 'strong';
    if (status === UI.good) return 'good';
    if (status === UI.needsRevisionStatus) return 'needs-revision';
    return 'weak';
  }

  function renderWeakAreas(weakConcepts) {
    var list = els['progress-weak-list'];
    list.innerHTML = '';

    if (!weakConcepts || weakConcepts.length === 0) {
      var li = document.createElement('li');
      li.className = 'muted';
      li.textContent = UI.noWeakAreas;
      list.appendChild(li);
      return;
    }

    weakConcepts.forEach(function (concept) {
      var li = document.createElement('li');
      li.textContent = concept;
      list.appendChild(li);
    });
  }

  function renderStrengths(strengths) {
    var list = els['progress-strengths-list'];
    list.innerHTML = '';

    if (!strengths || strengths.length === 0) {
      var li = document.createElement('li');
      li.className = 'muted';
      li.textContent = 'Complete more lessons to identify your strengths.';
      list.appendChild(li);
      return;
    }

    strengths.forEach(function (concept) {
      var li = document.createElement('li');
      li.textContent = concept;
      list.appendChild(li);
    });
  }

  function renderRecommendations(recommendations) {
    var list = els['progress-recommendations-list'];
    list.innerHTML = '';

    if (!recommendations || recommendations.length === 0) {
      var p = document.createElement('p');
      p.className = 'muted';
      p.textContent = 'No specific recommendations yet.';
      list.appendChild(p);
      return;
    }

    recommendations.forEach(function (rec) {
      var div = document.createElement('div');
      div.className = 'recommendation-card';
      div.textContent = rec;
      list.appendChild(div);
    });
  }

  function renderNextTopic(nextTopic) {
    var textEl = els['progress-next-topic-text'];
    if (nextTopic && nextTopic.trim().length > 0) {
      textEl.textContent = nextTopic;
    } else {
      textEl.textContent = UI.noNextTopic;
      textEl.classList.add('muted');
    }
  }

  /* ======================== Screen Management ======================== */

  function showDashboard() {
    hideAllScreens();
    els['progressDashboard'].classList.remove('hidden');
    els['progressDashboard'].scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function hideDashboard() {
    els['progressDashboard'].classList.add('hidden');
  }

  function hideAllScreens() {
    var profileForm = $('profileForm');
    if (profileForm) profileForm.classList.add('hidden');
    var teachingScreen = $('teachingScreen');
    if (teachingScreen) teachingScreen.classList.add('hidden');
    var assessmentScreen = $('assessmentScreen');
    if (assessmentScreen) assessmentScreen.classList.add('hidden');
    var reportScreen = $('reportScreen');
    if (reportScreen) reportScreen.classList.add('hidden');
    hideDashboard();
  }

  /* ======================== Save Progress ======================== */

  async function saveProgress(profileData, assessmentResult) {
    if (!profileData || !assessmentResult) return;

    var requestBody = {
      studentName: profileData.name,
      topic: profileData.topic || '',
      language: profileData.language || 'English',
      educationalLevel: profileData.educationLevel || '',
      teachingStyle: profileData.teachingStyle || '',
      learningObjective: profileData.objective || '',
      score: assessmentResult.score || 0,
      totalQuestions: assessmentResult.total || 0,
      percentage: assessmentResult.percentage || 0,
      conceptsUnderstood: assessmentResult.conceptsUnderstood || [],
      weakAreas: assessmentResult.weakAreas || [],
      incorrectConcepts: assessmentResult.incorrectConcepts || [],
      revisionRecommendations: (assessmentResult.revisionRecommendations || []).map(function (r) {
        return r.concept + ': ' + r.recommendation;
      }),
      suggestedNextTopic: assessmentResult.suggestedNextTopic || ''
    };

    try {
      var res = await fetch('/api/progress', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!res.ok) {
        console.warn('Failed to save learning session');
        return null;
      }

      return await res.json();
    } catch (err) {
      console.warn('Failed to save learning session:', err);
      return null;
    }
  }

  /* ======================== Public API ==================================== */

  function init(name) {
    cacheEls();
    loadProgress(name);
  }

  function save(profileData, assessmentResult) {
    return saveProgress(profileData, assessmentResult);
  }

  return {
    init: init,
    save: save
  };
})();
