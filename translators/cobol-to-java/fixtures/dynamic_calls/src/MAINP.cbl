       IDENTIFICATION DIVISION.
       PROGRAM-ID. MAINP.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 PNAME PIC X(8).
       01 ARG   PIC X(4) VALUE "abcd".
       01 LONGA PIC X(6) VALUE "123456".
       PROCEDURE DIVISION.
           MOVE "SUBA" TO PNAME.
           CALL PNAME. CALL PNAME.
           DISPLAY "rc " RETURN-CODE.
           CANCEL PNAME.
           CALL PNAME.
           CANCEL "SUBA".
           CALL "SUBA".
           MOVE "  SUBA" TO PNAME.
           CALL PNAME ON EXCEPTION DISPLAY "1 exc" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "1 noexc" END-CALL.
           MOVE "suba" TO PNAME.
           CALL PNAME ON EXCEPTION DISPLAY "2 exc" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "2 noexc" END-CALL.
           MOVE 7 TO RETURN-CODE.
           MOVE "NOPE" TO PNAME.
           CALL PNAME ON EXCEPTION DISPLAY "3 exc " RETURN-CODE
             END-DISPLAY
             NOT ON EXCEPTION DISPLAY "3 noexc" END-CALL.
           CALL PNAME ON OVERFLOW DISPLAY "4 ovf" END-CALL.
           MOVE SPACES TO PNAME.
           CALL PNAME ON EXCEPTION DISPLAY "5 spaces exc" END-CALL.
           CANCEL "NOPE3".
           CANCEL PNAME.
           DISPLAY "cancel unknown ok".
           MOVE "SUBB" TO PNAME.
           CALL PNAME USING ARG. DISPLAY "arg " ARG.
           CALL PNAME USING LONGA. DISPLAY "longa " LONGA.
           MOVE "abcd" TO ARG.
           CALL "SUBB" USING BY CONTENT ARG. DISPLAY "content " ARG.
           CALL "SUBE".
           DISPLAY "back " RETURN-CODE.
           CALL "Mixed".
           MOVE "Mixed" TO PNAME.
           CALL PNAME.
           MOVE "MIXED" TO PNAME.
           CALL PNAME ON EXCEPTION DISPLAY "MIXED exc" END-CALL.
           CALL "SUBF".
           DISPLAY "not reached".
           STOP RUN.
