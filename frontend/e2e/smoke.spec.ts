import { expect, test } from '@playwright/test';

test('app shell renders the product name', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/ProcureFlow/);
  await expect(page.getByRole('heading', { name: 'ProcureFlow' })).toBeVisible();
});
