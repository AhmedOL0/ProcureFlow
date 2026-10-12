/**
 * Binary download helpers: filename resolution, blob saving, and the CSV
 * cell guard. Pure and framework-free for Vitest.
 */
import type { HttpResponse } from '@angular/common/http';
import type { WritableSignal } from '@angular/core';
import type { Observable } from 'rxjs';

/** A downloaded file with its server-chosen name. */
export interface Download {
  blob: Blob;
  filename: string;
}

const MAX_FILENAME = 120;

/**
 * Formula-injection guard mirroring the backend CellText rule: a cell that
 * opens with = + - @ | % (or tab/return) executes in Excel, so such cells
 * take the invisible text marker. Applied to every value the client CSV
 * exporter emits.
 */
export function sanitizeCsvCell(value: string | number | null | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = `${value}`;
  if (text === '' || !'=+-@|%\t\r\n'.includes(text.charAt(0))) {
    return text;
  }
  return `'${text}`;
}

/** Quote one CSV cell (commas, quotes and newlines survive the round trip). */
export function csvCell(value: string | number | null | undefined): string {
  return `"${sanitizeCsvCell(value).replace(/"/g, '""')}"`;
}

/**
 * Server filename wins (Content-Disposition attachment), falling back to the
 * caller name. Path segments and over-long names are stripped so a hostile
 * header cannot escape the downloads folder.
 */
export function filenameFromDisposition(header: string | null | undefined, fallback: string): string {
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header ?? '');
  const raw = (match?.[1] ?? fallback).trim();
  const base = raw.split(/[\\/]/).pop() ?? fallback;
  const clean = base.replace(/["<>|:*?]/g, '').slice(0, MAX_FILENAME);
  return clean === '' ? fallback : clean;
}

/** Trigger the browser save dialog for an in-memory blob. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  // Attached: detached-anchor clicks are ignored by some engines, which
  // silently drops the download (caught by the export e2e, not by hand).
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/** Map an observed blob response to a Download (server filename wins). */
export function toDownload(response: HttpResponse<Blob>, fallback: string): Download {
  const blob = response.body ?? new Blob();
  return { blob, filename: filenameFromDisposition(response.headers.get('Content-Disposition'), fallback) };
}

/**
 * Run an export download: busy spinner while in flight, save dialog on
 * success, inline message on failure. The message mapping stays in the
 * caller (it owns the ApiError vocabulary).
 */
export function runDownload(
  source: Observable<Download>,
  busy: WritableSignal<boolean>,
  failed: WritableSignal<string | null>,
  toMessage: (error: unknown) => string,
): void {
  busy.set(true);
  failed.set(null);
  source.subscribe({
    next: (file) => {
      busy.set(false);
      saveBlob(file.blob, file.filename);
    },
    error: (error: unknown) => {
      busy.set(false);
      failed.set(toMessage(error));
    },
  });
}
