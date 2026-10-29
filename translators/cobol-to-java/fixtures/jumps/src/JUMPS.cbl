       IDENTIFICATION DIVISION.
       PROGRAM-ID. JUMPS.
      * NEXT SENTENCE, EXIT PARAGRAPH / SECTION / PERFORM [CYCLE].
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-I           PIC 99.
       01 WS-J           PIC 99.
       PROCEDURE DIVISION.
       MAIN SECTION.
       START-PARA.
           MOVE 1 TO WS-I.
           IF WS-I = 1 NEXT SENTENCE ELSE DISPLAY "not reached"
           END-IF
           DISPLAY "skipped by next sentence".
           DISPLAY "after sentence".
           EVALUATE WS-I
               WHEN 1 NEXT SENTENCE
               WHEN OTHER DISPLAY "other"
           END-EVALUATE
           DISPLAY "also skipped".
           PERFORM WORK-PARA.
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 5
               IF WS-I = 2
                   EXIT PERFORM CYCLE
               END-IF
               IF WS-I = 4
                   EXIT PERFORM
               END-IF
               DISPLAY "loop " WS-I
           END-PERFORM.
           DISPLAY "after loop " WS-I.
           PERFORM
               DISPLAY "once"
               EXIT PERFORM
           END-PERFORM.
           MOVE 0 TO WS-J.
           PERFORM WITH TEST AFTER UNTIL WS-J >= 3
               ADD 1 TO WS-J
               IF WS-J = 2 EXIT PERFORM CYCLE END-IF
               DISPLAY "after-test " WS-J
           END-PERFORM.
           PERFORM OTHER-SECTION.
           DISPLAY "done".
           STOP RUN.
       WORK-PARA.
           DISPLAY "work start".
           IF WS-I = 1
               EXIT PARAGRAPH
           END-IF.
           DISPLAY "work not reached".
       OTHER-SECTION SECTION.
       FIRST-PARA.
           DISPLAY "first".
           IF WS-I > 0
               EXIT SECTION
           END-IF.
           DISPLAY "not reached either".
       SECOND-PARA.
           DISPLAY "second not reached".
