       IDENTIFICATION DIVISION.
       PROGRAM-ID. UNSUP.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT KEYED ASSIGN TO "keyed.dat"
               ORGANIZATION IS INDEXED RECORD KEY IS K-KEY.
           SELECT UNMAPPED ASSIGN TO "where.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT IN-FILE ASSIGN TO "in.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT OUT-FILE ASSIGN TO "out.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT WORK ASSIGN TO "work.tmp".
       DATA DIVISION.
       FILE SECTION.
       FD KEYED.
       01 K-REC.
          05 K-KEY   PIC X(4).
       FD UNMAPPED.
       01 U-REC      PIC X(4).
       FD IN-FILE.
       01 I-REC      PIC X(4).
       FD OUT-FILE.
       01 O-REC      PIC X(4).
       SD WORK.
       01 W-REC      PIC X(4).
       WORKING-STORAGE SECTION.
       01 TBL.
          05 ITEM    PIC 9 OCCURS 3 ASCENDING KEY ITEM INDEXED BY IX.
       01 FLOATING   COMP-1.
       01 FLAG       PIC X VALUE "Y".
          88 IS-YES  VALUE "Y".
       01 EDITED     PIC -$$9.99.
       01 N          PIC 99.
       01 ENV-VAL    PIC X(10).
       01 SHORT-ARG  PIC X(2).
       PROCEDURE DIVISION.
           MOVE 1 TO N.
           EVALUATE N
               WHEN 1 DISPLAY "one"
           END-EVALUATE.
           COMPUTE N = N ** 0.5.
           DISPLAY "report" UPON PRINTER.
           ACCEPT ENV-VAL FROM ENVIRONMENT-VALUE.
           STOP "operator".
           SEARCH ALL ITEM
               WHEN ITEM(IX) = 2 DISPLAY "found".
           CALL "SUB" USING SHORT-ARG.
           SORT WORK ON ASCENDING KEY W-REC
               USING IN-FILE GIVING OUT-FILE.
           COMPUTE N = FUNCTION SQRT(N).
           ALTER SWITCH-PARA TO PROCEED TO DONE-PARA.
           GO TO SWITCH-PARA.
       SWITCH-PARA.
           GO TO DONE-PARA.
       DONE-PARA.
           STOP RUN.
