       IDENTIFICATION DIVISION.
       PROGRAM-ID. UNSUP.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TBL.
          05 ITEM    PIC 9 OCCURS 3.
       01 PACKED     PIC S9(5) COMP-3.
       01 FLAG       PIC X VALUE "Y".
          88 IS-YES  VALUE "Y".
       01 EDITED     PIC ZZ9.
       01 N          PIC 99.
       PROCEDURE DIVISION.
           MOVE 1 TO N.
           EVALUATE N
               WHEN 1 DISPLAY "one"
           END-EVALUATE.
           COMPUTE N = N / 3.
           GO TO DONE-PARA.
       DONE-PARA.
           STOP RUN.
