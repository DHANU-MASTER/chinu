// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * AI-free end-to-end flows: these run without an AI_API_KEY because they only
 * exercise the landing page, demo mode, auth UI, i18n and PWA assets.
 */

test.describe('Landing page', () => {
  test('presents the product and links into the app', async ({ page }) => {
    await page.goto('/landing.html');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('personal AI teacher');
    await expect(page.getByRole('link', { name: /Try a Demo Lesson/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Create free account/i })).toBeVisible();
  });

  test('demo CTA deep-links into demo mode', async ({ page }) => {
    await page.goto('/landing.html');
    await page.getByRole('link', { name: /Try a Demo Lesson/i }).click();
    await page.waitForURL(/index\.html\?demo=1/);
    await expect(page.locator('#wizardScreen')).toBeVisible();
    await expect(page.locator('#authScreen')).toBeHidden();
  });
});

test.describe('Auth screen', () => {
  test('renders styled tabs, brand panel and demo CTA', async ({ page }) => {
    await page.goto('/index.html');
    await expect(page.locator('#authScreen .auth-brand')).toBeVisible();
    await expect(page.locator('#tabLoginBtn')).toBeVisible();
    await expect(page.locator('#tabRegisterBtn')).toBeVisible();
    await expect(page.locator('#btnDemoMode')).toBeVisible();
    // Tabs are a real segmented control, not default buttons
    const tabs = page.locator('.auth-tabs');
    await expect(tabs).toHaveCSS('display', 'grid');
  });

  test('register form enforces an 8-character password client-side', async ({ page }) => {
    await page.goto('/index.html');
    await page.locator('#tabRegisterBtn').click();
    await page.locator('#regName').fill('E2E Student');
    await page.locator('#regEmail').fill('e2e@example.com');
    await page.locator('#regPassword').fill('short');
    const passwordInput = page.locator('#regPassword');
    const validity = await passwordInput.evaluate((el) => el.validity.tooShort || el.getAttribute('minlength') === '8');
    expect(validity).toBeTruthy();
  });

  test('flow rail hides until a session exists', async ({ page }) => {
    await page.goto('/index.html');
    await expect(page.locator('#flowRail')).toBeHidden();
  });
});

test.describe('Demo mode', () => {
  test('enters the wizard and shows the flow rail', async ({ page }) => {
    await page.goto('/index.html');
    await page.locator('#btnDemoMode').click();
    await expect(page.locator('#wizardScreen')).toBeVisible();
    await expect(page.locator('#flowRail')).toBeVisible();
    await expect(page.locator('#flowRail .flow-step.active')).toContainText('Setup');
  });

  test('wizard step 1 shows persona cards and profile fields', async ({ page }) => {
    await page.goto('/index.html');
    await page.locator('#btnDemoMode').click();
    await expect(page.locator('.persona-cards-grid .persona-card')).toHaveCount(3);
    await expect(page.locator('#educationLevel')).toBeVisible();
    await expect(page.locator('#teachingStyle')).toBeVisible();
  });
});

test.describe('UI i18n', () => {
  test('switches the chrome to Hindi and back', async ({ page }) => {
    await page.goto('/index.html');
    const title = page.locator('[data-i18n="welcomeTitle"]');
    await expect(title).toHaveText('Welcome to AI Teacher');
    await page.evaluate(() => window.AiTeacherI18N.apply('hi'));
    await expect(title).toHaveText('AI टीचर में आपका स्वागत है');
    await page.evaluate(() => window.AiTeacherI18N.apply('en'));
    await expect(title).toHaveText('Welcome to AI Teacher');
  });
});

test.describe('PWA assets', () => {
  test('manifest, service worker and icons are served', async ({ request }) => {
    expect((await request.get('/manifest.webmanifest')).ok()).toBeTruthy();
    expect((await request.get('/sw.js')).ok()).toBeTruthy();
    expect((await request.get('/icon.svg')).ok()).toBeTruthy();
  });
});

test.describe('Ask the teacher (UI presence)', () => {
  test('ask input exists on the teaching stage markup', async ({ page }) => {
    await page.goto('/index.html');
    const askInput = page.locator('#ts-ask-input');
    await expect(askInput).toHaveCount(1);
    // It sits inside the (initially hidden) teaching screen until a lesson starts
    await expect(page.locator('#teachingScreen #ts-ask-send')).toHaveCount(1);
  });
});
