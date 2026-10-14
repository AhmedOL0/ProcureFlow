package com.procureflow.shared.antivirus;

import com.procureflow.shared.web.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ClamAV INSTREAM scanning for uploaded files, dependency-free (plain
 * socket, 2 KB chunks). Wired into every import path after the
 * size/sniff guards and before any parse: an infected file never reaches
 * POI or the CSV reader.
 *
 * <p>When {@code app.antivirus.host} is blank (local dev default) scanning
 * is skipped with a one-time warning — running clamd is opt-in via the
 * {@code scan} compose profile. When a host IS configured the scan is
 * enforced: infected answers 422 {@code VIRUS_DETECTED}, an unreachable
 * daemon answers 503 {@code SCAN_UNAVAILABLE} (fail closed, never silent).
 */
@Service
public class ClamAvScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanner.class);
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanner(
            @Value("${app.antivirus.host:}") String host,
            @Value("${app.antivirus.port:3310}") int port,
            @Value("${app.antivirus.timeout-ms:10000}") int timeoutMs) {
        this.host = host == null ? "" : host.strip();
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    /** Scan raw upload bytes; returns silently when clean or disabled. */
    public void scan(byte[] content) {
        if (host.isEmpty()) {
            if (WARNED.compareAndSet(false, true)) {
                log.warn("antivirus scan skipped: app.antivirus.host is not set"
                        + " (opt in with the scan compose profile)");
            }
            return;
        }
        // clamd terminates replies with NUL, which strip() keeps (it is not
        // whitespace): without this every verdict misses and all scans
        // report unavailable. Normalize first, then match the tail.
        String verdict = instream(content).replace("\0", "").strip();
        if (verdict.endsWith("FOUND")) {
            String signature = verdict.substring(0, verdict.length() - "FOUND".length()).strip();
            if (signature.startsWith("stream:")) {
                signature = signature.substring("stream:".length()).strip();
            }
            log.warn("antivirus rejected an upload: {}", signature);
            throw ApiException.unprocessable(
                    "VIRUS_DETECTED", "Uploaded file failed the malware scan and was rejected");
        }
        if (!verdict.endsWith("OK")) {
            throw ApiException.unavailable("SCAN_UNAVAILABLE", "Malware scan did not answer cleanly; try again");
        }
    }

    private String instream(byte[] content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
            ByteBuffer frame = ByteBuffer.allocate(4);
            for (int offset = 0; offset < content.length; offset += 2048) {
                int length = Math.min(2048, content.length - offset);
                frame.clear();
                out.write(frame.putInt(length).array());
                out.write(content, offset, length);
            }
            frame.clear();
            out.write(frame.putInt(0).array());
            out.flush();
            try (BufferedReader in =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String reply = in.readLine();
                if (reply == null || reply.isBlank()) {
                    throw new IOException("empty reply from clamd");
                }
                return reply.strip();
            }
        } catch (IOException e) {
            log.warn("antivirus daemon unreachable at {}:{} ({})", host, port, e.getMessage());
            throw ApiException.unavailable("SCAN_UNAVAILABLE", "Malware scanner is unreachable; try again");
        }
    }
}
