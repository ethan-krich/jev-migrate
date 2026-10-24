       IDENTIFICATION DIVISION.
       PROGRAM-ID. PAY-MAIN.
      * Calls two programs from other directories; shares a copybook.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY PAY-REC.
       01 RUNS       PIC 9 VALUE 0.
       PROCEDURE DIVISION.
           MOVE "ACME" TO PAY-NAME.
           MOVE 1200.50 TO PAY-AMOUNT.
           DISPLAY "main " PAY-NAME " " PAY-AMOUNT.
           CALL 'PAY-UTIL'.
           DISPLAY "after util rc=" RETURN-CODE.
           CALL 'PAY-UTIL'.
           DISPLAY "after util rc=" RETURN-CODE.
           CALL 'RPT01'.
           DISPLAY "after report rc=" RETURN-CODE.
           STOP RUN.
