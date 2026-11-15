package com.ajaxjs.devtools.sysmonitor.linux;

import com.ajaxjs.devtools.sysmonitor.linux.LinuxInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LinuxInfoTest {
    @TempDir
    Path directory;

    @Test
    void supportsLargeCountersAndAllEightFieldsWithoutCountingGuestTwice() throws Exception {
        long[] before = LinuxInfo.parseCpuTicks("cpu 3000000000 20 40 4000000000 50 60 70 80 90 100");
        long[] after = LinuxInfo.parseCpuTicks("cpu 3000000010 22 44 4000000040 55 66 77 88 1000090 1000100");

        assertEquals(3000000000L, before[0]);
        assertEquals(4000000000L, before[3]);
        assertEquals(37.0 / 82, LinuxInfo.calculateCpuUsage(before, after), 0.000001);
    }

    @Test
    void supportsOlderFourFieldFormatAndWhitespace() throws Exception {
        assertArrayEquals(new long[]{1, 2, 3, 4, 0, 0, 0, 0}, LinuxInfo.parseCpuTicks("  cpu\t1  2 3 4  "));
    }

    @Test
    void rejectsMissingMalformedAndOutOfRangeCounters() {
        String[] invalid = {"", "cpu0 1 2 3 4", "cpu 1 2 3", "cpu x 2 3 4", "cpu -1 2 3 4",
                "cpu 9223372036854775808 2 3 4"};

        for (String line : invalid)
            assertThrows(IOException.class, () -> LinuxInfo.parseCpuTicks(line), line);

        assertThrows(IOException.class, () -> LinuxInfo.parseCpuTicks(null));
    }

    @Test
    void rejectsZeroDeltaRollbackAndDeltaOverflow() throws Exception {
        long[] ticks = LinuxInfo.parseCpuTicks("cpu 10 20 30 40");
        assertThrows(IOException.class, () -> LinuxInfo.calculateCpuUsage(ticks, ticks));
        assertThrows(IOException.class, () -> LinuxInfo.calculateCpuUsage(ticks, LinuxInfo.parseCpuTicks("cpu 9 20 30 40")));
        assertThrows(IOException.class, () -> LinuxInfo.calculateCpuUsage(new long[8], new long[]{Long.MAX_VALUE, 1, 0, 0, 0, 0, 0, 0}));
    }

    @Test
    void distinguishesGenuineIdleAndFullyBusySamples() throws Exception {
        assertEquals(0, LinuxInfo.calculateCpuUsage(new long[8], new long[]{0, 0, 0, 10, 10, 0, 0, 0}));
        assertEquals(1, LinuxInfo.calculateCpuUsage(new long[8], new long[]{10, 10, 10, 0, 0, 10, 10, 10}));
    }

    @Test
    void readsTwoSnapshotsFromFileAndPropagatesSecondSampleErrors() throws Exception {
        Path file = directory.resolve("stat");
        Files.writeString(file, "cpu 100 0 0 100\ncpu0 50 0 0 50\n");
        assertEquals(0.5f, LinuxInfo.getCpuUsage(file, () -> write(file, "cpu 150 0 0 150\n")));
        assertThrows(UncheckedIOException.class, () -> LinuxInfo.getCpuUsage(file, () -> write(file, "invalid")));
    }

    @Test
    void readFailureDoesNotReturnZeroOrContinueSampling() {
        assertThrows(UncheckedIOException.class, () -> LinuxInfo.getCpuUsage(directory.resolve("missing"),
                () -> fail("must not wait after the first read failed")));
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
