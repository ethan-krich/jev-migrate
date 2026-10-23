       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.
       PROCEDURE DIVISION.
           DISPLAY "before".
           CALL 'EXTPROG'.
           DISPLAY "after".
           STOP RUN.
