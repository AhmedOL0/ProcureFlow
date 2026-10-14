import { HttpErrorResponse } from '@angular/common/http';

import { getDefaultLanguage, type Language } from '../../core/i18n/language.service';export interface ApiFailure {
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
      message:
        typeof body.message === 'string' && body.message
          ? body.message
          : fallbackMessage(error.status, getDefaultLanguage()),
      details: isStringMap(body.details) ? body.details : {},
    };
  }
  return { status: 0, code: null, message: networkMessage(getDefaultLanguage()), details: {} };
}

function isStringMap(value: unknown): value is Record<string, string> {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  return Object.values(value).every((entry) => typeof entry === 'string');
}

/** Localized generic text for a status, for client-built failures (no HTTP round trip). */
export function fallbackMessage(status: number, lang: Language): string {
  if (status === 0) {
    return networkMessage(lang);
  }
  if (status === 401) {
    return lang === 'fr' ? 'Votre session a expiré. Reconnectez-vous.' : 'Your session expired. Sign in again.';
  }
  if (status === 403) {
    return lang === 'fr'
      ? "Vous n'avez pas la permission pour cette action."
      : 'You do not have permission for this action.';
  }
  if (status === 404) {
    return lang === 'fr'
      ? "Cet enregistrement n'existe pas dans votre espace."
      : 'This record does not exist in your workspace.';
  }
  return lang === 'fr' ? "Un problème est survenu. Réessayez." : 'Something went wrong. Try again.';
}

function networkMessage(lang: Language): string {
  return lang === 'fr' ? 'Le serveur est injoignable. Vérifiez votre connexion.' : 'The server is unreachable. Check your connection.';
}

/** Friendly overrides for business-rule codes the UI handles explicitly. */
const CODE_MESSAGES: Record<Language, Record<string, string>> = {
  en: {
    SUPPLIER_EXISTS: 'A supplier with this name already exists.',
    SUPPLIER_REFERENCED: 'This supplier is used by purchase documents and cannot be deleted.',
    CATEGORY_IN_USE: 'This category is assigned to suppliers and cannot be deleted.',
    EMAIL_IN_USE: 'This email is already registered in this workspace.',
    LOGIN_AMBIGUOUS: 'This email lives in several workspaces. Enter your workspace slug.',
    PASSWORD_TOO_LONG: 'That password is too long for secure hashing. Use at most 72 characters.',
    INVALID_CREDENTIALS: 'Invalid email or password.',
    CONTACT_NOT_FOUND: 'This contact no longer exists.',
    SUPPLIER_NOT_FOUND: 'This supplier no longer exists in your workspace.',
    IMPORT_VALIDATION_FAILED: 'Some rows failed validation. Nothing was imported. Fix the rows below and re-upload.',
    MISSING_COLUMNS: "The file needs at least a 'name' column.",
    UNSUPPORTED_FILE: 'Only .xlsx and .csv files can be imported.',
    CORRUPT_FILE: 'The file could not be parsed. Check it opens in Excel first.',
    EMPTY_FILE: 'The uploaded file is empty.',
    FILE_TOO_LARGE: 'The file exceeds the 5 MB import limit.',
    TOO_MANY_ROWS: 'Import is limited to 500 rows per file.',
    NO_DATA_ROWS: 'The file has no data rows.',
    EXPORT_TOO_LARGE: 'Too many rows for one export. Narrow the filters and try again.',
    MALFORMED_UPLOAD: 'The upload could not be read. Try again.',
    TOO_MANY_REQUESTS: 'Too many attempts. Wait a little and try again.',
  },
  fr: {
    SUPPLIER_EXISTS: 'Un fournisseur portant ce nom existe déjà.',
    SUPPLIER_REFERENCED: "Ce fournisseur est utilisé par des documents d'achat et ne peut pas être supprimé.",
    CATEGORY_IN_USE: 'Cette catégorie est assignée à des fournisseurs et ne peut pas être supprimée.',
    EMAIL_IN_USE: 'Cet e-mail est déjà enregistré dans cet espace.',
    LOGIN_AMBIGUOUS: 'Cet e-mail existe dans plusieurs espaces. Saisissez votre identifiant.',
    PASSWORD_TOO_LONG: 'Ce mot de passe est trop long pour le hachage sécurisé. 72 caractères maximum.',
    INVALID_CREDENTIALS: "E-mail ou mot de passe invalide.",
    CONTACT_NOT_FOUND: "Ce contact n'existe plus.",
    SUPPLIER_NOT_FOUND: "Ce fournisseur n'existe plus dans votre espace.",
    IMPORT_VALIDATION_FAILED:
      'Certaines lignes ont échoué la validation. Rien ne fut importé. Corrigez les lignes ci-dessous et renvoyez le fichier.',
    MISSING_COLUMNS: "Le fichier exige au moins une colonne 'name'.",
    UNSUPPORTED_FILE: 'Seuls les fichiers .xlsx et .csv peuvent être importés.',
    CORRUPT_FILE: 'Le fichier ne peut être lu. Vérifiez qu’il s’ouvre dans Excel.',
    EMPTY_FILE: 'Le fichier téléversé est vide.',
    FILE_TOO_LARGE: "Le fichier dépasse la limite d'importation de 5 Mo.",
    TOO_MANY_ROWS: "L'importation est limitée à 500 lignes par fichier.",
    NO_DATA_ROWS: "Le fichier ne contient aucune ligne de données.",
    EXPORT_TOO_LARGE: 'Trop de lignes pour une exportation. Réduisez les filtres et réessayez.',
    MALFORMED_UPLOAD: "Le téléversement ne peut être lu. Réessayez.",
    TOO_MANY_REQUESTS: 'Trop de tentatives. Patientez un peu et réessayez.',
  },
};

export function userMessageFor(failure: ApiFailure, lang: Language = getDefaultLanguage()): string {
  if (failure.code && CODE_MESSAGES[lang][failure.code]) {
    return CODE_MESSAGES[lang][failure.code];
  }
  if (failure.code && CODE_MESSAGES.en[failure.code]) {
    return CODE_MESSAGES.en[failure.code];
  }
  // Unknown codes keep the server text: a specific English message beats a
  // generic translated one for errors (details stay English by design until
  // backend locale negotiation exists).
  if (failure.message) {
    return failure.message;
  }
  return fallbackMessage(failure.status, lang);
}
