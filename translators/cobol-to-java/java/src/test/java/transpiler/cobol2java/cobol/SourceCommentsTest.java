package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceCommentsTest {
    @Test
    void readsIndicatorAndFloatingCommentsAndSkipsBanners() {
        String fixed = String.join("\n",
                "       PROCEDURE DIVISION.",
                "      ****************************",
                "      * Validates a part code.   *",
                "      / page",
                "       MAIN-PARA.",
                "           MOVE \"*>x\" TO A *> keeps A",
                "           STOP RUN.                                                   SEQ00001");
        SourceComments c = SourceComments.of(fixed, CobolSourceFormatEnum.FIXED, false);
        assertEquals(List.of("Validates a part code.", "page"), c.above(5));
        assertEquals(List.of("keeps A"), c.between(5, 7));
        assertEquals(List.of(), c.above(1));
    }

    @Test
    void readsFreeFormatComments() {
        SourceComments c = SourceComments.of("*> header\nmove 1 to x *> why\ndisplay \"a*>b\"\n", CobolSourceFormatEnum.FIXED, true);
        assertEquals(List.of("header"), c.above(2));
        assertEquals(List.of("why"), c.between(2, 3));
        assertTrue(SourceComments.of("move 1 to x\n", CobolSourceFormatEnum.FIXED, true).isEmpty());
    }
}
