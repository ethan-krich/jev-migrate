       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLEE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 N         PIC 9.
       01 I         PIC 9.
       LINKAGE SECTION.
       01 L-REC.
          05 L-NAME PIC X(5).
          05 L-AMT  PIC 9(3).
          05 L-TAB  PIC 99 OCCURS 3.
       01 L-CNT     PIC S9(4) COMP.
       01 L-TOTAL   PIC 9(5) COMP-3.
       PROCEDURE DIVISION USING L-REC L-CNT L-TOTAL.
           MOVE NUMBER-OF-CALL-PARAMETERS TO N.
           DISPLAY "params " N " count " L-CNT.
           MOVE "xyz" TO L-NAME.
           ADD 1 TO L-CNT.
           ADD 5 TO L-AMT.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 3
               ADD L-TAB(I) TO L-TOTAL
               MULTIPLY 2 BY L-TAB(I)
           END-PERFORM.
           MOVE L-CNT TO RETURN-CODE.
           GOBACK.
