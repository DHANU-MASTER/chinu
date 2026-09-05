/* ==========================================================================
   AI TEACHER — Phase 10: Lesson Video Recording
   Records the ACTUAL user-generated lesson presentation to a playable video
   using only browser-native APIs:

     - a composite <canvas> re-renders the live teaching state every frame
       (avatar state, lesson/section title, explanation, subtitles, progress)
     - canvas.captureStream(30) + MediaRecorder capture it to a real file
     - the result is previewable and downloadable, named after the topic

   Nothing here is faked: the recording shows the real lesson content that is
   currently on screen. Browser speech-synthesis audio cannot be captured by
   MediaRecorder, so the recording is visual (avatar + lesson visuals +
   subtitles) and the limitation is stated clearly — no fake audio is ever
   added.
   ========================================================================== */
window.AiTeacherRecorder = (function () {
  'use strict';

  var UI = {
    ready: 'Ready to record the teacher presentation.',
    recording: 'Recording…',
    paused: 'Paused — tap Resume to continue.',
    stopped: 'Recording complete.',
    unsupported: 'Video recording is not supported in this browser. Your lesson still works — only recording is unavailable.',
    failed: 'Recording failed in this browser. Please try again.',
    start: '🎥 Start Recording',
    pause: '⏸ Pause',
    resume: '▶ Resume',
    stop: '⏹ Stop',
    again: '↺ Record Again',
    download: '⬇ Download Video',
    timerIdle: '00:00'
  };

  var CANVAS_W = 1280;
  var CANVAS_H = 720;

  var els = {};
  var lesson = null;
  var topicSlug = 'lesson';
  var active = false;       // lesson active → live preview loop runs
  var recording = false;    // MediaRecorder running
  var paused = false;
  var recorder = null;
  var stream = null;
  var chunks = [];
  var rafId = null;
  var timerId = null;
  var startedAt = 0;
  var pausedAccumMs = 0;
  var pauseStartedAt = 0;
  var mimeType = '';
  var lastBlob = null;
  var lastUrl = null;
  var fileName = '';
  var lastDraw = 0;

  function $(id) {
    return document.getElementById(id);
  }

  function cacheEls() {
    ['recordingScreen', 'rec-canvas', 'rec-preview-wrap', 'rec-preview',
      'rec-start', 'rec-pause', 'rec-stop', 'rec-download', 'rec-again',
      'rec-timer', 'rec-status', 'rec-file-name', 'rec-unsupported'
    ].forEach(function (id) {
      els[id] = $(id);
    });
  }

  /* ======================= Support detection ======================= */

  function isSupported() {
    var canvas = document.createElement('canvas');
    return !!(window.MediaRecorder && canvas.captureStream
      && typeof canvas.captureStream === 'function');
  }

  /* ======================= Lesson lifecycle ======================= */

  /** Called when a lesson is loaded: shows the panel and starts the live preview. */
  function beginLesson(plan, profileData) {
    cacheEls();
    lesson = plan || null;
    topicSlug = slug((plan && (plan.topic || plan.lessonTitle)) || 'lesson');

    if (!isSupported()) {
      if (els['recordingScreen']) {
        els['recordingScreen'].classList.remove('hidden');
        if (els['rec-start']) { els['rec-start'].disabled = true; }
        showUnsupported(UI.unsupported);
      }
      return;
    }

    resetRecordingState();
    els['recordingScreen'].classList.remove('hidden');
    els['rec-unsupported'].classList.add('hidden');
    updateStatus(UI.ready);
    startPreviewLoop();
  }

  /** Called when the lesson is abandoned (edit profile / new lesson). */
  function endLesson() {
    stopRecordingSilently();
    stopPreviewLoop();
    releasePreview();
    active = false;
    lesson = null;
    if (els['recordingScreen']) {
      els['recordingScreen'].classList.add('hidden');
    }
  }

  /* ======================= Live preview loop ======================= */

  function startPreviewLoop() {
    active = true;
    stopPreviewLoop();
    function draw(now) {
      if (!active) { return; }
      lastDraw = now;
      renderFrame(now);
      rafId = requestAnimationFrame(draw);
    }
    rafId = requestAnimationFrame(draw);
  }

  function stopPreviewLoop() {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
  }

  /* ======================= Composite canvas rendering ======================= */

  /** Pulls the current live teaching state out of the actual UI elements. */
  function readState() {
    var avatar = $('ts-avatar');
    var title = $('ts-title');
    var sectionTitle = $('ts-section-title');
    var bodyText = $('ts-text');
    var subtitle = $('ts-subtitle');
    var bubble = $('ts-bubble');
    var progressBar = $('ts-progress-bar');
    var motif = $('ts-motif');

    var hint = 'generic';
    if (motif) {
      var m = motif.className.match(/motif-([a-z]+)/);
      if (m) { hint = m[1]; }
    }

    var progress = 0;
    if (progressBar && progressBar.style.width) {
      progress = parseFloat(progressBar.style.width) || 0;
    }

    return {
      avatarState: avatar ? (avatar.getAttribute('data-state') || 'idle') : 'idle',
      lessonTitle: (title && title.textContent) || (lesson && lesson.lessonTitle) || 'AI Teacher',
      sectionTitle: (sectionTitle && sectionTitle.textContent) || '',
      bodyText: (bodyText && bodyText.textContent) || '',
      subtitle: (subtitle && subtitle.textContent) || '',
      bubble: (bubble && bubble.textContent) || '',
      progress: Math.min(100, Math.max(0, progress)),
      hint: hint
    };
  }

  function renderFrame(now) {
    var canvas = els['rec-canvas'];
    if (!canvas) { return; }
    var ctx = canvas.getContext('2d');
    var s = readState();

    // Background
    var bg = ctx.createLinearGradient(0, 0, 0, CANVAS_H);
    bg.addColorStop(0, '#0d1428');
    bg.addColorStop(0.6, '#141a38');
    bg.addColorStop(1, '#1c1440');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    drawHeader(ctx, s);
    drawAvatar(ctx, s, now);
    drawBody(ctx, s);
    drawSubtitle(ctx, s);
    drawProgress(ctx, s);

    if (recording && !paused) {
      drawRecBadge(ctx, now);
    } else if (paused) {
      drawPausedOverlay(ctx);
    }
  }

  function drawHeader(ctx, s) {
    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.06)';
    ctx.fillRect(0, 0, CANVAS_W, 84);

    ctx.font = '600 30px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = '#31d0ff';
    ctx.textBaseline = 'middle';
    ctx.fillText('🧑\u200d🏫', 28, 44);
    ctx.fillStyle = '#ffffff';
    ctx.fillText('AI Teacher', 84, 44);

    ctx.font = '600 24px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.85)';
    var title = truncate(s.lessonTitle, 52);
    ctx.fillText(title, CANVAS_W - 28 - ctx.measureText(title).width, 44);

    ctx.restore();
  }

  function drawAvatar(ctx, s, now) {
    var cx = 180;
    var cy = 330;
    var pulse = 0;
    if (s.avatarState === 'teaching' || (recording && !paused)) {
      pulse = Math.sin(now / 300) * 0.035;
    }
    var dim = s.avatarState === 'idle' && !recording ? 0.75 : 1;

    ctx.save();
    ctx.globalAlpha = dim;
    ctx.translate(cx, cy);

    // body
    ctx.fillStyle = '#7c6cff';
    ctx.beginPath();
    ctx.moveTo(-88, 150);
    ctx.quadraticCurveTo(-40, 40, 0, 40);
    ctx.quadraticCurveTo(40, 40, 88, 150);
    ctx.closePath();
    ctx.fill();

    // head
    var r = 62 * (1 + pulse);
    ctx.fillStyle = '#31d0ff';
    ctx.beginPath();
    ctx.arc(0, -70, r, 0, Math.PI * 2);
    ctx.fill();

    // eyes
    ctx.fillStyle = '#0d1428';
    ctx.beginPath();
    ctx.arc(-20, -78, 7, 0, Math.PI * 2);
    ctx.arc(20, -78, 7, 0, Math.PI * 2);
    ctx.fill();

    // smile
    ctx.strokeStyle = '#0d1428';
    ctx.lineWidth = 5;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.arc(0, -52, 24, 0.15 * Math.PI, 0.85 * Math.PI);
    ctx.stroke();

    ctx.restore();

    // speech bubble
    var bubbleText = (s.bubble || 'Teaching…').trim();
    if (bubbleText.length > 40) { bubbleText = bubbleText.substring(0, 40) + '…'; }
    ctx.save();
    ctx.font = '600 20px "Segoe UI", system-ui, sans-serif';
    var bw = ctx.measureText(bubbleText).width + 40;
    var bx = cx - bw / 2;
    var by = 175;
    ctx.fillStyle = 'rgba(49,208,255,0.16)';
    ctx.strokeStyle = 'rgba(49,208,255,0.5)';
    ctx.lineWidth = 2;
    roundRect(ctx, bx, by, bw, 44, 22);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = '#e8f6ff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(bubbleText, cx, by + 22);
    ctx.textAlign = 'left';
    ctx.restore();
  }

  function drawBody(ctx, s) {
    var x = 360;
    var y = 140;
    var w = CANVAS_W - x - 48;

    ctx.save();
    // section title
    ctx.font = '700 34px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = '#ffffff';
    var titleLines = wrapText(ctx, s.sectionTitle || 'Lesson', w);
    titleLines.slice(0, 2).forEach(function (line, i) {
      ctx.fillText(line, x, y + i * 46);
    });
    var titleBottom = y + titleLines.slice(0, 2).length * 46 + 10;

    // divider
    ctx.strokeStyle = 'rgba(124,108,255,0.55)';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(x, titleBottom);
    ctx.lineTo(CANVAS_W - 48, titleBottom);
    ctx.stroke();

    // body text
    ctx.font = '24px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = 'rgba(235,242,255,0.92)';
    var bodyLines = wrapText(ctx, s.bodyText || '', w);
    var lineH = 34;
    var maxLines = 9;
    bodyLines.slice(0, maxLines).forEach(function (line, i) {
      ctx.fillText(line, x, titleBottom + 44 + i * lineH);
    });

    ctx.restore();
  }

  function drawSubtitle(ctx, s) {
    ctx.save();
    ctx.fillStyle = 'rgba(8,12,26,0.88)';
    ctx.fillRect(0, CANVAS_H - 150, CANVAS_W, 150);
    ctx.strokeStyle = 'rgba(49,208,255,0.35)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, CANVAS_H - 150);
    ctx.lineTo(CANVAS_W, CANVAS_H - 150);
    ctx.stroke();

    if (s.subtitle) {
      ctx.font = '24px "Segoe UI", system-ui, sans-serif';
      ctx.fillStyle = '#dceeff';
      var lines = wrapText(ctx, s.subtitle, CANVAS_W - 120);
      lines.slice(0, 3).forEach(function (line, i) {
        ctx.fillText(line, 60, CANVAS_H - 104 + i * 32);
      });
    } else {
      ctx.font = 'italic 22px "Segoe UI", system-ui, sans-serif';
      ctx.fillStyle = 'rgba(255,255,255,0.4)';
      ctx.fillText('Subtitles appear here while the teacher speaks.', 60, CANVAS_H - 96);
    }
    ctx.restore();
  }

  function drawProgress(ctx, s) {
    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.12)';
    ctx.fillRect(0, CANVAS_H - 10, CANVAS_W, 10);
    var grad = ctx.createLinearGradient(0, 0, CANVAS_W, 0);
    grad.addColorStop(0, '#5c64ff');
    grad.addColorStop(1, '#31d0ff');
    ctx.fillStyle = grad;
    ctx.fillRect(0, CANVAS_H - 10, CANVAS_W * s.progress / 100, 10);
    ctx.restore();
  }

  function drawRecBadge(ctx, now) {
    ctx.save();
    var blink = Math.floor(now / 500) % 2 === 0;
    if (blink) {
      ctx.fillStyle = '#ff4757';
      ctx.beginPath();
      ctx.arc(60, 30, 10, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.font = '700 26px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = '#ff6b7a';
    ctx.textAlign = 'center';
    ctx.fillText('● REC ' + formatTimer(elapsedMs()), 180, 38);
    ctx.textAlign = 'left';
    ctx.restore();
  }

  function drawPausedOverlay(ctx) {
    ctx.save();
    ctx.fillStyle = 'rgba(8,12,26,0.55)';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    ctx.font = '700 46px "Segoe UI", system-ui, sans-serif';
    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('⏸ Paused', CANVAS_W / 2, CANVAS_H / 2);
    ctx.restore();
  }

  /* ======================= Canvas text helpers ======================= */

  function wrapText(ctx, text, maxWidth) {
    var words = String(text).split(/\s+/);
    var lines = [];
    var current = '';
    for (var i = 0; i < words.length; i++) {
      var test = current ? current + ' ' + words[i] : words[i];
      if (ctx.measureText(test).width > maxWidth && current) {
        lines.push(current);
        current = words[i];
      } else {
        current = test;
      }
    }
    if (current) { lines.push(current); }
    return lines;
  }

  function truncate(text, maxChars) {
    if (!text) { return ''; }
    return text.length > maxChars ? text.substring(0, maxChars - 1) + '…' : text;
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /* ======================= Recording ======================= */

  function pickMimeType() {
    var candidates = [
      'video/webm;codecs=vp9',
      'video/webm;codecs=vp8',
      'video/webm',
      'video/mp4'
    ];
    for (var i = 0; i < candidates.length; i++) {
      if (window.MediaRecorder.isTypeSupported(candidates[i])) {
        return candidates[i];
      }
    }
    return '';
  }

  function startRecording() {
    if (recording || !els['rec-canvas']) { return; }
    try {
      stream = els['rec-canvas'].captureStream(30);
      mimeType = pickMimeType();
      recorder = new MediaRecorder(stream, mimeType ? { mimeType: mimeType } : undefined);
      chunks = [];
      recorder.ondataavailable = function (e) {
        if (e.data && e.data.size > 0) { chunks.push(e.data); }
      };
      recorder.onstop = finalizeRecording;
      recorder.onerror = function () {
        recordingError(UI.failed);
      };
      recorder.start(250);
      recording = true;
      paused = false;
      startedAt = Date.now();
      pausedAccumMs = 0;
      pauseStartedAt = 0;
      updateStatus(UI.recording);
      updateControls();
      startTimer();
    } catch (err) {
      recordingError(UI.failed + (err && err.message ? ' (' + err.message + ')' : ''));
    }
  }

  function togglePause() {
    if (!recording || !recorder) { return; }
    if (paused) {
      recorder.resume();
      paused = false;
      pausedAccumMs += Date.now() - pauseStartedAt;
      pauseStartedAt = 0;
      updateStatus(UI.recording);
    } else {
      recorder.pause();
      paused = true;
      pauseStartedAt = Date.now();
      updateStatus(UI.paused);
    }
    updateControls();
  }

  function stopRecording() {
    if (!recording) { return; }
    stopTimer();
    var r = recorder;
    var st = stream;
    recording = false;
    paused = false;
    recorder = null;
    stream = null;
    if (r) {
      try { r.stop(); } catch (e) { /* already stopped */ }
    }
    if (st) {
      st.getTracks().forEach(function (t) { t.stop(); });
    }
    updateControls();
  }

  function stopRecordingSilently() {
    if (!recording) { return; }
    stopTimer();
    var r = recorder;
    var st = stream;
    recording = false;
    paused = false;
    recorder = null;
    stream = null;
    if (r) {
      try { r.stop(); } catch (e) { /* ignore */ }
    }
    if (st) {
      st.getTracks().forEach(function (t) { t.stop(); });
    }
  }

  function finalizeRecording() {
    var isMp4 = mimeType && mimeType.indexOf('mp4') >= 0;
    var type = isMp4 ? 'video/mp4' : 'video/webm';
    var ext = isMp4 ? 'mp4' : 'webm';

    lastBlob = new Blob(chunks, { type: type });
    chunks = [];

    if (lastUrl) { URL.revokeObjectURL(lastUrl); }
    lastUrl = URL.createObjectURL(lastBlob);

    fileName = 'ai-teacher_' + topicSlug + '_' + timestamp() + '.' + ext;

    if (els['rec-preview']) {
      els['rec-preview'].src = lastUrl;
      els['rec-preview-wrap'].classList.remove('hidden');
    }
    if (els['rec-canvas']) {
      els['rec-canvas'].classList.add('hidden');
    }
    if (els['rec-file-name']) {
      els['rec-file-name'].textContent = fileName + ' — ' + (lastBlob.size / 1024).toFixed(0) + ' KB';
    }
    updateStatus(UI.stopped);
    updateControls();
  }

  function downloadVideo() {
    if (!lastBlob || !lastUrl) { return; }
    var a = document.createElement('a');
    a.href = lastUrl;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  function recordAgain() {
    releasePreview();
    resetRecordingState();
    updateStatus(UI.ready);
  }

  function releasePreview() {
    if (els['rec-preview']) {
      els['rec-preview'].removeAttribute('src');
      els['rec-preview-wrap'].classList.add('hidden');
    }
    if (lastUrl) {
      URL.revokeObjectURL(lastUrl);
      lastUrl = null;
    }
    lastBlob = null;
    fileName = '';
    if (els['rec-file-name']) { els['rec-file-name'].textContent = ''; }
  }

  function resetRecordingState() {
    recording = false;
    paused = false;
    recorder = null;
    stream = null;
    chunks = [];
    mimeType = '';
    startedAt = 0;
    pausedAccumMs = 0;
    pauseStartedAt = 0;
    stopTimer();
    if (els['rec-timer']) { els['rec-timer'].textContent = UI.timerIdle; }
    if (els['rec-canvas']) { els['rec-canvas'].classList.remove('hidden'); }
    releasePreview();
    updateControls();
  }

  /* ======================= Timer ======================= */

  function elapsedMs() {
    if (!startedAt) { return 0; }
    var total = Date.now() - startedAt - pausedAccumMs;
    return paused && pauseStartedAt ? total - (Date.now() - pauseStartedAt) : total;
  }

  function startTimer() {
    stopTimer();
    timerId = setInterval(function () {
      if (els['rec-timer']) { els['rec-timer'].textContent = formatTimer(elapsedMs()); }
    }, 250);
  }

  function stopTimer() {
    if (timerId) {
      clearInterval(timerId);
      timerId = null;
    }
  }

  function formatTimer(ms) {
    var totalSec = Math.max(0, Math.floor(ms / 1000));
    var m = Math.floor(totalSec / 60);
    var s = totalSec % 60;
    return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
  }

  /* ======================= UI helpers ======================= */

  function updateStatus(text) {
    if (els['rec-status']) { els['rec-status'].textContent = text; }
  }

  function showUnsupported(message) {
    if (els['rec-unsupported']) {
      els['rec-unsupported'].textContent = message;
      els['rec-unsupported'].classList.remove('hidden');
    }
  }

  function recordingError(message) {
    recording = false;
    paused = false;
    stopTimer();
    updateControls();
    showUnsupported(message);
  }

  function updateControls() {
    if (!els['rec-start']) { return; }
    els['rec-start'].disabled = recording;
    els['rec-pause'].disabled = !recording;
    els['rec-stop'].disabled = !recording;
    els['rec-download'].disabled = !lastBlob;
    els['rec-again'].disabled = !lastBlob;
    els['rec-pause'].textContent = paused ? UI.resume : UI.pause;
  }

  /* ======================= Misc helpers ======================= */

  function slug(text) {
    var s = String(text || '').toLowerCase().replace(/[^a-z0-9]+/g, '-');
    s = s.replace(/^-+|-+$/g, '');
    return s.substring(0, 48) || 'lesson';
  }

  function timestamp() {
    var d = new Date();
    function pad(n) { return (n < 10 ? '0' : '') + n; }
    return d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + '-' +
      pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
  }

  /* ======================= Wire up controls ======================= */

  function init() {
    cacheEls();
    if (!els['rec-start']) { return; }
    els['rec-start'].addEventListener('click', startRecording);
    els['rec-pause'].addEventListener('click', togglePause);
    els['rec-stop'].addEventListener('click', stopRecording);
    els['rec-download'].addEventListener('click', downloadVideo);
    els['rec-again'].addEventListener('click', recordAgain);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  /* ======================= Public API ======================= */

  function state() {
    return {
      active: active,
      recording: recording,
      paused: paused,
      mimeType: mimeType,
      fileName: fileName,
      blobSize: lastBlob ? lastBlob.size : 0,
      elapsed: formatTimer(elapsedMs())
    };
  }

  return {
    beginLesson: beginLesson,
    endLesson: endLesson,
    isSupported: isSupported,
    state: state
  };
})();