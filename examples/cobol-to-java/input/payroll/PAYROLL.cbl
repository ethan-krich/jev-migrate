       IDENTIFICATION DIVISION.
       PROGRAM-ID. PAYROLL.
      * Computes gross and net pay for a few hard-coded employees.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY EMPLOYEE.
       01 TAX-RATE      PIC V999 VALUE .185.
       01 TAX           PIC 9(5)V99.
       01 NET           PIC 9(5)V99.
       01 TOTAL-NET     PIC 9(6)V99 VALUE 0.
       PROCEDURE DIVISION.
       MAIN-PARA.
           MOVE "ADA LOVELACE" TO EMP-NAME.
           MOVE 40 TO EMP-HOURS.
           MOVE 52.75 TO EMP-RATE.
           PERFORM PAY-ONE.
           MOVE "ALAN TURING" TO EMP-NAME.
           MOVE 37 TO EMP-HOURS.
           MOVE 48.10 TO EMP-RATE.
           PERFORM PAY-ONE.
           DISPLAY "TOTAL NET    " TOTAL-NET.
           CALL 'SUMMARY'.
           STOP RUN.
       PAY-ONE.
           MULTIPLY EMP-HOURS BY EMP-RATE GIVING EMP-GROSS ROUNDED.
           MULTIPLY EMP-GROSS BY TAX-RATE GIVING TAX ROUNDED.
           SUBTRACT TAX FROM EMP-GROSS GIVING NET.
           ADD NET TO TOTAL-NET.
           DISPLAY EMP-NAME " GROSS " EMP-GROSS " NET " NET.
           IF NET > 1500
               DISPLAY "  ABOVE THRESHOLD"
           END-IF.
