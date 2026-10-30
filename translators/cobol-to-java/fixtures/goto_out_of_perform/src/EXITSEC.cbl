       IDENTIFICATION DIVISION.
       PROGRAM-ID. EXITSEC.
      * EXIT SECTION from a paragraph PERFORMed on its own.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 M            PIC 9 VALUE 0.
       PROCEDURE DIVISION.
       MAIN SECTION.
       M-1.
           PERFORM S1-A.
           DISPLAY "after s1-a " M.
           GOBACK.
       S1 SECTION.
       S1-A.
           ADD 1 TO M.
           DISPLAY "s1-a " M.
           IF M < 2
               EXIT SECTION
           END-IF.
       S1-B.
           DISPLAY "s1-b".
       S2 SECTION.
       S2-A.
           DISPLAY "s2-a".
           PERFORM S1-A.
           DISPLAY "s2 back".
