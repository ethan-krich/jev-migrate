       IDENTIFICATION DIVISION.
       PROGRAM-ID. ADVANCE.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SPECIAL-NAMES.
           C01 IS TO-TOP.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT LS ASSIGN TO "adv.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT SQ ASSIGN TO "adv.dat"
               ORGANIZATION IS SEQUENTIAL.
           SELECT PG ASSIGN TO "page.txt"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS PG-ST.
           SELECT RAW1 ASSIGN TO "adv.txt"
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS B-ST.
           SELECT RAW2 ASSIGN TO "adv.dat"
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS B-ST.
           SELECT RAW3 ASSIGN TO "page.txt"
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS B-ST.
       DATA DIVISION.
       FILE SECTION.
       FD LS.
       01 LS-REC PIC X(6).
       FD SQ.
       01 SQ-REC PIC X(3).
       FD PG
           LINAGE IS 5 LINES WITH FOOTING AT 4
           LINES AT TOP 1 LINES AT BOTTOM 2.
       01 PG-REC PIC X(4).
       FD RAW1.
       01 B1 PIC X.
       FD RAW2.
       01 B2 PIC X.
       FD RAW3.
       01 B3 PIC X.
       WORKING-STORAGE SECTION.
       01 B PIC X.
       01 B-ST  PIC XX.
       01 PG-ST PIC XX.
       01 N     PIC 9 VALUE 2.
       01 I     PIC 99.
       PROCEDURE DIVISION.
           OPEN OUTPUT LS.
           MOVE "plain" TO LS-REC. WRITE LS-REC.
           MOVE "aft2" TO LS-REC. WRITE LS-REC AFTER ADVANCING 2 LINES.
           MOVE "bef1" TO LS-REC. WRITE LS-REC BEFORE ADVANCING 1.
           MOVE "aftn" TO LS-REC. WRITE LS-REC AFTER N LINES.
           MOVE "aft0" TO LS-REC. WRITE LS-REC AFTER 0.
           MOVE "page" TO LS-REC. WRITE LS-REC AFTER PAGE.
           MOVE "bpage" TO LS-REC. WRITE LS-REC BEFORE PAGE.
           MOVE "top" TO LS-REC. WRITE LS-REC AFTER TO-TOP.
           MOVE "tail" TO LS-REC. WRITE LS-REC AFTER 1.
           CLOSE LS.
           OPEN INPUT RAW1. DISPLAY "adv.txt:" WITH NO ADVANCING.
           PERFORM UNTIL B-ST NOT = "00"
               READ RAW1
               MOVE B1 TO B
               PERFORM SHOW
           END-PERFORM.
           DISPLAY " ." B-ST. CLOSE RAW1.
           OPEN OUTPUT SQ.
           MOVE "abc" TO SQ-REC. WRITE SQ-REC AFTER ADVANCING 1.
           MOVE "def" TO SQ-REC. WRITE SQ-REC BEFORE ADVANCING PAGE.
           MOVE "ghi" TO SQ-REC. WRITE SQ-REC.
           MOVE "jkl" TO SQ-REC. WRITE SQ-REC AFTER 2.
           CLOSE SQ.
           OPEN INPUT RAW2. DISPLAY "adv.dat:" WITH NO ADVANCING.
           PERFORM UNTIL B-ST NOT = "00"
               READ RAW2
               MOVE B2 TO B
               PERFORM SHOW
           END-PERFORM.
           DISPLAY " ." B-ST. CLOSE RAW2.
           OPEN OUTPUT PG.
           DISPLAY "open " PG-ST " lc " LINAGE-COUNTER.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 7
               MOVE I TO PG-REC
               WRITE PG-REC
                   AT END-OF-PAGE DISPLAY "eop at " I " lc "
                       LINAGE-COUNTER
                   NOT AT END-OF-PAGE DISPLAY "line " I " lc "
                       LINAGE-COUNTER
               END-WRITE
           END-PERFORM.
           MOVE "aft3" TO PG-REC. WRITE PG-REC AFTER 3.
           DISPLAY "aft3 lc " LINAGE-COUNTER " " PG-ST.
           MOVE "pg" TO PG-REC. WRITE PG-REC AFTER PAGE.
           DISPLAY "page lc " LINAGE-COUNTER " " PG-ST.
           MOVE "bef2" TO PG-REC. WRITE PG-REC BEFORE 2
               AT EOP DISPLAY "bef2 eop" END-WRITE.
           DISPLAY "bef2 lc " LINAGE-COUNTER " " PG-ST.
           CLOSE PG.
           OPEN INPUT RAW3. DISPLAY "page.txt:" WITH NO ADVANCING.
           PERFORM UNTIL B-ST NOT = "00"
               READ RAW3
               MOVE B3 TO B
               PERFORM SHOW
           END-PERFORM.
           DISPLAY " ." B-ST. CLOSE RAW3.
           STOP RUN.
       SHOW.
           IF B-ST = "00"
               IF B < " "
                   DISPLAY " #" FUNCTION ORD(B) WITH NO ADVANCING
               ELSE
                   DISPLAY " " B WITH NO ADVANCING
               END-IF
           END-IF.
