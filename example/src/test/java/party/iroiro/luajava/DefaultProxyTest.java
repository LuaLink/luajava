package party.iroiro.luajava;

import party.iroiro.luajava.suite.B;
import party.iroiro.luajava.suite.InvokeSpecialConversionTest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static party.iroiro.luajava.Lua.LuaError.OK;

public class DefaultProxyTest {
    private interface PrivateNullable {
        default void test(Object obj) {
            if (obj == null) {
                throw new NullPointerException("Passed a null value");
            }
        }
    }

    public interface DefaultRunnable extends Callable<Integer> {
        @Override
        default Integer call() {
            return 1024;
        }

        default void throwsError() {
            throw new LuaException("exception!");
        }

        boolean equals();

        void luaError();

        int luaError(int i);
    }

    private final AbstractLua L;

    public DefaultProxyTest(AbstractLua L) {
        this.L = L;
    }

    public void testMethodEquals() throws Throwable {
        Method equals = Object.class.getMethod("equals", Object.class);
        Method hashCode = Object.class.getMethod("hashCode");
        Method toString = Object.class.getMethod("toString");
        assertTrue(
                LuaProxy.methodEquals(equals, boolean.class, "equals", Object.class)
        );
        assertTrue(
                LuaProxy.methodEquals(hashCode, int.class, "hashCode")
        );
        assertTrue(
                LuaProxy.methodEquals(toString, String.class, "toString")
        );
        assertFalse(
                LuaProxy.methodEquals(equals, int.class, "equals", Object.class)
        );
        assertFalse(
                LuaProxy.methodEquals(equals, boolean.class, "equal", Object.class)
        );
        assertFalse(
                LuaProxy.methodEquals(equals, boolean.class, "equals", Integer.class)
        );
    }

    public void test() {
        assertDoesNotThrow(this::testMethodEquals);
        L.run("return { luaError = function(_, i)\n" +
              "assert(i == 2 or i == 3)\n" +
              "if i == 2 then return nil else return 3 end\n" +
              "end }");
        DefaultRunnable proxy =
                (DefaultRunnable) L.createProxy(new Class[]{DefaultRunnable.class}, Lua.Conversion.SEMI);
        assertEquals(1024, proxy.call());
        assertEquals(Proxy.getInvocationHandler(proxy).hashCode(), proxy.hashCode());
        // noinspection SimplifiableAssertion,EqualsWithItself
        assertTrue(proxy.equals(proxy));
        // noinspection SimplifiableAssertion,EqualsBetweenInconvertibleTypes
        assertFalse(proxy.equals(L));
        assertEquals("LuaProxy[interface party.iroiro.luajava.DefaultProxyTest$DefaultRunnable]@"
                     + Integer.toHexString(proxy.hashCode()), proxy.toString());
        LuaException exception = assertThrows(LuaException.class, proxy::equals);
        assertTrue(exception.getMessage().startsWith("method not implemented: "));

        assertEquals("exception!",
                assertThrows(LuaException.class, proxy::throwsError).getMessage());

        assertTrue(assertThrows(LuaException.class, proxy::luaError).getMessage()
                .contains("assertion failed"));
        assertTrue(assertThrows(LuaException.class, () -> proxy.luaError(1)).getMessage()
                .contains("assertion failed"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> proxy.luaError(2)).getMessage()
                .contains("Primitive not accepting null values"));
        assertEquals(3., proxy.luaError(3));

        hierarchyTest();
        simpleIterTest();
        exceptionTest();

        defaultMethodTest();
    }

    private void defaultMethodTest() {
        Iterator<Object> iterator = new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Object next() {
                return null;
            }

            @Override
            public void remove() {
            }
        };
        L.push(iterator, Lua.Conversion.SEMI);

        assertThrows(UnsupportedOperationException.class, () -> L.invokeSpecial(iterator,
                Iterator.class.getDeclaredMethod("remove"),
                new Object[0]
        ));

        L.error((Throwable) null);
        L.run("i = 10");
        L.run("return {\n" +
              "  next = function()\n" +
              "    i = i - 1\n" +
              "    return i\n" +
              "  end,\n" +
              "  hasNext = function()\n" +
              "    return i > 0\n" +
              "  end" +
              "}");
        Iterator<?> iter = (Iterator<?>) L.createProxy(new Class[]{Iterator.class}, Lua.Conversion.SEMI);
        Set<Double> set = new HashSet<>();
        iter.forEachRemaining(i -> {
            assertInstanceOf(Double.class, i);
            set.add(((Double) i));
        });
        assertEquals(10, set.size());
        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains((double) i));
        }

        L.run("return {}");
        PrivateNullable priv = (PrivateNullable) L.createProxy(new Class[]{PrivateNullable.class}, Lua.Conversion.SEMI);
        assertEquals(
                "Passed a null value",
                assertThrows(NullPointerException.class, () -> priv.test(null)).getMessage()
        );
        assertDoesNotThrow(() -> priv.test(new Object()));

        new InvokeSpecialConversionTest(L).test();
    }

    private void exceptionTest() {
        L.run("return {}");
        assertTrue(
                assertThrows(IllegalArgumentException.class,
                        () -> L.createProxy(new Class[]{String.class}, Lua.Conversion.SEMI))
                        .getMessage().contains("java.lang.String")
        );
        L.run("return {}");
        L.push(L.createProxy(new Class[]{A.class}, Lua.Conversion.SEMI), Lua.Conversion.NONE);
        L.setGlobal("aa");
        assertEquals(OK, L.run("return aa:a() + 1"), L.toString(-1));
        assertEquals(2., L.toNumber(-1));
    }

    private void simpleIterTest() {
        L.run("i = 10");
        assertEquals(OK, L.run("return {\n" +
                               "  next = function()\n" +
                               "    i = i - 1\n" +
                               "    return i\n" +
                               "  end,\n" +
                               "  hasNext = function()\n" +
                               "    return i > 0\n" +
                               "  end\n" +
                               "}"));
        Iterator<?> iter = (Iterator<?>)
                L.createProxy(new Class[]{Iterator.class}, Lua.Conversion.SEMI);
        Set<Double> iset = new HashSet<>();
        iter.forEachRemaining(i -> {
            if (i instanceof Double) {
                assertTrue(iset.add((Double) i));
            }
        });
        assertEquals(10, iset.size(), Arrays.toString(iset.toArray()));

        L.createTable(0, 0);
        assertEquals("Expecting a table / function and interfaces",
                assertThrows(IllegalArgumentException.class,
                        () -> L.createProxy(new Class[0], Lua.Conversion.SEMI))
                        .getMessage());
    }

    private void hierarchyTest() {
        L.createTable(0, 0);
        Object proxy = L.createProxy(new Class[]{
                A.class, B.class, C.class,
        }, Lua.Conversion.SEMI);
        assertEquals(4, ((C) proxy).c());
        assertEquals(3, ((B) proxy).b());

        L.createTable(0, 0);
        Object dup = L.createProxy(new Class[]{
                A.class, D.class,
        }, Lua.Conversion.SEMI);
        Set<Integer> s = new HashSet<>();
        s.add(1);
        s.add(2);
        assertTrue(s.contains(((A) dup).dup()));
        assertTrue(s.contains(((D) dup).dup()));
    }

    /* {@code public} is required */
    public interface A {
        default int a() {
            return 1;
        }

        default int dup() {
            return 1;
        }
    }

    public interface D {
        default int dup() {
            return 2;
        }

        @SuppressWarnings("unused")
        default void noReturn() {}
    }

    public interface C extends A {
        default int c() {
            return a() + 3;
        }
    }
}
