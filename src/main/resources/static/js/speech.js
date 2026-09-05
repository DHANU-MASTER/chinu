/* ==========================================================================
   AI TEACHER — Phase 9: SpeechService
   Browser-native Text-to-Speech using the Web Speech API.
   Narrates the ACTUAL lesson content generated from the student's profile.
   Supports English, Hindi, and Kannada with appropriate browser voices.
   ========================================================================== */
window.AiTeacherSpeech = (function () {
  'use strict';

  /* ----------------------- Language locale mapping ----------------------- */
  var LOCALE_MAP = {
    'english': 'en-IN',
    'hindi': 'hi-IN',
    'kannada': 'kn-IN'
  };

  var DEFAULT_LOCALE = 'en-US';

  /* ----------------------- State ----------------------- */
  var synth = window.speechSynthesis || null;
  var currentUtterance = null;
  var currentText = '';
  var currentLang = 'English';
  var currentRate = 1.0;
  var isPaused = false;
  var isSpeaking = false;
  var availableVoices = [];
  var selectedVoice = null;
  var onStateChange = null;  // callback: function(state) where state is 'idle'|'speaking'|'paused'
  var onBoundary = null;     // callback: function(charIndex, charLength) for subtitle sync

  /* ----------------------- Initialization ----------------------- */

  function init() {
    if (!synth) {
      console.warn('SpeechSynthesis not supported in this browser.');
      return false;
    }

    // Reset any pending queue from previous page reloads
    try { synth.cancel(); } catch (e) { /* ignore */ }

    // Load voices (Chrome loads them asynchronously)
    loadVoices();
    if (synth.onvoiceschanged !== undefined) {
      synth.onvoiceschanged = loadVoices;
    }

    // Fallback timer for browsers where voiceschanged fires late
    setTimeout(loadVoices, 200);
    setTimeout(loadVoices, 800);

    return true;
  }

  function loadVoices() {
    if (!synth) return;
    var voices = synth.getVoices();
    if (voices && voices.length > 0) {
      availableVoices = voices;
      selectVoiceForLanguage(currentLang);
    }
  }

  function selectVoiceForLanguage(language) {
    if (!availableVoices || availableVoices.length === 0) {
      if (synth) { availableVoices = synth.getVoices() || []; }
    }

    var locale = LOCALE_MAP[String(language).toLowerCase()] || DEFAULT_LOCALE;
    var langPrefix = locale.split('-')[0];

    selectedVoice = null;

    // Priority 1: exact locale match (e.g. en-IN, hi-IN, kn-IN)
    for (var i = 0; i < availableVoices.length; i++) {
      if (availableVoices[i].lang === locale) {
        selectedVoice = availableVoices[i];
        break;
      }
    }

    // Priority 2: language prefix match (e.g. en, hi, kn)
    if (!selectedVoice) {
      for (var i = 0; i < availableVoices.length; i++) {
        if (availableVoices[i].lang && availableVoices[i].lang.toLowerCase().startsWith(langPrefix)) {
          selectedVoice = availableVoices[i];
          break;
        }
      }
    }

    // Priority 3: Fallback to first available system voice
    if (!selectedVoice && availableVoices.length > 0) {
      selectedVoice = availableVoices[0];
    }

    return selectedVoice;
  }

  /* ----------------------- Core API ----------------------- */

  /**
   * Speak the given text. The text comes from the actual lesson content.
   */
  function speak(text, language, options) {
    if (!synth) return false;
    if (!text || text.trim().length === 0) return false;

    // Force cancel & resume synth if stuck
    try {
      synth.cancel();
      if (synth.paused) { synth.resume(); }
    } catch (e) { /* ignore */ }

    currentText = text;
    currentLang = language || 'English';

    // Reload voices to ensure fresh availability
    loadVoices();
    selectVoiceForLanguage(currentLang);

    var utterance = new SpeechSynthesisUtterance(text);

    // Set voice
    if (selectedVoice) {
      utterance.voice = selectedVoice;
      utterance.lang = selectedVoice.lang;
    } else {
      utterance.lang = LOCALE_MAP[currentLang.toLowerCase()] || DEFAULT_LOCALE;
    }

    utterance.rate = options && options.rate ? options.rate : currentRate;
    utterance.pitch = 1.0;
    utterance.volume = 1.0;

    utterance.onstart = function () {
      isSpeaking = true;
      isPaused = false;
      notifyState('speaking');
    };

    utterance.onend = function () {
      isSpeaking = false;
      isPaused = false;
      currentUtterance = null;
      notifyState('idle');
      if (options && typeof options.onEnd === 'function') {
        options.onEnd();
      }
    };

    utterance.onerror = function (event) {
      if (event.error !== 'interrupted' && event.error !== 'canceled') {
        console.warn('Speech error:', event.error);
      }
      isSpeaking = false;
      isPaused = false;
      currentUtterance = null;
      notifyState('idle');
    };

    utterance.onpause = function () {
      isPaused = true;
      notifyState('paused');
    };

    utterance.onresume = function () {
      isPaused = false;
      isSpeaking = true;
      notifyState('speaking');
    };

    utterance.onboundary = function (event) {
      if (onBoundary && event.name === 'word') {
        onBoundary(event.charIndex, event.charLength);
      }
    };

    currentUtterance = utterance;

    // Direct invocation with Chrome fix
    setTimeout(function () {
      synth.speak(utterance);
    }, 10);

    return true;
  }

  function pause() {
    if (!synth) return;
    if (isSpeaking && !isPaused) {
      synth.pause();
    }
  }

  function resume() {
    if (!synth) return;
    if (isPaused) {
      synth.resume();
    } else {
      if (currentText) {
        speak(currentText, currentLang, { rate: currentRate });
      }
    }
  }

  function stop() {
    if (!synth) return;
    try { synth.cancel(); } catch (e) { /* ignore */ }
    isSpeaking = false;
    isPaused = false;
    currentUtterance = null;
    notifyState('idle');
  }

  function isSpeakingNow() {
    return isSpeaking && !isPaused;
  }

  function isPausedNow() {
    return isPaused;
  }

  function isSupported() {
    return !!synth;
  }

  function setRate(rate) {
    currentRate = rate;
    if (isSpeaking && currentText) {
      var text = currentText;
      var lang = currentLang;
      stop();
      setTimeout(function () {
        speak(text, lang, { rate: rate });
      }, 100);
    }
  }

  function getRate() {
    return currentRate;
  }

  function getAvailableRates() {
    return [
      { value: 0.8, label: '0.8x' },
      { value: 1.0, label: '1.0x' },
      { value: 1.2, label: '1.2x' }
    ];
  }

  function setOnStateChange(callback) {
    onStateChange = callback;
  }

  function setOnBoundary(callback) {
    onBoundary = callback;
  }

  function notifyState(state) {
    if (onStateChange) {
      onStateChange(state);
    }
  }

  function getCurrentVoice() {
    return selectedVoice ? selectedVoice.name : 'System Voice';
  }

  function isVoiceAvailable(language) {
    return true; // fallback system voice is always available
  }

  function getVoiceInfo() {
    if (!synth) return { supported: false };
    return {
      supported: true,
      voiceCount: availableVoices.length,
      currentVoice: getCurrentVoice(),
      currentLang: currentLang,
      currentRate: currentRate
    };
  }

  init();

  return {
    speak: speak,
    pause: pause,
    resume: resume,
    stop: stop,
    isSpeaking: isSpeakingNow,
    isPaused: isPausedNow,
    isSupported: isSupported,
    setRate: setRate,
    getRate: getRate,
    getAvailableRates: getAvailableRates,
    setOnStateChange: setOnStateChange,
    setOnBoundary: setOnBoundary,
    getCurrentVoice: getCurrentVoice,
    isVoiceAvailable: isVoiceAvailable,
    getVoiceInfo: getVoiceInfo
  };
})();
