       IDENTIFICATION DIVISION.
       PROGRAM-ID. DEBUGLN.
      * Lines with D in column 7 are comments unless debugging lines
      * are enabled (GnuCOBOL default; Dialect.debugging_lines=False).
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-N           PIC 9 VALUE 1.
       PROCEDURE DIVISION.
           DISPLAY "start".
      D    DISPLAY "debug only".
      D    ADD 1 TO WS-N.
           DISPLAY "n=" WS-N.
           STOP RUN.
