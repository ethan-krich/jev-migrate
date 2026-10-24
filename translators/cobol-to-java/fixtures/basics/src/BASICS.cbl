       IDENTIFICATION DIVISION.
       PROGRAM-ID. BASICS.
      * Data movement, arithmetic and DISPLAY formatting.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-NAME        PIC X(10) VALUE "COBOL".
       01 WS-SHORT       PIC X(3).
       01 WS-COUNT       PIC 9(3) VALUE 7.
       01 WS-SMALL       PIC 99.
       01 WS-AMOUNT      PIC S9(5)V99 VALUE -123.45.
       01 WS-RATE        PIC 9V999 VALUE 1.125.
       01 WS-RESULT      PIC S9(7)V99.
       01 WS-QUOT        PIC 9(3)V9.
       77 WS-FLAG        PIC X VALUE "N".
       PROCEDURE DIVISION.
       MAIN-PARA.
           DISPLAY "Hello, " WS-NAME "!".
           MOVE "ABCDEFG" TO WS-SHORT.
           DISPLAY "[" WS-SHORT "]".
           MOVE WS-COUNT TO WS-SHORT.
           DISPLAY "[" WS-SHORT "]".
           MOVE 12345 TO WS-SMALL.
           DISPLAY "[" WS-SMALL "]".
           ADD 95 TO WS-SMALL.
           DISPLAY "[" WS-SMALL "]".
           MULTIPLY WS-AMOUNT BY WS-RATE GIVING WS-RESULT.
           DISPLAY "product " WS-RESULT.
           MULTIPLY WS-AMOUNT BY WS-RATE GIVING WS-RESULT ROUNDED.
           DISPLAY "rounded " WS-RESULT.
           DIVIDE 7 INTO 100 GIVING WS-QUOT.
           DISPLAY "quot " WS-QUOT.
           DIVIDE 7 INTO 100 GIVING WS-QUOT ROUNDED.
           DISPLAY "quot " WS-QUOT.
           SUBTRACT 200 FROM WS-AMOUNT.
           DISPLAY "amount " WS-AMOUNT.
           COMPUTE WS-RESULT = (WS-AMOUNT + 10) * -2.
           DISPLAY "computed " WS-RESULT.
           ADD 1 TO WS-COUNT ON SIZE ERROR DISPLAY "overflow"
               NOT ON SIZE ERROR DISPLAY "no overflow".
           ADD 999 TO WS-COUNT
               ON SIZE ERROR DISPLAY "overflow, kept " WS-COUNT
           END-ADD.
           IF WS-AMOUNT < 0 AND WS-FLAG = "N"
               DISPLAY "negative"
           ELSE
               DISPLAY "positive"
           END-IF.
           IF WS-NAME = "COBOL"
               DISPLAY "padded compare"
           END-IF.
           PERFORM COUNT-PARA 3 TIMES.
           PERFORM VARYING WS-SMALL FROM 1 BY 2 UNTIL WS-SMALL > 6
               DISPLAY "vary " WS-SMALL
           END-PERFORM.
           DISPLAY "done" WITH NO ADVANCING.
           DISPLAY " -1.50 = " -1.50.
           MOVE 3 TO RETURN-CODE.
           STOP RUN.
       COUNT-PARA.
           ADD 1 TO WS-COUNT.
           DISPLAY "count " WS-COUNT.
