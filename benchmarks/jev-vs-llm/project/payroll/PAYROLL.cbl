       IDENTIFICATION DIVISION.
       PROGRAM-ID. PAYROLL.
      * Weekly payroll: gross pay is hours times the hourly rate, the
      * tax withheld comes from TAXCALC, PAYSLIP prints one payslip
      * line per employee, and the run ends with the totals.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY EMPTAB.
       COPY PAYTOT.
       COPY SLIPREC.
       01 WS-I     PIC 9(2).
       01 WS-COUNT PIC 9(2) VALUE ZERO.
       01 WS-GROSS PIC 9(5)V99.
       01 WS-TAX   PIC 9(5)V99.
       01 WS-NET   PIC 9(5)V99.
       PROCEDURE DIVISION.
           DISPLAY "Payroll run started".
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 4
      *        Gross pay, truncated to the cent like the old system.
               COMPUTE WS-GROSS = EMP-HOURS(WS-I) * EMP-RATE(WS-I)
               CALL "TAXCALC" USING WS-GROSS WS-TAX
               SUBTRACT WS-TAX FROM WS-GROSS GIVING WS-NET
               ADD WS-GROSS TO TOT-GROSS
               ADD WS-TAX TO TOT-TAX
               ADD WS-NET TO TOT-NET
               MOVE EMP-ID(WS-I) TO SLIP-ID
               MOVE EMP-NAME(WS-I) TO SLIP-NAME
               MOVE WS-GROSS TO SLIP-GROSS
               MOVE WS-TAX TO SLIP-TAX
               MOVE WS-NET TO SLIP-NET
               CALL "PAYSLIP" USING SLIP-RECORD
      *        Progress counter for the operator message below.
               ADD 1 TO WS-COUNT
           END-PERFORM.
           DISPLAY "Employees paid: " WS-COUNT.
           DISPLAY "Totals gross " TOT-GROSS " tax " TOT-TAX
                   " net " TOT-NET.
           STOP RUN.
