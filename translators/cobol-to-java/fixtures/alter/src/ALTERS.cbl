       IDENTIFICATION DIVISION.
       PROGRAM-ID. ALTERS.
      * ALTER changes where SWITCH-PARA's GO TO goes, several times,
      * reached both by GO TO and by falling through into it.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 N            PIC 9 VALUE 0.
       PROCEDURE DIVISION.
       MAIN-PARA.
           DISPLAY "start".
       LOOP-PARA.
           ADD 1 TO N.
           DISPLAY "pass " N.
           IF N = 2
               ALTER SWITCH-PARA TO PROCEED TO SECOND-PARA
           END-IF.
           IF N = 4
               ALTER SWITCH-PARA TO PROCEED TO DONE-PARA
           END-IF.
       SWITCH-PARA.
           GO TO FIRST-PARA.
       FIRST-PARA.
           DISPLAY "first".
           GO TO LOOP-PARA.
       SECOND-PARA.
           DISPLAY "second".
           IF N = 3
               ALTER SWITCH-PARA TO FIRST-PARA
           END-IF.
           GO TO LOOP-PARA.
       DONE-PARA.
           DISPLAY "done".
      * ALTER state lives with the program: it survives a GOBACK and
      * is reset by CANCEL.
           CALL "ALTSUB".
           CALL "ALTSUB".
           CALL "ALTSUB".
           CANCEL "ALTSUB".
           CALL "ALTSUB".
           STOP RUN.
