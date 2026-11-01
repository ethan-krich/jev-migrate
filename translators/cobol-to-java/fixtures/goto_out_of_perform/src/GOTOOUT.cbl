       IDENTIFICATION DIVISION.
       PROGRAM-ID. GOTOOUT.
      * GO TO out of PERFORMed ranges.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 N            PIC 9 VALUE 0.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM P1.
           DISPLAY "after p1 " N.
           CALL "EXITSEC".
           PERFORM P3 THRU P4.
           DISPLAY "after p3 never".
           STOP RUN.
       P1.
           ADD 1 TO N.
           DISPLAY "p1 " N.
           IF N < 3
               GO TO P2
           END-IF.
       P2.
           DISPLAY "p2".
           PERFORM P9.
           GO TO P1.
       P3.
           DISPLAY "p3".
           GO TO P7.
       P4.
           DISPLAY "p4 never".
       P9.
           DISPLAY "p9".
       P7.
           DISPLAY "p7".
       P8.
           DISPLAY "p8, falling off the end".
