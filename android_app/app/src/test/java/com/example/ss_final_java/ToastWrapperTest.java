package com.example.ss_final_java;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class ToastWrapperTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @Before
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @After
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    public void testShow_printsToConsole_whenIsTestTrue() {
        // Arrange
        Context mockContext = null; // context won't be used in test mode
        ToastWrapper toastWrapper = new ToastWrapper(mockContext, true);
        String message = "Test message";

        // Act
        toastWrapper.show(message);

        // Assert
        String expectedOutput = "[Toast] " + message + System.lineSeparator();
        assertEquals(expectedOutput, outContent.toString());
    }
}
