       IDENTIFICATION DIVISION.
       PROGRAM-ID. DECLDBG.
      * Debugging declaratives without DEBUGGING MODE; GLOBAL USE.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT F ASSIGN TO "missing.dat"
               ORGANIZATION LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD F.
       01 F-REC PIC X(4).
       WORKING-STORAGE SECTION.
       01 N PIC 9 VALUE 0.
       PROCEDURE DIVISION.
       DECLARATIVES.
       DBG SECTION.
           USE FOR DEBUGGING ON P1.
       DBG-1.
           DISPLAY "debugging " N.
       ERR SECTION.
           USE GLOBAL AFTER ERROR PROCEDURE ON F.
       ERR-1.
           DISPLAY "global error handler".
       END DECLARATIVES.
       MAIN SECTION.
       M1.
           PERFORM P1.
           ADD 1 TO N.
           PERFORM P1.
           OPEN INPUT F.
           DISPLAY "after open".
           STOP RUN.
       P1.
           DISPLAY "p1 " N.
