package club.code2create.mcremote.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExampleArgumentsTest {

    @Test
    void defaultsToPromptAndMyWorld() {
        assertEquals(
                new ExampleArguments("my-world", CredentialMode.PROMPT),
                ExampleArguments.parse(new String[0]));
    }

    @Test
    void acceptsCredentialPolicyBeforeOrAfterSample() {
        assertEquals(
                new ExampleArguments("hello", CredentialMode.NO_SAVE),
                ExampleArguments.parse(new String[] {"--no-save", "hello"}));
        assertEquals(
                new ExampleArguments("axis-flat", CredentialMode.SAVE),
                ExampleArguments.parse(new String[] {"axis-flat", "--save"}));
    }

    @Test
    void forgetMustBeAnExclusiveOperation() {
        assertEquals(
                new ExampleArguments("my-world", CredentialMode.FORGET),
                ExampleArguments.parse(new String[] {"--forget"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExampleArguments.parse(new String[] {"--forget", "hello"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExampleArguments.parse(new String[] {"--save", "--no-save"}));
    }
}
