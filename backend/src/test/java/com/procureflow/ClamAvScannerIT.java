package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.procureflow.shared.antivirus.ClamAvScanner;
import com.procureflow.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ClamAV INSTREAM client against a real clamd: clean bytes pass, the EICAR
 * probe is caught, a dead daemon fails closed. No Spring context — the
 * scanner is a plain socket client. The EICAR string below is the
 * industry-standard harmless test signature, never malware.
 */
@Testcontainers
class ClamAvScannerIT {

    static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Container
    static final GenericContainer<?> CLAMD =
            new GenericContainer<>("clamav/clamav:1.4").withExposedPorts(3310);

    private ClamAvScanner scanner() {
        return new ClamAvScanner(CLAMD.getHost(), CLAMD.getMappedPort(3310), 15000);
    }

    private void awaitSignatures() {
        // clamd serves only after freshclam loads signature DBs (downloaded
        // at container boot); poll a clean scan instead of guessing logs.
        Awaitility.await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> {
                    scanner().scan("name,email\nAcme,a@b.c\n".getBytes(StandardCharsets.UTF_8));
                    return true;
                });
    }

    @Test
    void cleanBytesPass() {
        awaitSignatures();
        scanner().scan("name\nAcme\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void eicarProbeIsRejected() {
        awaitSignatures();
        ApiException rejected = assertThrows(
                ApiException.class,
                () -> scanner().scan(EICAR.getBytes(StandardCharsets.UTF_8)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, rejected.getStatus());
        assertEquals("VIRUS_DETECTED", rejected.getCode());
    }

    @Test
    void deadDaemonFailsClosed() {
        ClamAvScanner down = new ClamAvScanner("localhost", 9, 2000);
        ApiException rejected = assertThrows(
                ApiException.class, () -> down.scan("name\nAcme\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rejected.getStatus());
        assertEquals("SCAN_UNAVAILABLE", rejected.getCode());
    }
}
