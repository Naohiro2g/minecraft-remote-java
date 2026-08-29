package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BootstrapTest {

    @Test
    void usesTheApprovedRootPackage() {
        assertEquals("club.code2create.mcremote.client", BootstrapTest.class.getPackageName());
    }
}
