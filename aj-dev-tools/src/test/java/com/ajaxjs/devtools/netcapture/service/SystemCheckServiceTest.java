package com.ajaxjs.devtools.netcapture.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class SystemCheckServiceTest {
    @Test
    void missingNativeLibraryDoesNotAbortStartup() {
        PacketCaptureService capture = mock(PacketCaptureService.class);
        when(capture.getAvailableNetworkInterfaces()).thenThrow(new UnsatisfiedLinkError("wpcap"));
        assertDoesNotThrow(() -> new SystemCheckService(capture).performSystemCheck());
        verify(capture).getAvailableNetworkInterfaces();
    }

    @Test
    void failedNativeInitializationDoesNotAbortSubsequentChecks() {
        PacketCaptureService capture = mock(PacketCaptureService.class);
        when(capture.getAvailableNetworkInterfaces()).thenThrow(new NoClassDefFoundError("NativeMappings"));
        assertDoesNotThrow(() -> new SystemCheckService(capture).performSystemCheck());
    }

    @Test
    void unavailableCaptureDoesNotAbortStartup() {
        PacketCaptureService capture = mock(PacketCaptureService.class);
        when(capture.getAvailableNetworkInterfaces()).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(() -> new SystemCheckService(capture).performSystemCheck());
    }

    @Test
    void noInterfacesDoesNotAbortStartup() {
        PacketCaptureService capture = mock(PacketCaptureService.class);
        when(capture.getAvailableNetworkInterfaces()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> new SystemCheckService(capture).performSystemCheck());
    }
}
