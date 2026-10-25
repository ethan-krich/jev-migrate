       IDENTIFICATION DIVISION.
       PROGRAM-ID. UNSUP.
       DATA DIVISION.
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
           COMPUTE N = N / 3.
           DISPLAY "report" UPON PRINTER.
           ACCEPT ENV-VAL FROM ENVIRONMENT-VALUE.
           STOP "operator".
           SEARCH ALL ITEM
               WHEN ITEM(IX) = 2 DISPLAY "found".
           CALL "SUB" USING SHORT-ARG.
           GO TO DONE-PARA.
       DONE-PARA.
           STOP RUN.
