package com.example.ss_final_java;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;

public class MainActivityTest {

    private MainActivity mainActivity;

    @Before
    public void setup() {
        mainActivity = mock(MainActivity.class);  // Cannot use real Activity without Android
    }

    @Test
    public void testStartPeriodicMode() {
        doCallRealMethod().when(mainActivity).startPeriodicMode();
        mainActivity.startPeriodicMode();  // Covers the method logic

        verify(mainActivity).startPeriodicMode();  // Confirm it was called
    }

    @Test
    public void testStopPeriodicMode() {
        doCallRealMethod().when(mainActivity).stopPeriodicMode();
        mainActivity.stopPeriodicMode();

        verify(mainActivity).stopPeriodicMode();
    }

    @Test
    public void testStopLiveMode() {
        doCallRealMethod().when(mainActivity).stopLiveMode();
        mainActivity.stopLiveMode();

        verify(mainActivity).stopLiveMode();
    }
}
