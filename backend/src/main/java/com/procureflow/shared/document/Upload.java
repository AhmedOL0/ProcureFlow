package com.procureflow.shared.document;

import com.procureflow.shared.web.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Import upload validation. Trusts neither the extension nor the client:
 * every file is size-checked, extension-checked, then content-sniffed
 * (xlsx must start with the zip magic, CSV must decode as text without NUL
 * bytes). Limits are enforced here as well as in Spring's multipart config
 * so oversized uploads fail identically in tests without a container.
 */
public final class Upload {

    /** Multipart ceiling mirrored in application.yml (defense in depth). */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Imported data rows per file; exports cap separately in Spreadsheet. */
    public static final int MAX_IMPORT_ROWS = 500;

    /** Row-error entries flattened into an ApiError details map. */
    public static final int MAX_ERROR_DETAILS = 50;

    public enum FileKind {
        XLSX,
        CSV
    }

    private Upload() {
    }

    public static FileKind inspect(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "Uploaded file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Uploaded file exceeds the 5 MB import limit");
        }
        String extension = extensionOf(filename);
        return switch (extension) {
            case "xlsx" -> {
                if (!isZip(content)) {
                    throw ApiException.badRequest("CORRUPT_FILE", "File is not a valid .xlsx workbook");
                }
                yield FileKind.XLSX;
            }
            case "csv" -> {
                if (!isText(content)) {
                    throw ApiException.badRequest("CORRUPT_FILE", "File is not valid UTF-8 CSV text");
                }
                yield FileKind.CSV;
            }
            default -> throw ApiException.badRequest(
                    "UNSUPPORTED_FILE", "Only .xlsx and .csv files can be imported");
        };
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isZip(byte[] content) {
        return content.length > 4
                && content[0] == 0x50
                && content[1] == 0x4B
                && content[2] == 0x03
                && content[3] == 0x04;
    }

    private static boolean isText(byte[] content) {
        int probe = Math.min(content.length, 8192);
        for (int i = 0; i < probe; i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
