       IDENTIFICATION DIVISION.
       PROGRAM-ID. CONDS.
       AUTHOR. FIXTURES.
       DATE-WRITTEN. 2026-10-24.
      * Level 88, SET, EVALUATE, abbreviated and class conditions,
      * figurative constants.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-FLAG        PIC X VALUE "Y".
          88 FLAG-ON     VALUE "Y" WHEN SET TO FALSE IS "N".
          88 FLAG-OFF    VALUE "N" "n".
       01 WS-N           PIC S99 VALUE 5.
          88 N-LOW       VALUE 1 THRU 5.
          88 N-HIGH      VALUE 6 THRU 9, 20.
       01 WS-CODE        PIC X(3) VALUE "B12".
          88 CODE-B      VALUE "B00" THRU "B99".
       01 WS-TEXT        PIC X(6) VALUE "abc   ".
       01 WS-DIGITS      PIC X(4) VALUE "0123".
       01 WS-FILL        PIC X(5).
       01 WS-K           PIC 9.
       PROCEDURE DIVISION.
       MAIN-PARA.
           IF FLAG-ON DISPLAY "flag on" END-IF.
           SET FLAG-ON TO FALSE.
           DISPLAY "flag [" WS-FLAG "]".
           IF FLAG-OFF AND NOT FLAG-ON DISPLAY "flag off" END-IF.
           SET FLAG-ON TO TRUE.
           IF N-LOW DISPLAY "n low" END-IF.
           SET N-HIGH TO TRUE.
           DISPLAY "n " WS-N.
           IF CODE-B DISPLAY "code b" END-IF.
           MOVE 20 TO WS-N.
           IF N-HIGH DISPLAY "n high again" END-IF.
           IF WS-N = 1 OR 5 OR 20 DISPLAY "abbreviated" END-IF.
           IF WS-N > 10 AND < 30 DISPLAY "between" END-IF.
           IF WS-N = 3 OR NOT 7 DISPLAY "not seven" END-IF.
           IF WS-N = (3 OR 20) DISPLAY "combined" END-IF.
           IF WS-DIGITS IS NUMERIC DISPLAY "digits numeric" END-IF.
           IF WS-TEXT IS ALPHABETIC DISPLAY "text alphabetic" END-IF.
           IF WS-TEXT IS ALPHABETIC-LOWER DISPLAY "text lower" END-IF.
           IF WS-TEXT IS NOT ALPHABETIC-UPPER DISPLAY "not upper" END-IF.
           IF WS-CODE IS NOT NUMERIC DISPLAY "code not numeric" END-IF.
           IF WS-TEXT < "abd" DISPLAY "ordered" END-IF.
           IF WS-CODE > SPACES DISPLAY "above spaces" END-IF.
           MOVE ALL "xy" TO WS-FILL.
           DISPLAY "[" WS-FILL "]".
           IF WS-FILL = ALL "xy" DISPLAY "all matches" END-IF.
           MOVE QUOTES TO WS-FILL.
           DISPLAY "[" WS-FILL "]".
           MOVE HIGH-VALUES TO WS-FILL.
           IF WS-FILL = HIGH-VALUES DISPLAY "high" END-IF.
           IF WS-FILL > "zzzzz" DISPLAY "high sorts last" END-IF.
           MOVE LOW-VALUES TO WS-FILL.
           IF WS-FILL < SPACES DISPLAY "low sorts first" END-IF.
           PERFORM VARYING WS-K FROM 1 BY 1 UNTIL WS-K > 6
               EVALUATE TRUE ALSO WS-K
                   WHEN FLAG-OFF ALSO ANY
                       DISPLAY "never"
                   WHEN WS-N = 20 ALSO 1
                   WHEN WS-N = 20 ALSO 2
                       DISPLAY WS-K " one or two"
                   WHEN TRUE ALSO 3 THRU 4
                       DISPLAY WS-K " three-four"
                   WHEN FALSE ALSO ANY
                       DISPLAY "never false"
                   WHEN ANY ALSO NOT 6
                       DISPLAY WS-K " not six"
                   WHEN OTHER
                       DISPLAY WS-K " other"
               END-EVALUATE
           END-PERFORM.
           EVALUATE WS-CODE
               WHEN "A00" THRU "AZZ" DISPLAY "a code"
               WHEN "B12" DISPLAY "exact code"
           END-EVALUATE.
           EVALUATE WS-N > 10
               WHEN TRUE DISPLAY "big"
               WHEN FALSE DISPLAY "small"
           END-EVALUATE.
           EVALUATE TRUE
               WHEN N-LOW DISPLAY "low"
               WHEN N-HIGH DISPLAY "high"
           END-EVALUATE.
           STOP RUN.
