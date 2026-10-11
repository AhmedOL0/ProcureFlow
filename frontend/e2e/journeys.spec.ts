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
    { method: 'GET', match: '/suppliers', body: state.suppliers },
    { method: 'GET', match: '/orders', body: state.orders },
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
        return { status: 200, body: rows };
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
    await page.getByLabel('Password').fill('x');
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
    await page.getByLabel('Password').fill('correct-horse-123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Procurement overview')).toBeVisible();
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
      { method: 'GET', match: '/suppliers', body: suppliers },
      { method: 'GET', match: '/contacts', body: [] },
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
    await page.getByLabel('Password').fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/suppliers');
    await page.getByRole('button', { name: 'Add supplier' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Name', { exact: true }).fill('Acme Supplies');
    await dialog.getByRole('button', { name: 'Add supplier' }).click();
    await expect(page.getByRole('link', { name: 'Acme Supplies' })).toBeVisible({ timeout: 10000 });
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
    await page.getByLabel('Password').fill('x');
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
      { method: 'GET', match: '/budgets', body: budgets },
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
    await page.getByLabel('Password').fill('x');
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
    await page.getByLabel('Password').fill('x');
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
      { method: 'GET', match: '/departments', body: departments },
      { method: 'GET', match: '/memberships', body: [] },
      { method: 'GET', match: '/users', body: users },
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
    await page.getByLabel('Password').fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto('/admin/departments');
    await page.getByLabel(/new department/i).fill('Engineering');
    await page.getByRole('button', { name: 'Add' }).click();
    await expect(page.getByText('Engineering')).toBeVisible({ timeout: 10000 });

    await page.goto('/admin/users');
    await page.getByLabel(/^email/i).fill('min@acme.test');
    await page.getByLabel(/password/i).fill('correct-horse-123');
    await page.getByLabel(/roles/i).click();
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
    await page.getByLabel('Password').fill('x');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.goto('/analytics');
    await expect(page.getByText('Cloud')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('2026-10')).toBeVisible();
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
    await page.getByLabel('Password').fill('x');
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
        body: { content: notifications, page: 0, size: 20, totalElements: 1, totalPages: 1 },
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
    await page.getByLabel('Password').fill('x');
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
