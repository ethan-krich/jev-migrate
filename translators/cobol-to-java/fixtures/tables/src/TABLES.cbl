       IDENTIFICATION DIVISION.
       PROGRAM-ID. TABLES.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 T.
          05 E OCCURS 4 INDEXED BY X.
             10 EK    PIC X(2).
             10 EN    PIC 9(2).
       01 T2.
          05 ROW OCCURS 2.
             10 CELL  PIC 9 OCCURS 3 VALUE 5.
       01 I           PIC 9.
       01 J           PIC 9.
       01 S           PIC S99.
       01 V.
          05 VN       PIC 9 VALUE 3.
          05 VE       PIC X OCCURS 1 TO 5 DEPENDING ON VN.
       PROCEDURE DIVISION.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 4
               MOVE I TO EN(I)
               MOVE "k" TO EK(I)(1:1)
               MOVE I TO EK(I)(2:1)
           END-PERFORM.
           DISPLAY "[" T "]".
           DISPLAY "[" T2 "] " CELL(2, 3) " " ROW(1).
           MOVE 7 TO CELL(1, 2).
           DISPLAY "[" T2 "]".
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 2
               PERFORM VARYING J FROM 1 BY 1 UNTIL J > 3
                   ADD I J TO CELL(I, J)
               END-PERFORM
           END-PERFORM.
           DISPLAY "[" T2 "]".
           IF E(2) = E(3) DISPLAY "equal" ELSE DISPLAY "unequal".
           SET X TO 1.
           SEARCH E AT END DISPLAY "none"
               WHEN EN(X) = 3 DISPLAY "found " EK(X).
           SET S TO X.
           DISPLAY "X=" S.
           SET X TO 2.
           SEARCH E AT END DISPLAY "none"
               WHEN EN(X) = 9 DISPLAY "found nine"
               WHEN EK(X) = "k1" DISPLAY "found k1".
           SET S TO X.
           DISPLAY "X=" S.
           SET X DOWN BY 2.
           SET S TO X.
           DISPLAY "X=" S.
           SET X UP BY I.
           SET S TO X.
           DISPLAY "X=" S.
           PERFORM VARYING X FROM 1 BY 1 UNTIL X > 4
               DISPLAY E(X) WITH NO ADVANCING
           END-PERFORM.
           DISPLAY " ".
           IF X > 4 DISPLAY "X past the end".
           DISPLAY "[" V "]".
           MOVE "abc" TO V(2:3).
           DISPLAY "[" V "] " VE(2).
           MOVE 5 TO VN.
           MOVE "x" TO VE(4) VE(5).
           DISPLAY "[" V "]".
           MOVE 2 TO VN.
           DISPLAY "[" V "]".
           MOVE ALL "-" TO V.
           MOVE 4 TO VN.
           DISPLAY "[" V "]".
           INITIALIZE T.
           DISPLAY "[" T "]".
           INITIALIZE E(2) REPLACING ALPHANUMERIC BY "zz".
           DISPLAY "[" T "]".
           MOVE 5 TO I.
           DISPLAY "before the bad subscript".
           DISPLAY EN(I).
           DISPLAY "not reached".
           STOP RUN.
