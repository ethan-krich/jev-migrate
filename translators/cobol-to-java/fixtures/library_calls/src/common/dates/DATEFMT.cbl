       IDENTIFICATION DIVISION.
       PROGRAM-ID. DATEFMT.
      * Writes a YYYYMMDD date as the text DD/MM/YYYY.
       DATA DIVISION.
       LINKAGE SECTION.
       01 LK-DATE.
          05 LK-YYYY PIC 9(4).
          05 LK-MM   PIC 99.
          05 LK-DD   PIC 99.
       01 LK-TEXT    PIC X(10).
       PROCEDURE DIVISION USING LK-DATE LK-TEXT.
           STRING LK-DD "/" LK-MM "/" LK-YYYY DELIMITED BY SIZE
               INTO LK-TEXT.
           GOBACK.
