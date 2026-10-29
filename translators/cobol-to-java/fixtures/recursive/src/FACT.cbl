       IDENTIFICATION DIVISION.
       PROGRAM-ID. FACT IS RECURSIVE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 CALLS PIC 9(3) VALUE 0.
       LOCAL-STORAGE SECTION.
       01 MY-N  PIC 9(2).
       01 SUB-N PIC 9(2).
       01 SUB-R PIC 9(6).
       01 TAG   PIC X(3) VALUE "loc".
       LINKAGE SECTION.
       01 L-N PIC 9(2).
       01 L-R PIC 9(6).
       PROCEDURE DIVISION USING L-N L-R.
           ADD 1 TO CALLS.
           MOVE L-N TO MY-N.
           DISPLAY "enter " MY-N " calls " CALLS " " TAG.
           MOVE "chg" TO TAG.
           IF MY-N <= 1
               MOVE 1 TO L-R
           ELSE
               COMPUTE SUB-N = MY-N - 1
               CALL "FACT" USING SUB-N SUB-R
               COMPUTE L-R = MY-N * SUB-R
           END-IF.
           DISPLAY "leave " MY-N " " L-N " " L-R " " TAG.
           GOBACK.
