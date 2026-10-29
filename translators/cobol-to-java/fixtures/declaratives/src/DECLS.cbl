       IDENTIFICATION DIVISION.
       PROGRAM-ID. DECLS.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO "in.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS IN-ST.
           SELECT OUT-FILE ASSIGN TO "out.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT MISSING ASSIGN TO "nope.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT KEYED ASSIGN TO "keyed.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS RANDOM
               RECORD KEY IS K-KEY.
       DATA DIVISION.
       FILE SECTION.
       FD IN-FILE.
       01 IN-REC PIC X(4).
       FD OUT-FILE.
       01 OUT-REC PIC X(4).
       FD MISSING.
       01 M-REC PIC X(4).
       FD KEYED.
       01 K-REC.
          05 K-KEY PIC X(2).
       WORKING-STORAGE SECTION.
       01 IN-ST PIC XX.
       01 COUNT-ERR PIC 99 VALUE 0.
       PROCEDURE DIVISION.
       DECLARATIVES.
       IN-ERRORS SECTION.
           USE AFTER STANDARD ERROR PROCEDURE ON IN-FILE.
           ADD 1 TO COUNT-ERR.
           DISPLAY "use in-file " IN-ST " count " COUNT-ERR.
       IN-ERRORS-PARA.
           DISPLAY "  in-errors paragraph".
       INPUT-ERRORS SECTION.
           USE AFTER ERROR PROCEDURE ON INPUT.
           DISPLAY "use input mode".
       OUTPUT-ERRORS SECTION.
           USE AFTER EXCEPTION PROCEDURE OUTPUT.
           DISPLAY "use output mode".
       KEYED-ERRORS SECTION.
           USE AFTER ERROR PROCEDURE ON KEYED.
           DISPLAY "use keyed".
       END DECLARATIVES.
       MAIN-LOGIC SECTION.
       START-HERE.
           OPEN INPUT IN-FILE.
           READ IN-FILE. DISPLAY "read " IN-REC " " IN-ST.
           READ IN-FILE. DISPLAY "read " IN-REC " " IN-ST.
           READ IN-FILE. DISPLAY "after eof " IN-ST.
           READ IN-FILE AT END DISPLAY "at end phrase"
               NOT AT END DISPLAY "not at end".
           DISPLAY "after 46 " IN-ST.
           OPEN INPUT IN-FILE.
           DISPLAY "after reopen " IN-ST.
           CLOSE IN-FILE.
           OPEN INPUT MISSING.
           DISPLAY "after missing".
           READ MISSING.
           DISPLAY "after read missing".
           OPEN OUTPUT OUT-FILE.
           READ OUT-FILE.
           DISPLAY "after read output".
           CLOSE OUT-FILE.
           CLOSE OUT-FILE.
           DISPLAY "after double close".
           OPEN OUTPUT KEYED.
           MOVE "AA" TO K-KEY. WRITE K-REC.
           WRITE K-REC.
           DISPLAY "after dup".
           WRITE K-REC INVALID KEY DISPLAY "invalid key phrase".
           CLOSE KEYED.
           DISPLAY "errors " COUNT-ERR.
           STOP RUN.
