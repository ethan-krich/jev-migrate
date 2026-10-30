       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOCKS.
      * CLOSE WITH LOCK / NO REWIND and READ lock phrases.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT LF ASSIGN TO "locks.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS LF-STATUS.
           SELECT SF ASSIGN TO "seq.dat"
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS SF-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD LF.
       01 LF-REC       PIC X(8).
       FD SF.
       01 SF-REC       PIC X(4).
       WORKING-STORAGE SECTION.
       01 LF-STATUS    PIC XX.
       01 SF-STATUS    PIC XX.
       PROCEDURE DIVISION.
       MAIN-PARA.
           OPEN OUTPUT SF.
           MOVE "abcd" TO SF-REC.
           WRITE SF-REC.
           CLOSE SF NO REWIND.
           DISPLAY "close no rewind " SF-STATUS.
           OPEN INPUT SF.
           READ SF WITH KEPT LOCK.
           DISPLAY "read with lock " SF-STATUS " " SF-REC.
           CLOSE SF.
           OPEN OUTPUT LF.
           MOVE "line one" TO LF-REC.
           WRITE LF-REC.
           CLOSE LF WITH LOCK.
           DISPLAY "close with lock " LF-STATUS.
           OPEN INPUT LF.
           DISPLAY "open after lock " LF-STATUS.
           READ LF WITH NO LOCK.
           DISPLAY "read closed " LF-STATUS.
           CLOSE LF.
           DISPLAY "close again " LF-STATUS.
           STOP RUN.
