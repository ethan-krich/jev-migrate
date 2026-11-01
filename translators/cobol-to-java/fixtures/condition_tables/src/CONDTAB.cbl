       IDENTIFICATION DIVISION.
       PROGRAM-ID. CONDTAB.
      * Subscripted condition-names and AND / OR in combined
      * comparisons.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-TABLE.
          05 WS-ROW OCCURS 4 TIMES INDEXED BY RX.
             10 WS-STATE  PIC X VALUE "N".
                88 ROW-OPEN   VALUE "O" WHEN SET TO FALSE IS "C".
                88 ROW-CLOSED VALUE "C".
                88 ROW-NEW    VALUE "N".
             10 WS-CELL OCCURS 3 TIMES.
                15 WS-AMT PIC 99 VALUE 0.
                   88 AMT-ZERO  VALUE 0.
                   88 AMT-SMALL VALUE 1 THRU 9.
       01 WS-I          PIC 9.
       01 WS-J          PIC 9.
       01 WS-N          PIC 99 VALUE 7.
       01 WS-K          PIC 99.
       PROCEDURE DIVISION.
       MAIN-PARA.
           SET ROW-OPEN (2) TO TRUE.
           SET ROW-OPEN (4) TO TRUE.
           SET ROW-OPEN (4) TO FALSE.
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 4
               IF ROW-OPEN (WS-I)
                   DISPLAY "row " WS-I " open"
               ELSE
                   IF ROW-CLOSED (WS-I)
                       DISPLAY "row " WS-I " closed"
                   ELSE
                       DISPLAY "row " WS-I " other " WS-STATE (WS-I)
                   END-IF
               END-IF
           END-PERFORM.
           MOVE 5 TO WS-AMT (2, 3).
           MOVE 42 TO WS-AMT (3, 1).
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 4
               PERFORM VARYING WS-J FROM 1 BY 1 UNTIL WS-J > 3
                   EVALUATE TRUE
                       WHEN AMT-ZERO (WS-I, WS-J)
                           CONTINUE
                       WHEN AMT-SMALL (WS-I WS-J)
                           DISPLAY "small " WS-I "," WS-J
                       WHEN OTHER
                           DISPLAY "big " WS-I "," WS-J
                   END-EVALUATE
               END-PERFORM
           END-PERFORM.
           SET RX TO 1.
           PERFORM UNTIL ROW-OPEN (RX)
               SET RX UP BY 1
           END-PERFORM.
           SET WS-I TO RX.
           DISPLAY "first open " WS-I.
           SET RX UP BY 2.
           IF ROW-OPEN (RX) DISPLAY "rx open"
           ELSE DISPLAY "rx not open" END-IF.
           IF NOT ROW-NEW (1) AND NOT ROW-NEW (3)
               DISPLAY "no news"
           ELSE
               DISPLAY "some news"
           END-IF.
           SET AMT-SMALL (1, 1) TO TRUE.
           DISPLAY "amt " WS-AMT (1, 1).
           PERFORM VARYING WS-K FROM 1 BY 3 UNTIL WS-K > 12
               IF WS-K = (1 OR 4 AND 7 OR 10)
                   DISPLAY "or-and " WS-K
               END-IF
               IF WS-K > (2 AND 5 OR 9)
                   DISPLAY "and-or " WS-K
               END-IF
               IF WS-K NOT = (1 AND 10 OR 7)
                   DISPLAY "not " WS-K
               END-IF
           END-PERFORM.
           IF WS-N < (8 AND 9 AND 10 OR 3)
               DISPLAY "chain"
           END-IF.
           STOP RUN.
