       IDENTIFICATION DIVISION.
       PROGRAM-ID. RENAMING.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 REC.
          05 A    PIC X(3) VALUE "abc".
          05 B    PIC 9(3) VALUE 123.
          05 C.
             10 C1 PIC X(2) VALUE "c1".
             10 C2 PIC S9(3) COMP-3 VALUE -45.
          05 D    PIC X(4) VALUE "dddd".
       66 R-AB  RENAMES A THRU B.
       66 R-B   RENAMES B.
       66 R-C   RENAMES C.
       66 R-BD  RENAMES B THROUGH D.
       66 R-C1  RENAMES C1.
       01 GRP.
          88 GRP-YN VALUE "YN".
          88 GRP-NY VALUE "NY" "NN".
          88 GRP-RANGE VALUE "AA" THRU "MZ".
          88 GRP-SP VALUE SPACES.
          05 G1 PIC X VALUE "Y".
          05 G2 PIC X VALUE "N".
       01 STR PIC X(12) VALUE "a,b,,a,c,aa".
       PROCEDURE DIVISION.
           DISPLAY "[" R-AB "][" R-B "][" R-C "][" R-BD "][" R-C1 "]".
           MOVE "xyz999" TO R-AB.
           DISPLAY "[" REC "]".
           MOVE 7 TO R-B.
           DISPLAY "[" B "][" R-B "]".
           ADD 1 TO R-B.
           DISPLAY "[" R-B "]".
           IF R-C1 = "c1" DISPLAY "c1 ok".
           IF R-AB = "xyz008" DISPLAY "ab ok".
           MOVE SPACES TO R-BD.
           DISPLAY "[" REC "]".
           IF GRP-YN DISPLAY "yn" END-IF.
           MOVE "NN" TO GRP.
           IF GRP-NY DISPLAY "ny" END-IF.
           IF NOT GRP-YN DISPLAY "not yn" END-IF.
           MOVE "BC" TO GRP.
           IF GRP-RANGE DISPLAY "in range" END-IF.
           SET GRP-YN TO TRUE.
           DISPLAY "[" GRP "]".
           SET GRP-SP TO TRUE.
           DISPLAY "[" GRP "]".
           IF GRP-SP DISPLAY "spaces" END-IF.
           DISPLAY "tally " TALLY.
           INSPECT STR TALLYING TALLY FOR ALL "a".
           DISPLAY "tally " TALLY.
           MOVE 5 TO TALLY.
           ADD 2 TO TALLY.
           DISPLAY "tally " TALLY.
           IF TALLY > 6 DISPLAY "big".
           MOVE 123456 TO TALLY.
           DISPLAY "tally " TALLY.
           STOP RUN.
