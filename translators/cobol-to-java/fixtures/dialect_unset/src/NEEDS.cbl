       IDENTIFICATION DIVISION.
       PROGRAM-ID. NEEDS.
      * Uses settings the project leaves unset: one decision per field.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-A           PIC X(3) VALUE HIGH-VALUES.
       01 WS-B           PIC X(3).
       PROCEDURE DIVISION.
           MOVE LOW-VALUES TO WS-B.
           IF WS-A = HIGH-VALUES DISPLAY "high" END-IF.
           IF WS-A > WS-B DISPLAY "ordered" END-IF.
           IF WS-B < "abc" DISPLAY "ordered again" END-IF.
           STOP RUN.
