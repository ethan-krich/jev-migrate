       IDENTIFICATION DIVISION.
       PROGRAM-ID. RMAIN.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 N   PIC 9(2) VALUE 5.
       01 RES PIC 9(6).
       PROCEDURE DIVISION.
           CALL "FACT" USING N RES.
           DISPLAY "fact " RES.
           CALL "LOCALS". CALL "LOCALS".
           MOVE 3 TO N.
           CALL "FACT" USING N RES.
           DISPLAY "fact " RES.
           STOP RUN.
