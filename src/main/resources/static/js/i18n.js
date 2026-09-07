/**
 * AI Teacher — UI i18n.
 * Lightweight dictionary-based translation of the app chrome (labels, buttons,
 * headings). Lesson content is translated by the AI backend; this module covers
 * the static UI. Language persists in localStorage and follows the wizard's
 * Preferred Language selector.
 */
window.AiTeacherI18N = (function () {
  'use strict';

  var STORAGE_KEY = 'ai_teacher_ui_lang';

  var DICT = {
    en: {
      welcomeTitle: 'Welcome to AI Teacher',
      welcomeSub: 'Sign in to access your personalized learning portal',
      signIn: 'Sign In',
      createAccount: 'Create Account',
      email: 'Email Address *',
      password: 'Password *',
      fullName: 'Full Name *',
      signInBtn: 'Sign In to Learn',
      registerBtn: 'Register Account',
      demoBtn: 'Try a Demo Lesson — no account needed',
      chooseAvatar: 'Choose Your Avatar / Profile Pic',
      timetable: 'Timetable',
      myHistory: 'My History',
      logOut: 'Log Out',
      setup: 'Setup',
      lesson: 'Lesson',
      assessment: 'Assessment',
      report: 'Report',
      playVoice: 'Play Voice',
      pauseVoice: 'Pause',
      stop: 'Stop',
      auto: 'Auto',
      askPlaceholder: 'Ask your teacher anything about this section…',
      askBtn: 'Ask',
      wizardTitle: 'Set Up Your Personalized Lesson',
      wizardSub: 'Select your AI Teacher Persona & learning goals',
      step1: 'Teacher & Profile',
      step2: 'Learning Goals',
      nextGoals: 'Next: Learning Goals',
      startLesson: 'Start Personalized Lesson'
    },
    hi: {
      welcomeTitle: 'AI टीचर में आपका स्वागत है',
      welcomeSub: 'अपना व्यक्तिगत लर्निंग पोर्टल खोलने के लिए साइन इन करें',
      signIn: 'साइन इन',
      createAccount: 'खाता बनाएँ',
      email: 'ईमेल पता *',
      password: 'पासवर्ड *',
      fullName: 'पूरा नाम *',
      signInBtn: 'सीखना शुरू करें',
      registerBtn: 'खाता पंजीकृत करें',
      demoBtn: 'डेमो पाठ आज़माएँ — खाता ज़रूरी नहीं',
      chooseAvatar: 'अपना अवतार / प्रोफ़ाइल चित्र चुनें',
      timetable: 'समय-सारिणी',
      myHistory: 'मेरी प्रगति',
      logOut: 'लॉग आउट',
      setup: 'सेटअप',
      lesson: 'पाठ',
      assessment: 'मूल्यांकन',
      report: 'रिपोर्ट',
      playVoice: 'आवाज़ चलाएँ',
      pauseVoice: 'रोकें',
      stop: 'बंद करें',
      auto: 'ऑटो',
      askPlaceholder: 'इस अनुभाग के बारे में कुछ भी पूछें…',
      askBtn: 'पूछें',
      wizardTitle: 'अपना व्यक्तिगत पाठ सेट करें',
      wizardSub: 'अपनी AI टीचर पर्सोना और लक्ष्य चुनें',
      step1: 'शिक्षक और प्रोफ़ाइल',
      step2: 'सीखने के लक्ष्य',
      nextGoals: 'आगे: सीखने के लक्ष्य',
      startLesson: 'व्यक्तिगत पाठ शुरू करें'
    },
    kn: {
      welcomeTitle: 'AI ಟೀಚರ್‌ಗೆ ಸ್ವಾಗತ',
      welcomeSub: 'ನಿಮ್ಮ ವೈಯಕ್ತಿಕ ಕಲಿಕಾ ಪೋರ್ಟಲ್ ತೆರೆಯಲು ಸೈನ್ ಇನ್ ಮಾಡಿ',
      signIn: 'ಸೈನ್ ಇನ್',
      createAccount: 'ಖಾತೆ ತೆರೆಯಿರಿ',
      email: 'ಇಮೇಲ್ ವಿಳಾಸ *',
      password: 'ಪಾಸ್‌ವರ್ಡ್ *',
      fullName: 'ಪೂರ್ಣ ಹೆಸರು *',
      signInBtn: 'ಕಲಿಯಲು ಪ್ರಾರಂಭಿಸಿ',
      registerBtn: 'ಖಾತೆ ನೊಂದಾಯಿಸಿ',
      demoBtn: 'ಡೆಮೊ ಪಾಠ ಪ್ರಯತ್ನಿಸಿ — ಖಾತೆ ಅಗತ್ಯವಿಲ್ಲ',
      chooseAvatar: 'ನಿಮ್ಮ ಅವತಾರ / ಪ್ರೊಫೈಲ್ ಚಿತ್ರ ಆರಿಸಿ',
      timetable: 'ಸಮಯ ವೇಳಾಪಟ್ಟಿ',
      myHistory: 'ನನ್ನ ಪ್ರಗತಿ',
      logOut: 'ಲಾಗ್ ಔಟ್',
      setup: 'ಸೆಟಪ್',
      lesson: 'ಪಾಠ',
      assessment: 'ಮೌಲ್ಯಮಾಪನ',
      report: 'ವರದಿ',
      playVoice: 'ಧ್ವನಿ ಪ್ಲೇ ಮಾಡಿ',
      pauseVoice: 'ವಿರಾಮ',
      stop: 'ನಿಲ್ಲಿಸಿ',
      auto: 'ಸ್ವಯಂ',
      askPlaceholder: 'ಈ ವಿಭಾಗದ ಬಗ್ಗೆ ಏನನ್ನಾದರೂ ಕೇಳಿ…',
      askBtn: 'ಕೇಳಿ',
      wizardTitle: 'ನಿಮ್ಮ ವೈಯಕ್ತಿಕ ಪಾಠ ಹೊಂದಿಸಿ',
      wizardSub: 'ನಿಮ್ಮ AI ಟೀಚರ್ ಪರ್ಸೋನಾ ಮತ್ತು ಗುರಿಗಳನ್ನು ಆರಿಸಿ',
      step1: 'ಶಿಕ್ಷಕ ಮತ್ತು ಪ್ರೊಫೈಲ್',
      step2: 'ಕಲಿಕಾ ಗುರಿಗಳು',
      nextGoals: 'ಮುಂದೆ: ಕಲಿಕಾ ಗುರಿಗಳು',
      startLesson: 'ವೈಯಕ್ತಿಕ ಪಾಠ ಪ್ರಾರಂಭಿಸಿ'
    }
  };

  function currentLang() {
    return localStorage.getItem(STORAGE_KEY) || 'en';
  }

  function apply(lang) {
    var dict = DICT[lang] || DICT.en;
    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var key = el.getAttribute('data-i18n');
      if (dict[key] !== undefined) {
        el.textContent = dict[key];
      }
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(function (el) {
      var key = el.getAttribute('data-i18n-placeholder');
      if (dict[key] !== undefined) {
        el.setAttribute('placeholder', dict[key]);
      }
    });
    document.documentElement.setAttribute('data-ui-lang', lang);
    localStorage.setItem(STORAGE_KEY, lang);
  }

  function init() {
    apply(currentLang());
  }

  return {
    apply: apply,
    init: init,
    currentLang: currentLang,
    languages: Object.keys(DICT)
  };
})();
