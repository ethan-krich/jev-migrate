       IDENTIFICATION DIVISION.
       PROGRAM-ID. UNSUP.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TBL.
          05 ITEM    PIC 9 OCCURS 3.
       01 FLOATING   COMP-1.
       01 FLAG       PIC X VALUE "Y".
          88 IS-YES  VALUE "Y".
       01 EDITED     PIC -$$9.99.
       01 N          PIC 99.
       01 ENV-VAL    PIC X(10).
       PROCEDURE DIVISION.
           MOVE 1 TO N.
           EVALUATE N
               WHEN 1 DISPLAY "one"
           END-EVALUATE.
           COMPUTE N = N / 3.
           DISPLAY "report" UPON PRINTER.
           ACCEPT ENV-VAL FROM ENVIRONMENT-VALUE.
           STOP "operator".
           GO TO DONE-PARA.
       DONE-PARA.
           STOP RUN.
