/**
 * AI Teacher — voice input module.
 * Web Speech Recognition wrapper: students can answer questions by voice.
 * Zero dependencies; degrades gracefully when the browser lacks support.
 */
window.AiTeacherVoiceInput = (function () {
  'use strict';

  var recognition = null;
  var activeBtn = null;
  var SR = window.SpeechRecognition || window.webkitSpeechRecognition;

  function isSupported() {
    return !!SR;
  }

  /**
   * Attach voice capture to a button that fills a target input/textarea.
   * @param {Element} btnEl the mic button
   * @param {Element} targetEl the textarea/input to fill
   * @param {Object} [opts] { lang: string|function() } — BCP-47 tag or getter
   */
  function attach(btnEl, targetEl, opts) {
    if (!btnEl || !targetEl) return;
    btnEl.addEventListener('click', function () {
      if (!SR) {
        if (window.showToast) window.showToast('Voice input is not supported in this browser.', 'warn');
        return;
      }
      if (activeBtn === btnEl) {
        stop();
        return;
      }
      stop();
      start(btnEl, targetEl, opts);
    });
  }

  function start(btnEl, targetEl, opts) {
    recognition = new SR();
    var langOpt = opts && opts.lang;
    recognition.lang = typeof langOpt === 'function' ? (langOpt() || 'en-US') : (langOpt || 'en-US');
    recognition.interimResults = false;
    recognition.continuous = false;

    var base = targetEl.value ? targetEl.value.trim() + ' ' : '';

    recognition.onresult = function (event) {
      var text = '';
      for (var i = 0; i < event.results.length; i++) {
        text += event.results[i][0].transcript;
      }
      targetEl.value = (base + text).trim();
      targetEl.dispatchEvent(new Event('input', { bubbles: true }));
    };

    recognition.onend = function () {
      setActive(btnEl, false);
      activeBtn = null;
      recognition = null;
    };

    recognition.onerror = function () {
      setActive(btnEl, false);
      activeBtn = null;
      recognition = null;
      if (window.showToast) window.showToast('Could not hear you — please try again.', 'error');
    };

    try {
      recognition.start();
    } catch (e) {
      return;
    }
    setActive(btnEl, true);
    activeBtn = btnEl;
  }

  function stop() {
    if (recognition) {
      try { recognition.stop(); } catch (e) { /* ignore */ }
    }
    if (activeBtn) {
      setActive(activeBtn, false);
      activeBtn = null;
    }
  }

  function setActive(btn, on) {
    btn.classList.toggle('listening', on);
    btn.setAttribute('aria-pressed', on ? 'true' : 'false');
  }

  return {
    isSupported: isSupported,
    attach: attach,
    stop: stop
  };
})();
