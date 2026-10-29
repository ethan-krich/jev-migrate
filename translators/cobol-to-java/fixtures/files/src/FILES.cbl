       IDENTIFICATION DIVISION.
       PROGRAM-ID. FILES.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO "in.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS IN-ST.
           SELECT OUT-FILE ASSIGN TO "out.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS OUT-ST.
           SELECT BIN-FILE ASSIGN TO "bin.dat"
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS BIN-ST.
           SELECT MISSING ASSIGN TO "nope.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS MS-ST.
           SELECT UNCHECKED ASSIGN TO "nope.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD IN-FILE.
       01 IN-REC PIC X(8).
       FD OUT-FILE.
       01 OUT-REC PIC X(10).
       FD BIN-FILE.
       01 BIN-REC.
          05 B-NAME PIC X(4).
          05 B-NUM  PIC S9(3) COMP-3.
       FD MISSING.
       01 M-REC PIC X(4).
       FD UNCHECKED.
       01 U-REC PIC X(4).
       WORKING-STORAGE SECTION.
       01 IN-ST  PIC XX.
       01 OUT-ST PIC XX.
       01 BIN-ST PIC XX.
       01 MS-ST  PIC XX.
       01 EOF    PIC X VALUE "N".
       01 CNT    PIC 99 VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT MISSING. DISPLAY "missing open " MS-ST.
           READ MISSING AT END DISPLAY "eof".
           DISPLAY "read closed " MS-ST.
           OPEN INPUT IN-FILE. DISPLAY "open " IN-ST.
           OPEN INPUT IN-FILE. DISPLAY "open again " IN-ST.
           OPEN OUTPUT OUT-FILE.
           PERFORM UNTIL EOF = "Y"
               READ IN-FILE
                   AT END MOVE "Y" TO EOF
                   NOT AT END
                       ADD 1 TO CNT
                       DISPLAY CNT " [" IN-REC "] " IN-ST
                       MOVE IN-REC TO OUT-REC
                       WRITE OUT-REC
               END-READ
           END-PERFORM.
           DISPLAY "eof status " IN-ST.
           READ IN-FILE AT END DISPLAY "again eof " IN-ST.
           CLOSE IN-FILE OUT-FILE.
           DISPLAY "close " IN-ST " " OUT-ST.
           CLOSE IN-FILE. DISPLAY "close again " IN-ST.
           OPEN OUTPUT BIN-FILE.
           MOVE "abcd" TO B-NAME. MOVE -12 TO B-NUM. WRITE BIN-REC.
           MOVE "wxyz" TO B-NAME. MOVE 345 TO B-NUM. WRITE BIN-REC.
           CLOSE BIN-FILE.
           OPEN INPUT BIN-FILE.
           READ BIN-FILE. DISPLAY B-NAME " " B-NUM " " BIN-ST.
           READ BIN-FILE. DISPLAY B-NAME " " B-NUM " " BIN-ST.
           READ BIN-FILE AT END DISPLAY "bin eof " BIN-ST.
           CLOSE BIN-FILE.
           OPEN EXTEND OUT-FILE. MOVE "appended" TO OUT-REC.
           WRITE OUT-REC. CLOSE OUT-FILE.
           OPEN INPUT OUT-FILE.
           MOVE "N" TO EOF.
           PERFORM UNTIL EOF = "Y"
               READ OUT-FILE AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "out [" OUT-REC "]"
               END-READ
           END-PERFORM.
           CLOSE OUT-FILE.
           OPEN I-O BIN-FILE.
           READ BIN-FILE.
           MOVE 99 TO B-NUM.
           REWRITE BIN-REC.
           READ BIN-FILE.
           DISPLAY "second " B-NAME " " B-NUM.
           CLOSE BIN-FILE.
           OPEN INPUT BIN-FILE.
           READ BIN-FILE INTO OUT-REC.
           DISPLAY "rewritten " B-NAME " " B-NUM " " BIN-ST.
           CLOSE BIN-FILE.
           DISPLAY "unchecked open next".
           OPEN INPUT UNCHECKED.
           DISPLAY "not reached".
           STOP RUN.
