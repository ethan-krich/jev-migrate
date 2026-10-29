       IDENTIFICATION DIVISION.
       PROGRAM-ID. MAINE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 A PIC X(4) VALUE "aaaa".
       01 B PIC 9(2) VALUE 7.
       01 PN PIC X(8).
       PROCEDURE DIVISION.
           CALL "SUBE".
           CALL "SUBE-ALT" USING A.
           DISPLAY "a " A.
           CALL "SUBE-TWO" USING A B.
           DISPLAY "rc " RETURN-CODE.
           MOVE "SUBE-ALT" TO PN.
           CALL PN USING A.
           CALL "SUBE".
           CALL "SUBE-FALL".
           STOP RUN.
