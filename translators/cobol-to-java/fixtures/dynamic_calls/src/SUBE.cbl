       IDENTIFICATION DIVISION.
       PROGRAM-ID. SUBE.
       PROCEDURE DIVISION.
           DISPLAY "sube".
           CALL "MAINP" ON EXCEPTION DISPLAY "main exc " RETURN-CODE
             END-DISPLAY
             NOT ON EXCEPTION DISPLAY "main noexc" END-CALL.
           DISPLAY "sube after".
           GOBACK.
