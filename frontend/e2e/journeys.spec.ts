import { expect, Page, test } from '@playwright/test';

/**
 * Critical-journey coverage with deterministic API doubles (page.route).
 * No live backend, no live Groq: every contract answer is canned, so these
 * run identically in CI and locally. Real-data journeys stay in the manual
 * live-verify loop with screenshots (see progress-tracker).
 */

const ALL_AUTHORITIES = [
  'tenant:admin',
  'tenant:manage',
  'user:manage',
  'department:manage',
  'supplier:read',
  'supplier:write',
  'procurement:request',
  'procurement:approve',
  'budget:read',
  'budget:manage',
  'order:read',
  'order:write',
  'invoice:read',
  'invoice:write',
  'analytics:read',
  'ai:use',
  'audit:read',
];

function fakeJwt(): string {
  const b64 = (value: object): string => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${b64({ alg: 'HS512' })}.${b64({ authorities: ALL_AUTHORITIES })}.sig`;
}

const ME = {
  id: 'u-admin',
  email: 'boss@acme.test',
  firstName: '',
  lastName: '',
  status: 'ACTIVE',
  tenantSlug: 'acme',
  roles: ['TENANT_ADMIN'],
};

interface Stub {
  method: string;
  match: string;
  status?: number;
  body?: unknown;
  handle?: (url: string, payload: Record<string, unknown>) => { status: number; body: unknown };
}

/** Directory endpoints answer paged envelopes; tests wrap their live arrays. */
function asPage(rows: Record<string, unknown>[]) {
  return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 };
}

async function stubApi(page: Page, stubs: Stub[]): Promise<void> {
  const ordered = [...stubs].sort((a, b) => b.match.length - a.match.length);
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const found = ordered.find(
      (stub) => stub.method === request.method() && request.url().includes(stub.match),
    );
    if (!found) {
      await route.abort('failed');
      return;
    }
    if (found.handle) {
      let payload: Record<string, unknown> = {};
      try {
        payload = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      } catch {
        payload = {};
      }
      const { status, body } = found.handle(request.url(), payload);
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      return;
    }
    await route.fulfill({
      status: found.status ?? 200,
      contentType: 'application/json',
      body: JSON.stringify(found.body ?? {}),
    });
  });
}

function baseStubs(state: {
  suppliers: Record<string, unknown>[];
  requests: Record<string, unknown>[];
  budgets: Record<string, unknown>[];
  orders: Record<string, unknown>[];
  invoices: Record<string, unknown>[];
  departments: Record<string, unknown>[];
  users: Record<string, unknown>[];
  notifications: Record<string, unknown>[];
}): Stub[] {
  const token = fakeJwt();
  return [
    {
      method: 'POST',
      match: '/auth/login',
      body: { accessToken: token, refreshToken: 'r', expiresInSeconds: 900, user: ME },
    },
    { method: 'GET', match: '/auth/me', body: ME },
    {
      method: 'POST',
      match: '/auth/refresh',
      status: 401,
      body: { code: 'INVALID_REFRESH_TOKEN', message: 'x' },
    },
    {
      method: 'POST',
      match: '/auth/forgot-password',
      body: { message: 'If an account exists for that address, a reset link is on its way.' },
    },
    { method: 'POST', match: '/auth/reset-password', status: 204, body: {} },
    { method: 'POST', match: '/auth/change-password', status: 204, body: {} },
    // The list match carries its query delimiter so sub-resource fan-out
    // (/suppliers/{id}/contacts, .../categories, .../performances) never
    // collides with the directory envelope under substring matching.
    { method: 'GET', match: '/suppliers?', handle: () => ({ status: 200, body: asPage(state.suppliers) }) },
    { method: 'GET', match: '/purchase-orders', handle: () => ({ status: 200, body: asPage(state.orders) }) },
    { method: 'GET', match: '/invites', body: [] },
    { method: 'GET', match: '/supplier-categories', body: [] },
    {
      method: 'GET',
      match: '/users/me',
      body: ME,
    },
    {
      method: 'PATCH',
      match: '/users/me',
      handle: (_url, payload) => ({ status: 200, body: { ...ME, ...payload } }),
    },
    {
      method: 'GET',
      match: '/purchase-requests',
      handle: (url) => {
        const status = new URL(url).searchParams.get('status');
        const rows = status ? state.requests.filter((r) => r['status'] === status) : state.requests;
        return { status: 200, body: asPage(rows) };
      },
    },
    {
      method: 'POST',
      match: '/purchase-requests',
      handle: (_url, payload) => {
        const created = {
          id: `r-${state.requests.length + 1}`,
          status: 'DRAFT',
          totalMinor: 0,
          requesterId: 'u-admin',
          createdAt: '2026-10-10T10:00:00Z',
          items: [],
          ...payload,
        };
        state.requests.push(created);
        return { status: 201, body: created };
      },
    },
  ];
}

test.describe('critical journeys', () => {
  test('request draft warns on unsaved loss', async ({ page }) => {
    await stubApi(page, [
      ...baseStubs({
        suppliers: [], requests: [], budgets: [], orders: [], invoices: [],
        departments: [], users: [], notifications: [],
      }),
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/requests/new');
    await page.getByLabel(/title/i).fill('Unsaved idea');
    await page.getByRole('link', { name: 'Cancel' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Discard draft' }).click();
    await expect(page).toHaveURL(/\/requests$/);
  });

  test('expired session returns to login', async ({ page }) => {
    await stubApi(page, [
      {
        method: 'GET',
        match: '/auth/me',
        status: 401,
        body: { code: 'UNAUTHENTICATED', message: 'x' },
      },
      { method: 'POST', match: '/auth/refresh', status: 401, body: { code: 'x', message: 'x' } },
    ]);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('sign in lands on the overview', async ({ page }) => {
    await stubApi(
      page,
      baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
    );
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('correct-horse-123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Procurement overview')).toBeVisible();
  });

  test('dashboard enriches rows, times and trend views', async ({ page }) => {
    const createdAt = new Date(Date.now() - 2 * 3600_000).toISOString();
    const requests: Record<string, unknown>[] = [
      {
        id: 'r-1',
        title: 'Journey laptops',
        status: 'SUBMITTED',
        priority: 'HIGH',
        totalMinor: 300000,
        createdAt,
        requesterId: 'u-admin',
      },
    ];
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests,
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      { method: 'GET', match: '/budgets', handle: () => ({ status: 200, body: asPage([]) }) },
      {
        method: 'GET',
        match: '/analytics/spend',
        body: {
          requestedMinor: 300000,
          orderedMinor: 0,
          invoicedMinor: 0,
          paidMinor: 0,
          byCategory: [{ category: 'Hardware', amountMinor: 300000 }],
          byPeriod: [{ period: '2026-10', orderedMinor: 0, invoicedMinor: 0, paidMinor: 0 }],
        },
      },
      {
        method: 'GET',
        match: '/analytics/approvals',
        body: { pending: 1, decided: 0, avgLeadHours: 0, maxLeadHours: 0 },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('correct-horse-123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('HIGH', { exact: true })).toBeVisible({ timeout: 10000 });
    const awaiting = page.getByRole('region', { name: 'Awaiting approval' });
    await expect(awaiting.getByRole('link', { name: 'Review' })).toBeVisible();
    await expect(page.getByText('2 hrs ago')).toBeVisible();
    await page.getByRole('option', { name: 'Category' }).click();
    await expect(page.getByText('Hardware', { exact: true })).toBeVisible();
  });

  test('supplier creation appears in the directory', async ({ page }) => {
    const suppliers: Record<string, unknown>[] = [];
    await stubApi(page, [
      ...baseStubs({
        suppliers,
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      { method: 'GET', match: '/suppliers?', handle: () => ({ status: 200, body: asPage(suppliers) }) },
      { method: 'GET', match: '/contacts', body: [] },
      { method: 'GET', match: '/categories', body: [] },
      { method: 'GET', match: '/performances', body: [] },
      {
        method: 'POST',
        match: '/suppliers',
        handle: (_url, payload) => {
          const created = { id: 's-1', status: 'ACTIVE', ...payload };
          suppliers.push(created);
          return { status: 201, body: created };
        },
      },
      { method: 'GET', match: '/supplier-categories', body: [] },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/suppliers');
    await page.getByRole('button', { name: 'Add supplier' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Name', { exact: true }).fill('Acme Supplies');
    await dialog.getByRole('button', { name: 'Add supplier' }).click();
    await expect(page.getByRole('link', { name: 'Acme Supplies' })).toBeVisible({ timeout: 10000 });
  });

  test('supplier export downloads the server file', async ({ page }) => {
    await stubApi(page, [
      ...baseStubs({
        suppliers: [], requests: [], budgets: [], orders: [], invoices: [],
        departments: [], users: [], notifications: [],
      }),
    ]);
    // Binary route after the catch-all: this pattern must win over
    // stubApi's generic matcher (verified: the reverse order aborts the
    // request). Mirrors production, including the CORS-exposed filename
    // header — without the exposure the browser hides Content-Disposition
    // and the download falls back to a generic name.
    await page.route('**/suppliers/export.xlsx', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: {
          'Content-Disposition': 'attachment; filename="suppliers-acme-2026-10-12.xlsx"',
          'Access-Control-Expose-Headers': 'Content-Disposition',
        },
        body: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
      });
    });
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/suppliers');
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Export Excel' }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('suppliers-acme-2026-10-12.xlsx');
  });

  test('supplier import rejects bad files client-side', async ({ page }) => {
    let uploads = 0;
    await stubApi(page, [
      ...baseStubs({
        suppliers: [], requests: [], budgets: [], orders: [], invoices: [],
        departments: [], users: [], notifications: [],
      }),
      {
        method: 'POST',
        match: '/suppliers/import',
        handle: () => {
          uploads += 1;
          return { status: 200, body: { created: 1, errors: [] } };
        },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/suppliers');
    await page.getByRole('button', { name: 'Import' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Bulk-create');

    await dialog.getByLabel('Import file').setInputFiles({
      name: 'notes.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('name\nAcme\n'),
    });
    await expect(dialog).toContainText('Only .xlsx and .csv');
    expect(uploads).toBe(0);

    await dialog.getByLabel('Import file').setInputFiles({
      name: 'big.csv',
      mimeType: 'text/csv',
      buffer: Buffer.alloc(6 * 1024 * 1024),
    });
    await expect(dialog).toContainText('5 MB');
    expect(uploads).toBe(0);

    await dialog.getByLabel('Import file').setInputFiles({
      name: 'suppliers.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('name\nAcme\n'),
    });
    await dialog.getByRole('button', { name: 'Import', exact: true }).click();
    await expect(dialog).toContainText('1 supplier created.');
    expect(uploads).toBe(1);
  });

  test('request draft, submit and approve', async ({ page }) => {
    const requests: Record<string, unknown>[] = [];
    let decision: Record<string, unknown> | null = null;
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests,
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      {
        method: 'POST',
        match: '/purchase-requests/r-1/submit',
        handle: () => {
          requests[0]['status'] = 'SUBMITTED';
          return { status: 200, body: requests[0] };
        },
      },
      {
        method: 'GET',
        match: '/purchase-requests/r-1',
        handle: () => ({ status: 200, body: requests[0] }),
      },
      {
        method: 'GET',
        match: '/approvals/decisions',
        handle: () => (decision ? { status: 200, body: decision } : { status: 404, body: {} }),
      },
      { method: 'GET', match: '/approvals/state', status: 404, body: {} },
      {
        method: 'POST',
        match: '/approvals/decisions',
        handle: (_url, payload) => {
          decision = {
            id: 'd-1',
            requestId: 'r-1',
            deciderId: 'u-admin',
            createdAt: '2026-10-10T11:00:00Z',
            ...payload,
          };
          requests[0]['status'] = 'APPROVED';
          return { status: 201, body: decision };
        },
      },
      { method: 'GET', match: '/budgets', body: [] },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/requests/new');
    await page.getByLabel(/title/i).fill('Journey laptops');
    await page.getByLabel(/priority/i).click();
    await page.getByRole('option', { name: 'HIGH' }).click();
    await page.getByRole('button', { name: 'Create draft' }).click();
    await expect(page).toHaveURL(/\/requests\/r-1/);

    await page.getByRole('button', { name: 'Submit for approval' }).click();
    await expect(page.locator('pf-status-badge', { hasText: 'Submitted' }).first()).toBeVisible({
      timeout: 10000,
    });

    await page.getByRole('button', { name: 'Approve' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
    await expect(page.locator('pf-status-badge', { hasText: 'Approved' }).first()).toBeVisible({
      timeout: 10000,
    });
  });

  test('budget pot creation lists the pot', async ({ page }) => {
    const budgets: Record<string, unknown>[] = [];
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets,
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      { method: 'GET', match: '/budgets', handle: () => ({ status: 200, body: asPage(budgets) }) },
      {
        method: 'POST',
        match: '/budgets',
        handle: (_url, payload) => {
          const created = {
            id: 'b-1',
            reservedMinor: 0,
            remainingMinor: payload['amountMinor'],
            currency: 'MAD',
            ...payload,
          };
          budgets.push(created);
          return { status: 201, body: created };
        },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/budgets');
    await page.getByRole('button', { name: 'New pot' }).click();
    const budgetDialog = page.getByRole('dialog');
    await budgetDialog.getByLabel(/pot name/i).fill('Engineering');
    await budgetDialog.getByLabel('Period').fill('2030-01');
    await budgetDialog.getByLabel(/^amount/i).fill('25000');
    await page.getByRole('button', { name: 'Create pot' }).click();
    await expect(page.getByText('Engineering')).toBeVisible({ timeout: 10000 });
  });

  test('order send and invoice payment tracking', async ({ page }) => {
    const order: Record<string, unknown> = {
      id: 'o-1',
      requestId: 'r-1',
      supplierId: 's-1',
      status: 'DRAFT',
      currency: 'MAD',
      totalMinor: 300000,
      lines: [
        {
          id: 'l-1',
          description: 'Laptop',
          quantity: 2,
          receivedQty: 0,
          unitPriceMinor: 150000,
          currency: 'MAD',
        },
      ],
    };
    const invoice: Record<string, unknown> = {
      id: 'i-1',
      orderId: 'o-1',
      number: 'INV-1',
      status: 'UNPAID',
      totalMinor: 300000,
      paidMinor: 0,
      currency: 'MAD',
    };
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [order],
        invoices: [invoice],
        departments: [],
        users: [],
        notifications: [],
      }),
      { method: 'GET', match: '/purchase-orders/o-1', body: order },
      {
        method: 'GET',
        match: '/suppliers/s-1',
        body: { id: 's-1', name: 'Acme Supplies', status: 'ACTIVE' },
      },
      {
        method: 'POST',
        match: '/purchase-orders/o-1/send',
        handle: () => {
          order['status'] = 'SENT';
          return { status: 200, body: order };
        },
      },
      { method: 'GET', match: '/invoices/i-1', body: invoice },
      {
        method: 'POST',
        match: '/invoices/i-1/payments',
        handle: () => {
          invoice['status'] = 'PAID';
          invoice['paidMinor'] = 300000;
          return { status: 200, body: invoice };
        },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/orders/o-1');
    await page.getByRole('button', { name: 'Send order' }).click();
    await expect(page.locator('pf-status-badge', { hasText: 'Sent' }).first()).toBeVisible({
      timeout: 10000,
    });

    await page.goto('/invoices/i-1');
    await page.getByRole('button', { name: /record payment/i }).click();
    const payDialog = page.getByRole('dialog');
    await payDialog.getByLabel(/amount/i).fill('3000');
    await payDialog.getByRole('button', { name: /record|pay/i }).click();
    await expect(page.locator('pf-status-badge', { hasText: 'Paid' }).first()).toBeVisible({
      timeout: 10000,
    });
  });

  test('organization administration creates department and user', async ({ page }) => {
    const departments: Record<string, unknown>[] = [];
    const users: Record<string, unknown>[] = [{ ...ME }];
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments,
        users,
        notifications: [],
      }),
      { method: 'GET', match: '/departments', handle: () => ({ status: 200, body: asPage(departments) }) },
      {
        method: 'GET',
        match: '/memberships',
        handle: () => ({ status: 200, body: asPage([]) }),
      },
      { method: 'GET', match: '/users', handle: () => ({ status: 200, body: asPage(users) }) },
      {
        method: 'POST',
        match: '/departments',
        handle: (_url, payload) => {
          const created = { id: 'dep-1', ...payload };
          departments.push(created);
          return { status: 201, body: created };
        },
      },
      {
        method: 'POST',
        match: '/api/v1/users',
        handle: (_url, payload) => {
          const created = { id: 'u-2', status: 'ACTIVE', tenantSlug: 'acme', ...payload };
          users.push(created);
          return { status: 201, body: created };
        },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/admin/departments');
    await page.getByLabel(/new department/i).fill('Engineering');
    await page.getByRole('button', { name: 'Add' }).click();
    await expect(page.getByText('Engineering')).toBeVisible({ timeout: 10000 });

    await page.goto('/admin/users');
    const usersPanel = page.getByRole('region', { name: 'Users' });
    await usersPanel.getByLabel(/^email/i).fill('min@acme.test');
    await usersPanel.getByLabel(/password/i).fill('correct-horse-123');
    await usersPanel.getByLabel(/roles/i).click();
    await page.getByRole('option', { name: 'MEMBER' }).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Create user' }).click();
    await expect(page.getByText('min@acme.test')).toBeVisible({ timeout: 10000 });
  });

  test('analytics renders server aggregates', async ({ page }) => {
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      {
        method: 'GET',
        match: '/analytics/spend',
        body: {
          requestedMinor: 100000,
          orderedMinor: 80000,
          invoicedMinor: 50000,
          paidMinor: 20000,
          byCategory: [{ category: 'Cloud', amountMinor: 80000 }],
          byPeriod: [
            { period: '2026-10', orderedMinor: 80000, invoicedMinor: 50000, paidMinor: 20000 },
          ],
        },
      },
      { method: 'GET', match: '/analytics/suppliers', body: [] },
      {
        method: 'GET',
        match: '/analytics/approvals',
        body: { pending: 2, decided: 5, avgLeadHours: 3.5, maxLeadHours: 9 },
      },
      {
        method: 'GET',
        match: '/ai/usage',
        body: { calls: 0, promptTokens: 0, completionTokens: 0, byFeature: [] },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/analytics');
    await expect(page.getByText('Cloud', { exact: true })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.spend-chart__table')).toContainText('2026-10');
  });

  test('analytics charts drill into filters', async ({ page }) => {
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      {
        method: 'GET',
        match: '/analytics/spend',
        body: {
          requestedMinor: 100000,
          orderedMinor: 80000,
          invoicedMinor: 50000,
          paidMinor: 20000,
          byCategory: [{ category: 'Cloud', amountMinor: 80000 }],
          byPeriod: [
            { period: '2026-10', orderedMinor: 80000, invoicedMinor: 50000, paidMinor: 20000 },
          ],
        },
      },
      { method: 'GET', match: '/analytics/suppliers', body: [] },
      {
        method: 'GET',
        match: '/analytics/approvals',
        body: { pending: 2, decided: 5, avgLeadHours: 3.5, maxLeadHours: 9 },
      },
      {
        method: 'GET',
        match: '/ai/usage',
        body: { calls: 0, promptTokens: 0, completionTokens: 0, byFeature: [] },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/analytics');
    await expect(page.locator('.spend-chart__table')).toContainText('2026-10');
    // Entrance animations run ~600ms: click once the bars have grown.
    await page.waitForTimeout(1000);
    await expect(page.locator('.spend-chart__stage canvas')).toBeVisible();

    const spendBox = await page.locator('.spend-chart__stage canvas').boundingBox();
    // The single-month group sits near the canvas middle; sweep across it
    // so the click lands on a bar whichever series it hits (same month).
    // getByRole textbox: the "Spend by period" section name also matches
    // getByLabel('Period'), so scope to the month input explicitly.
    const periodBox = page.getByRole('textbox', { name: 'Period' });
    if (spendBox) {
      const spendCanvas = page.locator('.spend-chart__stage canvas');
      for (const fraction of [0.44, 0.47, 0.5, 0.53, 0.56]) {
        await spendCanvas.click({
          position: { x: spendBox.width * fraction, y: spendBox.height * 0.8 },
          timeout: 10000,
        });
        try {
          await expect(periodBox).toHaveValue('2026-10', { timeout: 1500 });
          break;
        } catch {
          // Missed the bars; try the next offset.
        }
      }
    }
    await expect(periodBox).toHaveValue('2026-10', { timeout: 10000 });

    // The month drill reloads (and re-animates) the charts; wait again,
    // then sweep the donut ring for the single Cloud segment.
    await page.waitForTimeout(1000);
    const categoryBox = page.getByRole('textbox', { name: 'Filter categories' });
    const donutCanvas = page.locator('.donut-chart__stage canvas');
    const donutBox = await donutCanvas.boundingBox();
    if (donutBox) {
      const centerX = donutBox.width / 2;
      const centerY = donutBox.height / 2;
      const radius = Math.min(donutBox.width, donutBox.height) * 0.34;
      const ring = [
        { x: centerX, y: centerY - radius },
        { x: centerX + radius, y: centerY },
        { x: centerX, y: centerY + radius },
        { x: centerX - radius, y: centerY },
      ];
      for (const position of ring) {
        await donutCanvas.click({ position, timeout: 10000 });
        try {
          await expect(categoryBox).toHaveValue('Cloud', {
            timeout: 1500,
          });
          break;
        } catch {
          // Missed the ring; try the next cardinal point.
        }
      }
    }
    await expect(categoryBox).toHaveValue('Cloud', {
      timeout: 10000,
    });
  });

  test('copilot answers with citations and survives provider failure', async ({ page }) => {
    let chatFails = false;
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications: [],
      }),
      {
        method: 'POST',
        match: '/ai/chat',
        handle: () =>
          chatFails
            ? {
                status: 503,
                body: {
                  code: 'AI_DISABLED',
                  message: 'AI features are disabled (AI_ENABLED=false)',
                },
              }
            : {
                status: 200,
                body: {
                  answer: 'Spend is flat.',
                  citations: ['orderedMinor=0 via GET /analytics/spend'],
                  model: 'stub',
                },
              },
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/copilot');
    await page.getByLabel(/ask about/i).fill('How is spend?');
    await page.getByRole('button', { name: 'Ask', exact: true }).click();
    await expect(page.getByText('Spend is flat.')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/orderedMinor=0/)).toBeVisible();

    chatFails = true;
    await page.getByLabel(/ask about/i).fill('Again?');
    await page.getByRole('button', { name: 'Ask', exact: true }).click();
    await expect(page.getByText(/disabled/)).toBeVisible({ timeout: 10000 });
  });

  test('notifications mark-read and settings save', async ({ page }) => {
    const notifications: Record<string, unknown>[] = [
      {
        id: 'n-1',
        type: 'REQUEST_SUBMITTED',
        title: 'Submitted',
        body: 'A request awaits.',
        read: false,
        createdAt: '2026-10-10T10:00:00Z',
      },
    ];
    await stubApi(page, [
      ...baseStubs({
        suppliers: [],
        requests: [],
        budgets: [],
        orders: [],
        invoices: [],
        departments: [],
        users: [],
        notifications,
      }),
      {
        method: 'GET',
        match: '/notifications',
        handle: (url) => {
          const rows = new URL(url).searchParams.get('unreadOnly') === 'true'
            ? notifications.filter((n) => !n['read'])
            : notifications;
          return {
            status: 200,
            body: { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 },
          };
        },
      },
      {
        method: 'PATCH',
        match: '/notifications/n-1/read',
        handle: () => {
          notifications[0]['read'] = true;
          return { status: 200, body: notifications[0] };
        },
      },
      { method: 'GET', match: '/users/me', body: ME },
      {
        method: 'PATCH',
        match: '/users/me',
        handle: (_url, payload) => ({ status: 200, body: { ...ME, ...payload } }),
      },
    ]);
    await page.goto('/login');
    await page.getByLabel('Work email').fill('boss@acme.test');
    await page.getByLabel('Password', { exact: true }).fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/notifications');
    await page.getByRole('button', { name: 'Mark read' }).click();
    await page.getByText(/Unread \(\d+\)/).click();
    await expect(page.getByText('Nothing here')).toBeVisible({ timeout: 10000 });

    await page.goto('/settings');
    await page.getByLabel(/first name/i).fill('Ada');
    await page.getByRole('button', { name: 'Save profile' }).click();
    await expect(page.getByText('Profile saved.')).toBeVisible({ timeout: 10000 });
  });
});
