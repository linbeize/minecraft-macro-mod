package de.linbei.macro;

public record MacroInstruction(Type type, String arg, long number, int jumpIndex) {
    public enum Type {
        KEY_DOWN,
        KEY_UP,
        KEY_PRESS,
        LEFT_DOWN,
        LEFT_UP,
        DELAY,
        FOR,
        NEXT
    }

    public MacroInstruction withJump(int jump) {
        return new MacroInstruction(type, arg, number, jump);
    }
}
