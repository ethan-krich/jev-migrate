       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOCALS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 W PIC 9 VALUE 0.
       LOCAL-STORAGE SECTION.
       01 L PIC 9 VALUE 0.
       01 T.
          05 E PIC X OCCURS 3 VALUE "x".
       01 LG.
          05 L1 PIC X(2).
          05 L2 PIC 99.
       PROCEDURE DIVISION.
           ADD 1 TO W. ADD 1 TO L.
           DISPLAY "w " W " l " L " " T " [" LG "]".
           MOVE "y" TO E(2). MOVE "ab12" TO LG.
           GOBACK.
