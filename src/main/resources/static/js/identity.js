/**
 * AI Teacher — request identity.
 * Patches window.fetch once so every /api/ and /ws-adjacent call carries the
 * signed-in student's email in the X-Student-Email header. The backend uses it
 * for per-user AI rate limiting; it is not an authentication credential.
 * Must be loaded before any script that calls fetch().
 */
window.AiTeacherIdentity = (function () {
  'use strict';

  var KEY = 'ai_teacher_user';

  function currentUser() {
    try {
      return JSON.parse(localStorage.getItem(KEY) || 'null');
    } catch (e) {
      return null;
    }
  }

  function studentEmail() {
    var user = currentUser();
    return user && user.email ? String(user.email) : '';
  }

  if (!window.__aiTeacherFetchPatched) {
    window.__aiTeacherFetchPatched = true;
    var originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      try {
        var url = typeof input === 'string' ? input : (input && input.url) || '';
        if (url.indexOf('/api/') === 0 || url.indexOf('/api/') > -1) {
          var email = studentEmail();
          if (email) {
            init = init || {};
            var headers = new Headers((init && init.headers) || (input && input.headers) || {});
            headers.set('X-Student-Email', email);
            init.headers = headers;
          }
        }
      } catch (e) {
        /* identity is best-effort; never break a request over it */
      }
      return originalFetch(input, init);
    };
  }

  return { studentEmail: studentEmail };
})();
