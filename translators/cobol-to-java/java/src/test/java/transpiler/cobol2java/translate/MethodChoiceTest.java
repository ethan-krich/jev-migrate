package transpiler.cobol2java.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import transpiler.cobol2java.cobol.Picture;
import transpiler.cobol2java.decision.Candidate;

class MethodChoiceTest {
    private static final DataItem NAME = new DataItem("WS-NAME", DataItem.Kind.ALPHANUMERIC, new Picture.Alphanumeric(10), null, null, 1);
    private static final DataItem CODE = new DataItem("WS-CODE", DataItem.Kind.ALPHANUMERIC, new Picture.Alphanumeric(4), null, null, 2);
    private static final DataItem COUNT = new DataItem("WS-COUNT", DataItem.Kind.NUMERIC, new Picture.Numeric(4, 0, false), null, null, 3);
    private static final DataItem BIG = new DataItem("WS-BIG", DataItem.Kind.NUMERIC, new Picture.Numeric(12, 0, true), null, null, 4);
    private static final DataItem AMOUNT = new DataItem("WS-AMT", DataItem.Kind.NUMERIC, new Picture.Numeric(7, 2, true), null, null, 5);

    /** Only what the type table asks of a program: a field is named after its item, its value is {@code .get()}. */
    private static final TranslationContext CTX = (TranslationContext) Proxy.newProxyInstance(
            MethodChoiceTest.class.getClassLoader(), new Class<?>[] {TranslationContext.class}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "field", "receiving" -> new NameExpr(((Operand.Field) args[0]).item().cobolName().replace("-", "_"));
                        case "numeric" -> args[0] instanceof Operand.Computed c ? c.value()
                                : new MethodCallExpr(new NameExpr(((Operand.Field) args[0]).item().cobolName().replace("-", "_")), "get");
                        default -> throw new UnsupportedOperationException(method.getName());
                    });

    private static JavaMethod method(String source) {
        var cu = StaticJavaParser.parse("class Lib { " + source + " }");
        return JavaMethod.of("Lib", cu.getType(0).getMethods().getFirst());
    }

    @Test
    void bindsOperandsByTypeAndStoresTheResultLikeMove() throws Exception {
        JavaMethod trim = method("/** Trims {@code s}.\n\n More detail. */ public static String trim(String s, int mode) { return s; }");
        var built = MethodChoice.candidates(trim, List.of(new Operand.NumericLiteral(BigDecimal.ONE, "1"), new Operand.Field(NAME)),
                new Operand.Field(CODE), CTX, null);
        assertEquals(1, built.size());
        Candidate c = built.getFirst().candidate();
        assertEquals("method:Lib.trim(String,int)", c.id());
        assertEquals("WS_CODE.set(Lib.trim(WS_NAME.get(), 1));", c.preview());
        assertEquals("calls Lib.trim with s = WS-NAME, mode = 1; the result is MOVEd to WS-CODE", c.description());
        assertEquals(List.of(new Candidate.Doc("String Lib.trim(String s, int mode)", "Trims s.")), c.docs());
    }

    @Test
    void offersEveryExactBindingWithItsOrder() throws Exception {
        JavaMethod cmp = method("public static int compare(String left, String right) { return 0; }");
        var built = MethodChoice.candidates(cmp, List.of(new Operand.Field(NAME), new Operand.Field(CODE)), new Operand.Field(COUNT), CTX, null);
        assertEquals(List.of("method:Lib.compare(String,String)#0,1", "method:Lib.compare(String,String)#1,0"),
                built.stream().map(b -> b.candidate().id()).toList());
        assertEquals("WS_COUNT.move(BigDecimal.valueOf(Lib.compare(WS_CODE.get(), WS_NAME.get())));", built.get(1).candidate().preview());
    }

    @Test
    void refusesWhatDoesNotPassExactly() throws Exception {
        JavaMethod count = method("public static int count(int n) { return n; }");
        // 12 digits do not fit an int; a decimal item is not an integer; text is not a number.
        for (DataItem item : List.of(BIG, AMOUNT, NAME)) {
            assertTrue(MethodChoice.candidates(count, List.of(new Operand.Field(item)), new Operand.Field(COUNT), CTX, null).isEmpty(),
                    item.cobolName());
        }
        // An int result cannot be MOVEd to an alphanumeric item here, and a void method takes no receiver.
        assertTrue(MethodChoice.candidates(count, List.of(new Operand.Field(COUNT)), new Operand.Field(NAME), CTX, null).isEmpty());
        JavaMethod log = method("public static void log(String s) {}");
        assertTrue(MethodChoice.candidates(log, List.of(new Operand.Field(NAME)), new Operand.Field(CODE), CTX, null).isEmpty());
        assertEquals("Lib.log(WS_NAME.get());",
                MethodChoice.candidates(log, List.of(new Operand.Field(NAME)), null, CTX, null).getFirst().candidate().preview());
        JavaMethod scale = method("public static long scale(long n, BigDecimal f) { return n; }");
        assertEquals("WS_BIG.move(BigDecimal.valueOf(Lib.scale(WS_BIG.get().longValueExact(), WS_AMT.get())));",
                MethodChoice.candidates(scale, List.of(new Operand.Field(BIG), new Operand.Field(AMOUNT)), new Operand.Field(BIG), CTX, null)
                        .getFirst().candidate().preview());
    }

    @Test
    void summaryIsTheFirstParagraphAsText() {
        assertEquals("MOVE of a value into this field, see Compare.", JavaMethod.summary(
                "MOVE of a {@code value} into <b>this</b> field,\n see {@link Compare}.\n\n<p>Details."));
        assertEquals("ORD(c): the ordinal.", JavaMethod.summary("ORD(c): the ordinal.<p>More"));
        assertEquals("the label", JavaMethod.summary("{@link Compare#numeric the label}"));
    }

    @Test
    void runtimeDocsAreFoundForTheCallsAPreviewMakes() {
        var stmt = StaticJavaParser.parseStatement("x.set(Functions.upperCase(y.get()));");
        List<Candidate.Doc> docs = JavaDocs.runtime().calledBy(List.of(stmt));
        assertEquals(List.of(new Candidate.Doc("String Functions.upperCase(String s)", "UPPER-CASE: ASCII letters only (the C locale).")), docs);
        assertTrue(JavaDocs.runtime().staticMethods("Compare").stream().anyMatch(m -> m.id().equals("Compare.alphanumeric(String,String)")));
    }
}
