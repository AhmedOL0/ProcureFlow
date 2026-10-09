import { HttpErrorResponse } from '@angular/common/http';

export interface ApiFailure {
  status: number;
  code: string | null;
  message: string;
}

/** Normalizes Http failures into the backend's ApiError envelope shape. */
export function parseApiFailure(error: unknown): ApiFailure {
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as { code?: unknown; message?: unknown };
    return {
      status: error.status,
      code: typeof body.code === 'string' ? body.code : null,
      message: typeof body.message === 'string' && body.message ? body.message : fallbackMessage(error.status),
    };
  }
  return { status: 0, code: null, message: 'Something went wrong. Try again.' };
}

function fallbackMessage(status: number): string {
  if (status === 0) {
    return 'The server is unreachable. Check your connection.';
  }
  if (status === 401) {
    return 'Your session expired. Sign in again.';
  }
  if (status === 403) {
    return 'You do not have permission for this action.';
  }
  if (status === 404) {
    return 'This record does not exist in your workspace.';
  }
  return 'Something went wrong. Try again.';
}

/** Friendly overrides for business-rule codes the UI handles explicitly. */
const CODE_MESSAGES: Record<string, string> = {
  SUPPLIER_EXISTS: 'A supplier with this name already exists.',
  SUPPLIER_REFERENCED: 'This supplier is used by purchase documents and cannot be deleted.',
  CATEGORY_IN_USE: 'This category is assigned to suppliers and cannot be deleted.',
  EMAIL_IN_USE: 'This email is already registered in this workspace.',
  LOGIN_AMBIGUOUS: 'This email lives in several workspaces — enter your workspace slug.',
  INVALID_CREDENTIALS: 'Invalid email or password.',
  CONTACT_NOT_FOUND: 'This contact no longer exists.',
  SUPPLIER_NOT_FOUND: 'This supplier no longer exists in your workspace.',
};

export function userMessageFor(failure: ApiFailure): string {
  if (failure.code && CODE_MESSAGES[failure.code]) {
    return CODE_MESSAGES[failure.code];
  }
  return failure.message;
}
