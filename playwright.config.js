// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Playwright E2E configuration for AI Teacher.
 * Boots the real Spring Boot app (webServer) and runs browser flows against it.
 * The AI-free specs (landing, demo mode, auth UI, i18n, PWA assets) run without
 * an AI_API_KEY; full lesson E2E requires one set in the environment.
 */
module.exports = defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://127.0.0.1:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
  ],
  webServer: {
    command: process.platform === 'win32'
      ? `cmd /c "${require('path').join(__dirname, 'mvnw.cmd')}" -q spring-boot:run`
      : './mvnw -q spring-boot:run',
    url: 'http://127.0.0.1:8080/index.html',
    reuseExistingServer: true,
    timeout: 240_000
  }
});
