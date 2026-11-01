       IDENTIFICATION DIVISION.
       PROGRAM-ID. PARTCHK.
      * Checks the part codes of the stock file: two letters, then
      * two digits.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 CODES-T.
          05 FILLER    PIC X(20) VALUE "AB12CD7Zx9Q1RS40M#55".
       01 CODES-R REDEFINES CODES-T.
          05 PART-CODE PIC X(4) OCCURS 5.
       01 I            PIC 9.
       01 J            PIC 9.
       01 ONE-CHAR     PIC X.
       01 BAD          PIC 9.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 5
               MOVE 0 TO BAD
               PERFORM VARYING J FROM 1 BY 1 UNTIL J > 4
                   MOVE PART-CODE(I)(J:1) TO ONE-CHAR
                   IF ONE-CHAR < "A" OR ONE-CHAR > "9"
                       MOVE 1 TO BAD
                   END-IF
                   IF J <= 2 AND ONE-CHAR > "Z"
                       MOVE 1 TO BAD
                   END-IF
                   IF J > 2 AND ONE-CHAR < "0"
                       MOVE 1 TO BAD
                   END-IF
               END-PERFORM
               IF BAD = 0
                   DISPLAY PART-CODE(I) " accepted"
               ELSE
                   DISPLAY PART-CODE(I) " rejected"
               END-IF
           END-PERFORM.
           STOP RUN.
