       IDENTIFICATION DIVISION.
       PROGRAM-ID. STRVERBS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FIRST-NAME   PIC X(10) VALUE "Ada".
       01 LAST-NAME    PIC X(10) VALUE "Lovelace".
       01 WHOLE         PIC X(20) VALUE ALL "*".
       01 SHORT        PIC X(6).
       01 PTR          PIC 99.
       01 CSV          PIC X(30) VALUE "alpha,beta,,gamma;delta".
       01 F1           PIC X(6).
       01 F2           PIC X(6).
       01 F3           PIC X(6).
       01 F4           PIC X(6).
       01 D1           PIC X.
       01 D2           PIC X.
       01 C1           PIC 99.
       01 C2           PIC 99.
       01 TALLY-N      PIC 99.
       01 TEXT1        PIC X(20) VALUE "AABAC  AAB AXA".
       01 CNT-A        PIC 999.
       01 CNT-L        PIC 999.
       01 CNT-C        PIC 999.
       01 SPACED       PIC X(12) VALUE "  a  b   c  ".
       01 NUM-ID       PIC 9(4) VALUE 1203.
       01 ZROS         PIC 99.
       01 TEN          PIC X(10) VALUE "abcdefghij".
       PROCEDURE DIVISION.
           STRING FIRST-NAME DELIMITED BY SPACE
                  " " DELIMITED BY SIZE
                  LAST-NAME DELIMITED BY SPACE
                  INTO WHOLE.
           DISPLAY "[" WHOLE "]".
           MOVE 3 TO PTR.
           STRING "abcdef" DELIMITED BY SIZE INTO SHORT WITH POINTER PTR
               ON OVERFLOW DISPLAY "overflow at " PTR
               NOT ON OVERFLOW DISPLAY "no overflow".
           DISPLAY "[" SHORT "]".
           MOVE 9 TO PTR.
           STRING "x" DELIMITED BY SIZE INTO SHORT WITH POINTER PTR
               ON OVERFLOW DISPLAY "bad pointer " PTR.
           MOVE 1 TO PTR.
           STRING "ab" "cd" DELIMITED BY SIZE INTO SHORT
               WITH POINTER PTR
               NOT ON OVERFLOW DISPLAY "fits, pointer " PTR.
           DISPLAY "[" SHORT "]".
           UNSTRING CSV DELIMITED BY "," OR ";"
               INTO F1 DELIMITER IN D1 COUNT IN C1
                    F2 DELIMITER IN D2 COUNT IN C2
                    F3 F4
               TALLYING IN TALLY-N
               ON OVERFLOW DISPLAY "unstring overflow".
           DISPLAY "[" F1 "][" D1 "]" C1 "[" F2 "][" D2 "]" C2.
           DISPLAY "[" F3 "][" F4 "] " TALLY-N.
           MOVE 1 TO PTR.
           MOVE ZERO TO TALLY-N.
           UNSTRING SPACED DELIMITED BY ALL " "
               INTO F1 F2 F3
               WITH POINTER PTR TALLYING IN TALLY-N.
           DISPLAY "[" F1 "][" F2 "][" F3 "] " PTR " " TALLY-N.
           UNSTRING TEN INTO F1 F2
               ON OVERFLOW DISPLAY "more left".
           DISPLAY "[" F1 "][" F2 "]".
           INSPECT TEXT1 TALLYING CNT-A FOR ALL "A"
               CNT-L FOR LEADING "A"
               CNT-C FOR CHARACTERS BEFORE INITIAL " ".
           DISPLAY CNT-A " " CNT-L " " CNT-C.
           MOVE ZERO TO CNT-A.
           INSPECT TEXT1 TALLYING CNT-A FOR ALL "AA" AFTER INITIAL "C".
           DISPLAY CNT-A.
           INSPECT TEXT1 REPLACING ALL "A" BY "x" AFTER INITIAL "B"
               FIRST "C" BY "y".
           DISPLAY "[" TEXT1 "]".
           INSPECT TEXT1 REPLACING LEADING "x" BY "-"
               CHARACTERS BY "." BEFORE INITIAL "B".
           DISPLAY "[" TEXT1 "]".
           INSPECT TEXT1 CONVERTING "xyAB" TO "XYab".
           DISPLAY "[" TEXT1 "]".
           MOVE ZERO TO CNT-A.
           INSPECT SPACED TALLYING CNT-A FOR ALL SPACES
               REPLACING ALL SPACES BY "_".
           DISPLAY CNT-A " [" SPACED "]".
           MOVE ZERO TO ZROS.
           INSPECT NUM-ID TALLYING ZROS FOR ALL "0".
           DISPLAY ZROS.
           STRING NUM-ID DELIMITED BY "0" "!" DELIMITED BY SIZE
               INTO SHORT.
           DISPLAY "[" SHORT "]".
           STOP RUN.
