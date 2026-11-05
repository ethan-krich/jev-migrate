       IDENTIFICATION DIVISION.
       PROGRAM-ID. BILLING.
      * Prints when an invoice falls due: its terms in days after the
      * invoice date, and the weekday of the due date.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-INVOICE-DATE PIC 9(8) VALUE 20261115.
       01 WS-TERMS-DAYS   PIC S9(4) VALUE 30.
       01 WS-DUE-DATE     PIC 9(8).
       01 WS-WEEKDAY      PIC 9.
       01 WS-DUE-TEXT     PIC X(10) VALUE SPACES.
       PROCEDURE DIVISION.
           CALL "DATEADD" USING WS-INVOICE-DATE WS-TERMS-DAYS
                                WS-DUE-DATE.
           DISPLAY "DUE " WS-DUE-DATE.
           CALL "WEEKDAY" USING WS-DUE-DATE WS-WEEKDAY.
           DISPLAY "WEEKDAY " WS-WEEKDAY.
           CALL "DATEFMT" USING WS-DUE-DATE WS-DUE-TEXT.
           DISPLAY "DUE ON " WS-DUE-TEXT.
           STOP RUN.
