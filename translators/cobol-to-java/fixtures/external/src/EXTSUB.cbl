       IDENTIFICATION DIVISION.
       PROGRAM-ID. EXTSUB.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT SHARED-F ASSIGN TO "shared.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS SH-ST.
       DATA DIVISION.
       FILE SECTION.
       FD SHARED-F IS EXTERNAL.
       01 SH-REC PIC X(5).
       WORKING-STORAGE SECTION.
       01 COUNTER PIC 9(3) EXTERNAL.
       01 NAMES-X EXTERNAL.
          05 N1 PIC X(3).
          05 N2 PIC 9(2).
       01 SH-ST PIC XX EXTERNAL.
       PROCEDURE DIVISION.
           ADD 1 TO COUNTER.
           DISPLAY "sub " COUNTER " " N1 " " N2 " [" SH-REC "] " SH-ST.
           MOVE "subwr" TO SH-REC.
           WRITE SH-REC.
           DISPLAY "sub write " SH-ST.
           GOBACK.
