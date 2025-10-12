package com.miro.persistence.tooling.core;

/**
 * @author Sergey Chernov
 */
public final class InitScript {

    private final String scriptPath;
    private final String script;

    public InitScript(String scriptPath, String script) {
        this.scriptPath = scriptPath;
        this.script = script;
    }

    public String scriptPath() {
        return scriptPath;
    }

    public String script() {
        return script;
    }
    @Override
    public String toString() {
        return "InitScript[" +
                "scriptPath=" + scriptPath + ", " +
                "script=" + script + ']';
    }
}
