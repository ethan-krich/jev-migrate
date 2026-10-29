       IDENTIFICATION DIVISION.
       PROGRAM-ID. EBCSEQ.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO "keys.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT OUT-FILE ASSIGN TO "sorted.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT WORK ASSIGN TO "work.tmp".
       DATA DIVISION.
       FILE SECTION.
       FD IN-FILE.
       01 IN-REC PIC X(3).
       FD OUT-FILE.
       01 OUT-REC PIC X(3).
       SD WORK.
       01 W-REC PIC X(3).
       WORKING-STORAGE SECTION.
       01 A PIC X(3).
       01 B PIC X(3).
       01 EOF PIC X VALUE "N".
       PROCEDURE DIVISION.
           MOVE "abc" TO A. MOVE "ABC" TO B.
           IF A < B DISPLAY "lower < upper"
               ELSE DISPLAY "lower >= upper".
           MOVE "123" TO A.
           IF A > B DISPLAY "digits > letters"
               ELSE DISPLAY "digits <= letters".
           MOVE "a" TO A. MOVE "a1" TO B.
           IF A < B DISPLAY "a < a1".
           IF A > SPACES DISPLAY "a > spaces".
           IF A < HIGH-VALUES DISPLAY "a < high".
           IF A > ALL "1" DISPLAY "a > all 1" ELSE DISPLAY "a <= all 1".
           IF "#" < "a" DISPLAY "# < a" ELSE DISPLAY "# >= a".
           SORT WORK ON ASCENDING KEY W-REC WITH DUPLICATES IN ORDER
               USING IN-FILE GIVING OUT-FILE.
           OPEN INPUT OUT-FILE.
           PERFORM UNTIL EOF = "Y"
               READ OUT-FILE AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "[" OUT-REC "]"
               END-READ
           END-PERFORM.
           CLOSE OUT-FILE.
           STOP RUN.
