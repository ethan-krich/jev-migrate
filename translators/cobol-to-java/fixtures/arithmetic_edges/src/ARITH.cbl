       IDENTIFICATION DIVISION.
       PROGRAM-ID. ARITH.
      * Truncation, rounding, signs, size errors and divide by zero.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 A          PIC S9(3)V99 VALUE -1.25.
       01 B          PIC S9(3)V9.
       01 C          PIC 9(3)V9.
       01 D          PIC 99 VALUE 50.
       01 E          PIC 99 VALUE 60.
       01 F          PIC S9(5) VALUE 7.
       01 G          PIC 9.
       01 X5         PIC X(5).
       01 Z0         PIC 9 VALUE 0.
       PROCEDURE DIVISION.
           MOVE A TO B.
           DISPLAY "trunc " B.
           ADD 0 TO A GIVING B ROUNDED.
           DISPLAY "round neg " B.
           MOVE A TO C.
           DISPLAY "unsigned " C.
           SUBTRACT 1 FROM A GIVING C.
           DISPLAY "giving unsigned " C.
           ADD 60 TO D E ON SIZE ERROR DISPLAY "size error " D " " E.
           MULTIPLY 3 BY F.
           DISPLAY "times " F.
           MULTIPLY -2 BY F ROUNDED.
           DISPLAY "neg " F.
           DIVIDE F BY 4 GIVING B.
           DISPLAY "div " B.
           DIVIDE F BY 4 GIVING B ROUNDED.
           DISPLAY "div rounded " B.
           DIVIDE Z0 INTO F.
           DISPLAY "div zero kept " F.
           DIVIDE Z0 INTO F ON SIZE ERROR DISPLAY "div zero size error".
           COMPUTE G = 9 + 1.
           DISPLAY "compute trunc " G.
           COMPUTE G = 9 + 1 ON SIZE ERROR DISPLAY "compute size error".
           DISPLAY "kept " G.
           COMPUTE F = -(F * 2) - 1.
           DISPLAY "unary " F.
           MOVE -42 TO X5.
           DISPLAY "[" X5 "]".
           MOVE F TO X5.
           DISPLAY "[" X5 "]".
           MOVE SPACES TO X5.
           MOVE ZEROS TO G.
           DISPLAY "[" X5 "][" G "]".
           MOVE 'it''s' TO X5.
           DISPLAY X5 " " +0 " " -0.50 " " 007.
           MOVE 9 TO RETURN-CODE.
           ADD 1 TO RETURN-CODE.
           STOP RUN.
