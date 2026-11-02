package de.linbei.macro;

/**
 * One parsed script statement.
 *
 * @param type      instruction kind
 * @param key       normalized key name for key/mouse instructions, otherwise {@code null}
 * @param number    numeric argument (delay ms, loop count, click count)
 * @param jumpIndex for FOR: index of the matching NEXT; for NEXT: index of the matching FOR
 * @param line      1-based source line, used for error messages and the GUI status
 */
public record MacroInstruction(Type type, String key, long number, int jumpIndex, int line) {
    public enum Type {
        KEY_DOWN,
        KEY_UP,
        KEY_PRESS,
        DELAY,
        FOR,
        NEXT
    }

    public MacroInstruction withJump(int jump) {
        return new MacroInstruction(type, key, number, jump, line);
    }
}
