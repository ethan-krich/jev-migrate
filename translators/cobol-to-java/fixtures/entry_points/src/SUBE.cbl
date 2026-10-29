       IDENTIFICATION DIVISION.
       PROGRAM-ID. SUBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 CNT PIC 9 VALUE 0.
       LINKAGE SECTION.
       01 P1 PIC X(4).
       01 P2 PIC 99.
       PROCEDURE DIVISION.
       MAIN-PARA.
           ADD 1 TO CNT.
           DISPLAY "main entry " CNT.
           GOBACK.
       ALT-PARA.
           ENTRY "SUBE-ALT" USING P1.
           ADD 1 TO CNT.
           DISPLAY "alt entry " CNT " " P1.
           MOVE "zz" TO P1.
           GOBACK.
       TWO-PARA.
           ENTRY "SUBE-TWO" USING P1 P2.
           DISPLAY "two " P1 " " P2.
           MOVE 9 TO RETURN-CODE.
       FALL-PARA.
           ENTRY "SUBE-FALL".
           DISPLAY "fell through".
           GOBACK.
