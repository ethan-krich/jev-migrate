       IDENTIFICATION DIVISION.
       PROGRAM-ID. FLOW.
      * Sections, nested IF, PERFORM variants, conditions.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 I          PIC 99.
       01 J          PIC S99 VALUE -3.
       01 TOTAL      PIC 9(4).
       01 CODE-X     PIC X(4) VALUE "AB".
       01 BLANKS     PIC X(3).
       01 DIGITS-X   PIC X(3) VALUE "042".
       01 NUM3       PIC 999 VALUE 42.
       PROCEDURE DIVISION.
       MAIN SECTION.
       BEGIN.
           PERFORM INIT-PARA.
           PERFORM LOOP-SECTION.
           PERFORM UNTIL I >= 3
               ADD 1 TO I
               IF I = 2
                   DISPLAY "two"
               ELSE
                   IF NOT I > 2
                       DISPLAY "small " I
                   ELSE
                       DISPLAY "big " I
                   END-IF
               END-IF
           END-PERFORM.
           PERFORM WITH TEST AFTER UNTIL I > 0
               DISPLAY "test after runs once " I
           END-PERFORM.
           PERFORM 2 TIMES
               DISPLAY "inline times"
           END-PERFORM.
           MOVE 0 TO I.
           PERFORM VARYING I FROM 10 BY -3 UNTIL I < 2
               DISPLAY "down " I
           END-PERFORM.
           PERFORM WITH TEST AFTER VARYING I FROM 1 BY 1 UNTIL I = 3
               DISPLAY "after " I
           END-PERFORM.
           IF J IS NEGATIVE AND NOT J IS ZERO
               DISPLAY "j negative"
           END-IF.
           IF J > 0 OR CODE-X = "AB" AND TOTAL = 0
               DISPLAY "precedence and-before-or"
           END-IF.
           IF (J > 0 OR CODE-X = "AB") AND TOTAL = 1
               DISPLAY "wrong"
           ELSE
               DISPLAY "parenthesised"
           END-IF.
           IF BLANKS = SPACES DISPLAY "spaces" END-IF.
           IF SPACES = BLANKS DISPLAY "spaces mirrored" END-IF.
           IF CODE-X > "AA" DISPLAY "collating" END-IF.
           IF DIGITS-X = NUM3 DISPLAY "alnum vs numeric" END-IF.
           IF ZERO < NUM3 DISPLAY "zero less" END-IF.
           CONTINUE.
           DISPLAY "total " TOTAL.
           GOBACK.
       INIT-PARA.
           MOVE ZERO TO TOTAL.
           EXIT.
       LOOP-SECTION SECTION.
       LOOP-A.
           ADD 5 TO TOTAL.
       LOOP-B.
           ADD 7 TO TOTAL.
           DISPLAY "section ran " TOTAL.
