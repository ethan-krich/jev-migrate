       IDENTIFICATION DIVISION.
       PROGRAM-ID. PERFAFT.
      * WITH TEST AFTER VARYING ... AFTER and TIMES of non-integers.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-I          PIC S99.
       01 WS-J          PIC S99.
       01 WS-K          PIC S99.
       01 WS-COUNT      PIC 999 VALUE 0.
       01 WS-NEG        PIC S9 VALUE -3.
       01 WS-TAB.
          05 WS-ROW OCCURS 3 INDEXED BY RX.
             10 WS-COL PIC X OCCURS 2 INDEXED BY CX.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM WITH TEST AFTER
                   VARYING WS-I FROM 1 BY 1 UNTIL WS-I >= 3
                   AFTER WS-J FROM 1 BY 2 UNTIL WS-J > 3
               DISPLAY "a " WS-I " " WS-J
           END-PERFORM.
           DISPLAY "after a " WS-I " " WS-J.
           PERFORM WITH TEST AFTER
                   VARYING WS-I FROM 1 BY 1 UNTIL WS-I = 2
                   AFTER WS-J FROM WS-I BY 1 UNTIL WS-J >= 3
                   AFTER WS-K FROM WS-J BY -1 UNTIL WS-K < 2
               DISPLAY "b " WS-I " " WS-J " " WS-K
           END-PERFORM.
           DISPLAY "after b " WS-I " " WS-J " " WS-K.
           PERFORM WITH TEST BEFORE
                   VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 2
                   AFTER WS-J FROM WS-I BY 1 UNTIL WS-J > 3
               DISPLAY "c " WS-I " " WS-J
           END-PERFORM.
           PERFORM SHOW-CELL WITH TEST AFTER
                   VARYING RX FROM 1 BY 1 UNTIL RX = 3
                   AFTER CX FROM 1 BY 1 UNTIL CX = 2.
           DISPLAY "count " WS-COUNT.
           MOVE 0 TO WS-COUNT.
           PERFORM WITH TEST AFTER
                   VARYING WS-I FROM 5 BY 1 UNTIL WS-I > 0
                   AFTER WS-J FROM 5 BY 1 UNTIL WS-J > 0
               ADD 1 TO WS-COUNT
           END-PERFORM.
           DISPLAY "once " WS-COUNT.
           MOVE 0 TO WS-COUNT.
           PERFORM WS-NEG TIMES
               ADD 1 TO WS-COUNT
           END-PERFORM.
           DISPLAY "times -3 " WS-COUNT.
           STOP RUN.
       SHOW-CELL.
           ADD 1 TO WS-COUNT.
           SET WS-I TO RX.
           SET WS-J TO CX.
           DISPLAY "cell " WS-I " " WS-J.
