       IDENTIFICATION DIVISION.
       PROGRAM-ID. OUTER.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 V PIC X(4) VALUE "outr".
       01 PN PIC X(8).
       PROCEDURE DIVISION.
           CALL "INNER-A" USING V.
           CALL "INNER-A" USING V.
           DISPLAY "outer " V.
           CALL "INNER-B".
           MOVE "INNER-C" TO PN.
           CALL PN ON EXCEPTION DISPLAY "inner-c not visible" END-CALL.
           CALL "ELSEWHERE".
           STOP RUN.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. INNER-A.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 N PIC 9 VALUE 0.
       LINKAGE SECTION.
       01 P PIC X(4).
       PROCEDURE DIVISION USING P.
           ADD 1 TO N.
           DISPLAY "inner-a " N " " P.
           MOVE "chgd" TO P.
           CALL "INNER-C".
           CALL "INNER-B".
           GOBACK.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. INNER-C.
       PROCEDURE DIVISION.
           DISPLAY "inner-c (nested in inner-a)".
           GOBACK.
       END PROGRAM INNER-C.
       END PROGRAM INNER-A.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. INNER-B IS COMMON.
       PROCEDURE DIVISION.
           DISPLAY "inner-b".
           GOBACK.
       END PROGRAM INNER-B.
       END PROGRAM OUTER.
