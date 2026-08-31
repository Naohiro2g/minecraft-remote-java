package club.code2create.mcremote.examples;

record ExampleArguments(String selection, CredentialMode credentialMode) {

    static ExampleArguments parse(String[] args) {
        String selection = null;
        CredentialMode credentialMode = CredentialMode.PROMPT;
        boolean credentialModeWasSet = false;

        for (String argument : args) {
            CredentialMode requestedMode = switch (argument) {
                case "--save" -> CredentialMode.SAVE;
                case "--no-save" -> CredentialMode.NO_SAVE;
                case "--forget" -> CredentialMode.FORGET;
                default -> null;
            };
            if (requestedMode != null) {
                if (credentialModeWasSet && credentialMode != requestedMode) {
                    throw new IllegalArgumentException(
                            "--save, --no-save, --forgetは同時に指定できません");
                }
                credentialMode = requestedMode;
                credentialModeWasSet = true;
                continue;
            }
            if (argument.startsWith("--")) {
                throw new IllegalArgumentException("不明なoptionです: " + argument);
            }
            if (selection != null) {
                throw new IllegalArgumentException("実行するsampleは一つだけ指定してください");
            }
            selection = argument;
        }

        if (credentialMode == CredentialMode.FORGET && selection != null) {
            throw new IllegalArgumentException("--forgetはsampleを指定せず単独で実行してください");
        }
        return new ExampleArguments(selection == null ? "my-world" : selection, credentialMode);
    }
}
