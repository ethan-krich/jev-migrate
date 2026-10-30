       IDENTIFICATION DIVISION.
       PROGRAM-ID. TICKETS.
      * Checks help-desk ticket keys: each key needs a digit, an
      * upper-case and a lower-case letter, and nothing else.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 KEYS-T.
          05 FILLER    PIC X(20) VALUE "Ab3xabcdAB12zZ9!Q7rT".
       01 KEYS-R REDEFINES KEYS-T.
          05 TICKET-KEY PIC X(4) OCCURS 5.
       01 I            PIC 9.
       01 J            PIC 9.
       01 ONE-CHAR     PIC X.
       01 DIGITS-N     PIC 9.
       01 UPPER-N      PIC 9.
       01 LOWER-N      PIC 9.
       01 OTHER-N      PIC 9.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 5
               MOVE 0 TO DIGITS-N UPPER-N LOWER-N OTHER-N
               PERFORM VARYING J FROM 1 BY 1 UNTIL J > 4
                   MOVE TICKET-KEY(I)(J:1) TO ONE-CHAR
                   IF ONE-CHAR < "0"
                       ADD 1 TO OTHER-N
                   ELSE IF ONE-CHAR <= "9"
                       ADD 1 TO DIGITS-N
                   ELSE IF ONE-CHAR < "A"
                       ADD 1 TO OTHER-N
                   ELSE IF ONE-CHAR <= "Z"
                       ADD 1 TO UPPER-N
                   ELSE IF ONE-CHAR >= "a" AND ONE-CHAR <= "z"
                       ADD 1 TO LOWER-N
                   ELSE
                       ADD 1 TO OTHER-N
                   END-IF
               END-PERFORM
               IF DIGITS-N > 0 AND UPPER-N > 0 AND LOWER-N > 0
                  AND OTHER-N = 0
                   DISPLAY TICKET-KEY(I) " valid"
               ELSE
                   DISPLAY TICKET-KEY(I) " invalid"
               END-IF
           END-PERFORM.
           STOP RUN.
