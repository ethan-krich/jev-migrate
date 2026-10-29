       IDENTIFICATION DIVISION.
       PROGRAM-ID. GOTOP.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 I PIC 9 VALUE 0.
       01 J PIC 9 VALUE 0.
       01 K PIC 9 VALUE 2.
       01 N PIC 99 VALUE 0.
       PROCEDURE DIVISION.
       MAIN-PARA.
           DISPLAY "main".
           PERFORM A-PARA THRU C-PARA.
           DISPLAY "back in main".
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 2
                   AFTER J FROM 1 BY 1 UNTIL J > 3
               DISPLAY I J WITH NO ADVANCING
           END-PERFORM.
           DISPLAY " end " I J.
           PERFORM S2.
           MOVE 1 TO K.
           PERFORM S2.
           PERFORM LOOP-PARA THRU LOOP-EXIT.
           DISPLAY "n=" N.
           PERFORM B-PARA 2 TIMES.
           MOVE 2 TO K.
           GO TO X1 X2 X3 DEPENDING ON K.
           DISPLAY "fell through".
       X1.
           DISPLAY "x1".
       X2.
           DISPLAY "x2".
           MOVE 7 TO K.
           GO TO X1 X2 DEPENDING ON K.
           DISPLAY "out of range, next statement".
           PERFORM S3.
           STOP RUN.
       X3.
           DISPLAY "x3".
       A-PARA.
           DISPLAY "a".
           GO TO C-PARA.
       B-PARA.
           DISPLAY "b".
       C-PARA.
           DISPLAY "c".
       LOOP-PARA.
           ADD 1 TO N.
           IF N < 5
               GO TO LOOP-PARA
           END-IF.
       LOOP-EXIT.
           EXIT.
       S2 SECTION.
       S2-A.
           DISPLAY "s2a".
           IF K = 2 GO TO S2-C.
       S2-B.
           DISPLAY "s2b".
       S2-C.
           DISPLAY "s2c".
       S3 SECTION.
       S3-A.
           DISPLAY "s3a".
           EXIT SECTION.
       S3-B.
           DISPLAY "not reached".
