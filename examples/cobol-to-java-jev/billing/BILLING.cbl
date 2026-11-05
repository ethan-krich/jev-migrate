       IDENTIFICATION DIVISION.
       PROGRAM-ID. BILLING.
      * Invoice due dates: each invoice falls due its payment terms
      * (in days) after its invoice date. Prints the due date as a
      * number and as text, and the weekday it falls on.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 INVOICES.
          05 FILLER PIC X(12) VALUE "202611150030".
          05 FILLER PIC X(12) VALUE "202612200045".
          05 FILLER PIC X(12) VALUE "202702260010".
       01 INVOICE-TABLE REDEFINES INVOICES.
          05 INVOICE OCCURS 3 TIMES.
             10 INV-DATE  PIC 9(8).
             10 INV-TERMS PIC 9(4).
       01 WS-I            PIC 9.
       01 WS-INVOICE-DATE PIC 9(8).
       01 WS-TERMS-DAYS   PIC S9(4).
       01 WS-DUE-DATE     PIC 9(8).
       01 WS-WEEKDAY      PIC 9.
       01 WS-DUE-TEXT     PIC X(10).
       PROCEDURE DIVISION.
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 3
               MOVE INV-DATE(WS-I) TO WS-INVOICE-DATE
               MOVE INV-TERMS(WS-I) TO WS-TERMS-DAYS
      *        Due date: the invoice date plus the terms in days.
               CALL "DATEADD" USING WS-INVOICE-DATE WS-TERMS-DAYS
                                    WS-DUE-DATE
      *        Weekday of the due date (1 Monday to 7 Sunday).
               CALL "WEEKDAY" USING WS-DUE-DATE WS-WEEKDAY
      *        The due date as DD/MM/YYYY for the letter.
               MOVE SPACES TO WS-DUE-TEXT
               CALL "DATEFMT" USING WS-DUE-DATE WS-DUE-TEXT
               DISPLAY WS-INVOICE-DATE " " WS-TERMS-DAYS " -> "
                       WS-DUE-DATE " " WS-DUE-TEXT " day " WS-WEEKDAY
           END-PERFORM.
           STOP RUN.
