       IDENTIFICATION DIVISION.
       PROGRAM-ID. OVERLAP.
      * MOVEs whose receiver starts inside the sender.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 REC          PIC X(10).
       01 GRP.
          05 G1        PIC X(5).
          05 G2        PIC X(5).
       01 I            PIC 99 VALUE 1.
       01 J            PIC 99 VALUE 3.
       PROCEDURE DIVISION.
       MAIN-PARA.
           MOVE "ABCDEFGHIJ" TO REC.
           MOVE REC (1:9) TO REC (2:9).
           DISPLAY "shift right [" REC "]".
           MOVE "*BCDEFGHIJ" TO REC.
           MOVE REC (1:9) TO REC (2:9).
           DISPLAY "propagate   [" REC "]".
           MOVE "ABCDEFGHIJ" TO REC.
           MOVE REC (3:5) TO REC (1:5).
           DISPLAY "shift left  [" REC "]".
           MOVE "abcdefghij" TO GRP.
           MOVE GRP (2:5) TO G2.
           DISPLAY "group       [" GRP "]".
           MOVE "abcdefghij" TO GRP.
           MOVE G1 TO GRP (3:5).
           DISPLAY "elementary  [" GRP "]".
           MOVE "ABCDEFGHIJ" TO REC.
           MOVE REC (1:4) TO REC (2:8).
           DISPLAY "shorter     [" REC "]".
           STOP RUN.
