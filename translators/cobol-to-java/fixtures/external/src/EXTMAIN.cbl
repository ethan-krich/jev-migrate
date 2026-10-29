       IDENTIFICATION DIVISION.
       PROGRAM-ID. EXTMAIN.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT SHARED-F ASSIGN TO "shared.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS SH-ST.
           SELECT A-FILE ASSIGN TO "a.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT B-FILE ASSIGN TO "b.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
       I-O-CONTROL.
           SAME RECORD AREA FOR A-FILE B-FILE.
       DATA DIVISION.
       FILE SECTION.
       FD SHARED-F IS EXTERNAL.
       01 SH-REC PIC X(5).
       FD A-FILE.
       01 A-REC PIC X(4).
       FD B-FILE.
       01 B-REC.
          05 B1 PIC X(2).
          05 B2 PIC X(4).
       WORKING-STORAGE SECTION.
       01 COUNTER PIC 9(3) EXTERNAL.
       01 NAMES-X EXTERNAL.
          05 N1 PIC X(3).
          05 N2 PIC 9(2).
       01 SH-ST PIC XX EXTERNAL.
       01 LOCAL-X PIC X(3) VALUE "loc".
       PROCEDURE DIVISION.
           DISPLAY "[" COUNTER "][" NAMES-X "]".
           MOVE 5 TO COUNTER.
           MOVE "abc" TO N1. MOVE 12 TO N2.
           CALL "EXTSUB".
           DISPLAY "main " COUNTER " " NAMES-X.
           OPEN OUTPUT SHARED-F.
           MOVE "first" TO SH-REC. WRITE SH-REC.
           CALL "EXTSUB".
           CLOSE SHARED-F.
           DISPLAY "close " SH-ST.
           MOVE "wxyz" TO A-REC.
           DISPLAY "[" B-REC "]".
           MOVE "123456" TO B-REC.
           DISPLAY "[" A-REC "]".
           OPEN OUTPUT A-FILE. WRITE A-REC. CLOSE A-FILE.
           OPEN INPUT A-FILE. READ A-FILE. CLOSE A-FILE.
           DISPLAY "[" B-REC "]".
           STOP RUN.
