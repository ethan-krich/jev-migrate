       IDENTIFICATION DIVISION.
       PROGRAM-ID. PAYSLIP.
      * Prints one payslip line: id, name, gross, tax and net pay.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-NAME     PIC X(10).
       01 WS-GROSS-ED PIC ZZ,ZZ9.99.
       01 WS-TAX-ED   PIC ZZ,ZZ9.99.
       01 WS-NET-ED   PIC ZZ,ZZ9.99.
       01 WS-LINE     PIC X(60).
       LINKAGE SECTION.
       COPY SLIPREC.
       PROCEDURE DIVISION USING SLIP-RECORD.
           MOVE SLIP-NAME TO WS-NAME.
      *    Payslips print names with spaces instead of hyphens.
           INSPECT WS-NAME REPLACING ALL "-" BY " ".
           MOVE SLIP-GROSS TO WS-GROSS-ED.
           MOVE SLIP-TAX TO WS-TAX-ED.
           MOVE SLIP-NET TO WS-NET-ED.
           MOVE SPACES TO WS-LINE.
           STRING SLIP-ID " " WS-NAME " gross " WS-GROSS-ED
                  " tax " WS-TAX-ED " net " WS-NET-ED
                  DELIMITED BY SIZE INTO WS-LINE.
           DISPLAY WS-LINE.
           GOBACK.
