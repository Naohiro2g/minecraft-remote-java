package com.mcremote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BootstrapTest {

    @Test
    void usesTheApprovedRootPackage() {
        assertEquals("com.mcremote", BootstrapTest.class.getPackageName());
    }
}
