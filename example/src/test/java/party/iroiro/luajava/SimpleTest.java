package party.iroiro.luajava;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;
import party.iroiro.luajava.lua51.Lua51;

@Testable
public class SimpleTest {
    @Test
    public void simpleTest() {
        Lua L = new Lua51();
        L.push("Hello World from Lua");
        L.setGlobal("message");
        L.run("print(message)");
        L.run("java.import('java.lang.System').out:println(message)");
        L.close();
    }
}
