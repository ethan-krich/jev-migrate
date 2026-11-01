       IDENTIFICATION DIVISION.
       PROGRAM-ID. ALTSUB.
       PROCEDURE DIVISION.
       GATE-PARA.
           GO TO FRESH-PARA.
       FRESH-PARA.
           DISPLAY "sub fresh".
           ALTER GATE-PARA TO PROCEED TO AGAIN-PARA.
           GOBACK.
       AGAIN-PARA.
           DISPLAY "sub again".
           GOBACK.
