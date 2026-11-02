package de.linbei.macro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static de.linbei.macro.MacroInstruction.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class MacroParserTest {

    @Test
    void parsesLegacyStyleWithTrailingCounts() {
        List<MacroInstruction> p = MacroParser.parse("""
                LeftDown 1,
                KeyDown "W", 1,
                Delay 41000,
                KeyUp "W", 1,
                KeyPress "Num 2", 1,
                LeftUp 1,
                """);
        assertEquals(List.of(KEY_DOWN, KEY_DOWN, DELAY, KEY_UP, KEY_PRESS, KEY_UP), p.stream().map(MacroInstruction::type).toList());
        assertEquals("LMB", p.get(0).key());
        assertEquals("W", p.get(1).key());
        assertEquals(41000, p.get(2).number());
        assertEquals("NUM 2", p.get(4).key());
        assertEquals(1, p.get(4).number());
    }

    @Test
    void semicolonsCommentsAndLineNumbers() {
        List<MacroInstruction> p = MacroParser.parse("""
                # comment
                KeyDown "W"; Delay 100 // trailing comment
                12 KeyUp W
                """);
        assertEquals(3, p.size());
        assertEquals(2, p.get(0).line());
        assertEquals(2, p.get(1).line());
        assertEquals(3, p.get(2).line());
        assertEquals("W", p.get(2).key());
    }

    @Test
    void singleLineScriptForChatCommand() {
        List<MacroInstruction> p = MacroParser.parse("KeyDown \"W\"; Delay 500; KeyUp \"W\"");
        assertEquals(List.of(KEY_DOWN, DELAY, KEY_UP), p.stream().map(MacroInstruction::type).toList());
    }

    @Test
    void nestedLoopsAreLinked() {
        List<MacroInstruction> p = MacroParser.parse("For 2\nFor 3\nDelay 1\nNext\nEndFor");
        assertEquals(4, p.get(0).jumpIndex());
        assertEquals(3, p.get(1).jumpIndex());
        assertEquals(1, p.get(3).jumpIndex());
        assertEquals(0, p.get(4).jumpIndex());
    }

    @Test
    void clickCounts() {
        List<MacroInstruction> p = MacroParser.parse("KeyPress \"2\", 3\nRightClick 2\nLeftClick");
        assertEquals(3, p.get(0).number());
        assertEquals("RMB", p.get(1).key());
        assertEquals(2, p.get(1).number());
        assertEquals(1, p.get(2).number());
    }

    @Test
    void prefixesAreNotMatchedLoosely() {
        assertError("Forward 3", 1, "macro_mod.parse.unknown_command");
        assertError("KeyUpX \"W\"", 1, "macro_mod.parse.unknown_command");
    }

    @Test
    void errorsCarryLineNumbers() {
        assertError("Delay 1\nKeyDown \"X\"", 2, "macro_mod.parse.unknown_key");
        assertError("Delay 1\nDelay abc", 2, "macro_mod.parse.not_a_number");
        assertError("Delay -5", 1, "macro_mod.parse.out_of_range");
        assertError("KeyDown \"W", 1, "macro_mod.parse.unclosed_quote");
        assertError("KeyDown", 1, "macro_mod.parse.missing_arg");
        assertError("Delay 1\nNext", 2, "macro_mod.parse.next_without_for");
        assertError("Delay 1\nFor 3\nDelay 1", 2, "macro_mod.parse.for_without_next");
    }

    @Test
    void unquotedKeyWithTrailingCountIsTheKeyOnly() {
        // Previously this became the key "W, 1" and was silently ignored at runtime.
        assertEquals("W", MacroParser.parse("KeyDown W, 1").get(0).key());
    }

    @Test
    void exampleScriptParses() {
        // compile-time constant, inlined: does not initialise MacroModClient / Fabric
        assertEquals(25, MacroParser.parse(MacroModClient.EXAMPLE_SCRIPT).size());
    }

    private static void assertError(String script, int line, String key) {
        MacroParser.ParseException e = assertThrows(MacroParser.ParseException.class, () -> MacroParser.parse(script));
        assertEquals(line, e.line(), "line of: " + script);
        assertEquals(key, e.key(), "key of: " + script);
    }
}
