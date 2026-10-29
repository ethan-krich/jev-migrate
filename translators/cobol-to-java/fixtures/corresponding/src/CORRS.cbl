       IDENTIFICATION DIVISION.
       PROGRAM-ID. CORRS.
      * DIVIDE ... REMAINDER and MOVE / ADD / SUBTRACT CORRESPONDING.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 Q              PIC S9V9.
       01 Q2             PIC S99.
       01 R              PIC S9(3)V99.
       01 R2             PIC S9.
       01 SRC.
          05 ID-NO       PIC 9(3) VALUE 42.
          05 NAME        PIC X(5) VALUE "ALICE".
          05 AMOUNT      PIC S9(3)V9 VALUE 12.5.
          05 INNER.
             10 QTY      PIC 99 VALUE 7.
          05 ONLY-SRC    PIC X VALUE "S".
       01 DST.
          05 NAME        PIC X(3).
          05 FILLER      PIC X VALUE "-".
          05 AMOUNT      PIC S9(4)V99 VALUE 100.
          05 ID-NO       PIC X(4).
          05 INNER.
             10 QTY      PIC 9(3) VALUE 1.
          05 ONLY-DST    PIC X VALUE "D".
       PROCEDURE DIVISION.
           DIVIDE -17 BY 5 GIVING Q REMAINDER R.
           DISPLAY Q " " R.
           DIVIDE -17.3 BY 5 GIVING Q REMAINDER R.
           DISPLAY Q " " R.
           DIVIDE -17.3 BY 5 GIVING Q ROUNDED REMAINDER R.
           DISPLAY Q " " R.
           DIVIDE 4 INTO 17.3 GIVING Q REMAINDER R.
           DISPLAY Q " " R.
           DIVIDE 99 BY 5 GIVING Q REMAINDER R
               ON SIZE ERROR DISPLAY "size " Q " " R
               NOT ON SIZE ERROR DISPLAY "no size error"
           END-DIVIDE.
           DIVIDE 99 BY 5 GIVING Q REMAINDER R.
           DISPLAY "truncated " Q " " R.
           DIVIDE 50 BY 7 GIVING Q2 REMAINDER R2.
           DISPLAY Q2 " " R2.
           DIVIDE 5 BY 0 GIVING Q REMAINDER R
               ON SIZE ERROR DISPLAY "by zero " Q " " R
           END-DIVIDE.
           MOVE CORRESPONDING SRC TO DST.
           DISPLAY "[" NAME OF DST "][" ID-NO OF DST "] " AMOUNT OF DST
               " " QTY OF INNER OF DST " " ONLY-DST.
           ADD CORR SRC TO DST.
           DISPLAY AMOUNT OF DST " " QTY OF INNER OF DST.
           SUBTRACT CORRESPONDING SRC FROM DST ROUNDED.
           DISPLAY AMOUNT OF DST " " QTY OF INNER OF DST.
           STOP RUN.
