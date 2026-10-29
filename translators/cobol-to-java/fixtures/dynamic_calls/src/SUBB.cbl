       IDENTIFICATION DIVISION.
       PROGRAM-ID. SUBB.
       DATA DIVISION.
       LINKAGE SECTION.
       01 P PIC X(4).
       PROCEDURE DIVISION USING P.
           DISPLAY "subb [" P "]".
           MOVE "zz" TO P.
           GOBACK.
