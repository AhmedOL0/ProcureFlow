import { expect, test } from '@playwright/test';

/**
 * Auth shell without a backend: routing, guards and form states are all
 * reachable; API-backed success needs the backend up (covered by backend
 * AuthFlowIT instead).
 */
test('guests land on the login screen', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByText('Sign in to ProcureFlow')).toBeVisible();
});

test('guests cannot open suppliers directly', async ({ page }) => {
  await page.goto('/suppliers');
  await expect(page).toHaveURL(/\/login/);
});

test('guests cannot open requests directly', async ({ page }) => {
  await page.goto('/requests');
  await expect(page).toHaveURL(/\/login/);
});

test('guests cannot open operations directly', async ({ page }) => {
  for (const path of ['/orders', '/invoices', '/admin', '/admin/audit', '/approvals']) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login/);
  }
});

test('empty login shows validation, not a request', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email is required.')).toBeVisible();
  await expect(page.getByText('Password is required.')).toBeVisible();
});

test('unreachable backend surfaces an error state', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('boss@acme.test');
  await page.getByLabel('Password').fill('correct-horse-123');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toContainText('Invalid email or password.');
});

test('register screen explains the workspace model', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create a workspace' }).click();
  await expect(page).toHaveURL(/\/register/);
  await expect(page.getByText('Create your workspace')).toBeVisible();
  await expect(page.getByLabel('Workspace name (for new workspaces)')).toBeVisible();
});
