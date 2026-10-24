       IDENTIFICATION DIVISION.
       PROGRAM-ID. PAY-UTIL.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY PAY-REC.
       01 CALLS      PIC 99 VALUE 0.
       PROCEDURE DIVISION.
           ADD 1 TO CALLS.
           DISPLAY "util call " CALLS " sees own record [" PAY-NAME "]".
           ADD 4 TO RETURN-CODE.
           GOBACK.
