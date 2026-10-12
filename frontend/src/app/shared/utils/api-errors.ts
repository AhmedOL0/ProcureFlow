import { HttpErrorResponse } from '@angular/common/http';

export interface ApiFailure {
  status: number;
  code: string | null;
  message: string;
  details: Record<string, string>;
}

/** Normalizes Http failures into the backend's ApiError envelope shape. */
export function parseApiFailure(error: unknown): ApiFailure {
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as { code?: unknown; message?: unknown; details?: unknown };
    return {
      status: error.status,
      code: typeof body.code === 'string' ? body.code : null,
      message: typeof body.message === 'string' && body.message ? body.message : fallbackMessage(error.status),
      details: isStringMap(body.details) ? body.details : {},
    };
  }
  return { status: 0, code: null, message: 'Something went wrong. Try again.', details: {} };
}

function isStringMap(value: unknown): value is Record<string, string> {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  return Object.values(value).every((entry) => typeof entry === 'string');
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
  PASSWORD_TOO_LONG: 'That password is too long for secure hashing — use at most 72 characters.',
  INVALID_CREDENTIALS: 'Invalid email or password.',
  CONTACT_NOT_FOUND: 'This contact no longer exists.',
  SUPPLIER_NOT_FOUND: 'This supplier no longer exists in your workspace.',
  IMPORT_VALIDATION_FAILED: 'Some rows failed validation — nothing was imported. Fix the rows below and re-upload.',
  MISSING_COLUMNS: "The file needs at least a 'name' column.",
  UNSUPPORTED_FILE: 'Only .xlsx and .csv files can be imported.',
  CORRUPT_FILE: 'The file could not be parsed. Check it opens in Excel first.',
  EMPTY_FILE: 'The uploaded file is empty.',
  FILE_TOO_LARGE: 'The file exceeds the 5 MB import limit.',
  TOO_MANY_ROWS: 'Import is limited to 500 rows per file.',
  NO_DATA_ROWS: 'The file has no data rows.',
  EXPORT_TOO_LARGE: 'Too many rows for one export — narrow the filters and try again.',
  MALFORMED_UPLOAD: 'The upload could not be read. Try again.',
};

export function userMessageFor(failure: ApiFailure): string {
  if (failure.code && CODE_MESSAGES[failure.code]) {
    return CODE_MESSAGES[failure.code];
  }
  return failure.message;
}
