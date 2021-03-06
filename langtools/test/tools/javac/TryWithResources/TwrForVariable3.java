/* @test /nodynamiccopyright/
 * @bug 7196163
 * @summary Verify that improper expressions used as an operand to try-with-resources are rejected.
 * @compile/fail/ref=TwrForVariable3.out -XDrawDiagnostics -Xlint:-options TwrForVariable3.java
 */
public class TwrForVariable3 implements AutoCloseable {
    public static void main(String... args) {
        TwrForVariable3 v1 = new TwrForVariable3();
        Object v2 = new Object();

        try (v2) {
            fail("no an AutoCloseable");
        }
        try (java.lang.Object) {
            fail("not a variable access");
        }
        try (java.lang) {
            fail("not a variable access");
        }
    }

    static void fail(String reason) {
        throw new RuntimeException(reason);
    }

    public void close() {
    }

}
