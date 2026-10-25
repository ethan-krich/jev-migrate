       IDENTIFICATION DIVISION.
       PROGRAM-ID. GNUCOMP.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 A    PIC S9(5)V99 VALUE 1234.56.
       01 B    PIC S9(3)V9  VALUE -7.3.
       01 C    PIC 9(2)     VALUE 3.
       01 D    PIC S9V9(4)  VALUE 0.0001.
       01 R0   PIC S9(3)V9(4).
       01 R1   PIC S9(5)V99.
       01 R2   PIC S9(9).
       01 R3   PIC S9(2).
       01 R4   PIC S9V9(17).
       01 R5   PIC S9(3).
       PROCEDURE DIVISION.
           MOVE 0 TO R0.
           COMPUTE R0 = 1 / 3
               ON SIZE ERROR DISPLAY "00 size error"
               NOT ON SIZE ERROR DISPLAY "00 " R0.
           COMPUTE R0 = 1 / 3.
           DISPLAY "00 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 ROUNDED = 2 / 3
               ON SIZE ERROR DISPLAY "01 size error"
               NOT ON SIZE ERROR DISPLAY "01 " R0.
           COMPUTE R0 ROUNDED = 2 / 3.
           DISPLAY "01 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 = 10 / 7
               ON SIZE ERROR DISPLAY "02 size error"
               NOT ON SIZE ERROR DISPLAY "02 " R0.
           COMPUTE R0 = 10 / 7.
           DISPLAY "02 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 = 1 / 3 * 3
               ON SIZE ERROR DISPLAY "03 size error"
               NOT ON SIZE ERROR DISPLAY "03 " R0.
           COMPUTE R0 = 1 / 3 * 3.
           DISPLAY "03 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 ROUNDED = 1 / 3 * 3
               ON SIZE ERROR DISPLAY "04 size error"
               NOT ON SIZE ERROR DISPLAY "04 " R0.
           COMPUTE R0 ROUNDED = 1 / 3 * 3.
           DISPLAY "04 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 = 1 / (1 / 3)
               ON SIZE ERROR DISPLAY "05 size error"
               NOT ON SIZE ERROR DISPLAY "05 " R0.
           COMPUTE R0 = 1 / (1 / 3).
           DISPLAY "05 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 = -7 / 2
               ON SIZE ERROR DISPLAY "06 size error"
               NOT ON SIZE ERROR DISPLAY "06 " R0.
           COMPUTE R0 = -7 / 2.
           DISPLAY "06 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 ROUNDED = -7 / 2
               ON SIZE ERROR DISPLAY "07 size error"
               NOT ON SIZE ERROR DISPLAY "07 " R0.
           COMPUTE R0 ROUNDED = -7 / 2.
           DISPLAY "07 no phrase " R0.
           MOVE 0 TO R0.
           COMPUTE R0 ROUNDED = -2 / 3
               ON SIZE ERROR DISPLAY "08 size error"
               NOT ON SIZE ERROR DISPLAY "08 " R0.
           COMPUTE R0 ROUNDED = -2 / 3.
           DISPLAY "08 no phrase " R0.
           MOVE 0 TO R1.
           COMPUTE R1 = A / B
               ON SIZE ERROR DISPLAY "09 size error"
               NOT ON SIZE ERROR DISPLAY "09 " R1.
           COMPUTE R1 = A / B.
           DISPLAY "09 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 ROUNDED = A / B
               ON SIZE ERROR DISPLAY "10 size error"
               NOT ON SIZE ERROR DISPLAY "10 " R1.
           COMPUTE R1 ROUNDED = A / B.
           DISPLAY "10 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = A / B * C
               ON SIZE ERROR DISPLAY "11 size error"
               NOT ON SIZE ERROR DISPLAY "11 " R1.
           COMPUTE R1 = A / B * C.
           DISPLAY "11 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 ROUNDED = (A + C) / (B - C)
               ON SIZE ERROR DISPLAY "12 size error"
               NOT ON SIZE ERROR DISPLAY "12 " R1.
           COMPUTE R1 ROUNDED = (A + C) / (B - C).
           DISPLAY "12 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = A / C / B
               ON SIZE ERROR DISPLAY "13 size error"
               NOT ON SIZE ERROR DISPLAY "13 " R1.
           COMPUTE R1 = A / C / B.
           DISPLAY "13 no phrase " R1.
           MOVE 0 TO R2.
           COMPUTE R2 = 100000000 / 3
               ON SIZE ERROR DISPLAY "14 size error"
               NOT ON SIZE ERROR DISPLAY "14 " R2.
           COMPUTE R2 = 100000000 / 3.
           DISPLAY "14 no phrase " R2.
           MOVE 0 TO R3.
           COMPUTE R3 = 1000 / 3
               ON SIZE ERROR DISPLAY "15 size error"
               NOT ON SIZE ERROR DISPLAY "15 " R3.
           COMPUTE R3 = 1000 / 3.
           DISPLAY "15 no phrase " R3.
           MOVE 0 TO R4.
           COMPUTE R4 = 1 / 7
               ON SIZE ERROR DISPLAY "16 size error"
               NOT ON SIZE ERROR DISPLAY "16 " R4.
           COMPUTE R4 = 1 / 7.
           DISPLAY "16 no phrase " R4.
           MOVE 0 TO R4.
           COMPUTE R4 ROUNDED = 1 / 7
               ON SIZE ERROR DISPLAY "17 size error"
               NOT ON SIZE ERROR DISPLAY "17 " R4.
           COMPUTE R4 ROUNDED = 1 / 7.
           DISPLAY "17 no phrase " R4.
           MOVE 0 TO R4.
           COMPUTE R4 = 2 / 3 + 1 / 3
               ON SIZE ERROR DISPLAY "18 size error"
               NOT ON SIZE ERROR DISPLAY "18 " R4.
           COMPUTE R4 = 2 / 3 + 1 / 3.
           DISPLAY "18 no phrase " R4.
           MOVE 0 TO R1.
           COMPUTE R1 = 2 ** 10
               ON SIZE ERROR DISPLAY "19 size error"
               NOT ON SIZE ERROR DISPLAY "19 " R1.
           COMPUTE R1 = 2 ** 10.
           DISPLAY "19 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = 2 ** C
               ON SIZE ERROR DISPLAY "20 size error"
               NOT ON SIZE ERROR DISPLAY "20 " R1.
           COMPUTE R1 = 2 ** C.
           DISPLAY "20 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = -2 ** 3
               ON SIZE ERROR DISPLAY "21 size error"
               NOT ON SIZE ERROR DISPLAY "21 " R1.
           COMPUTE R1 = -2 ** 3.
           DISPLAY "21 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = (-2) ** 2
               ON SIZE ERROR DISPLAY "22 size error"
               NOT ON SIZE ERROR DISPLAY "22 " R1.
           COMPUTE R1 = (-2) ** 2.
           DISPLAY "22 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = 1.5 ** 3
               ON SIZE ERROR DISPLAY "23 size error"
               NOT ON SIZE ERROR DISPLAY "23 " R1.
           COMPUTE R1 = 1.5 ** 3.
           DISPLAY "23 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 ROUNDED = 10 ** C / 7
               ON SIZE ERROR DISPLAY "24 size error"
               NOT ON SIZE ERROR DISPLAY "24 " R1.
           COMPUTE R1 ROUNDED = 10 ** C / 7.
           DISPLAY "24 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 ROUNDED = A ** 2 / B
               ON SIZE ERROR DISPLAY "25 size error"
               NOT ON SIZE ERROR DISPLAY "25 " R1.
           COMPUTE R1 ROUNDED = A ** 2 / B.
           DISPLAY "25 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = 2 ** 3 ** 2
               ON SIZE ERROR DISPLAY "26 size error"
               NOT ON SIZE ERROR DISPLAY "26 " R1.
           COMPUTE R1 = 2 ** 3 ** 2.
           DISPLAY "26 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = 0 ** 3
               ON SIZE ERROR DISPLAY "27 size error"
               NOT ON SIZE ERROR DISPLAY "27 " R1.
           COMPUTE R1 = 0 ** 3.
           DISPLAY "27 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = 5 ** 0
               ON SIZE ERROR DISPLAY "28 size error"
               NOT ON SIZE ERROR DISPLAY "28 " R1.
           COMPUTE R1 = 5 ** 0.
           DISPLAY "28 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = D / 0.03
               ON SIZE ERROR DISPLAY "29 size error"
               NOT ON SIZE ERROR DISPLAY "29 " R1.
           COMPUTE R1 = D / 0.03.
           DISPLAY "29 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 ROUNDED = 0.01 / D
               ON SIZE ERROR DISPLAY "30 size error"
               NOT ON SIZE ERROR DISPLAY "30 " R1.
           COMPUTE R1 ROUNDED = 0.01 / D.
           DISPLAY "30 no phrase " R1.
           MOVE 0 TO R1.
           COMPUTE R1 = A / 0
               ON SIZE ERROR DISPLAY "31 size error"
               NOT ON SIZE ERROR DISPLAY "31 " R1.
           COMPUTE R1 = A / 0.
           DISPLAY "31 no phrase " R1.
           MOVE 0 TO R5.
           COMPUTE R5 = 999 / 0.5
               ON SIZE ERROR DISPLAY "32 size error"
               NOT ON SIZE ERROR DISPLAY "32 " R5.
           COMPUTE R5 = 999 / 0.5.
           DISPLAY "32 no phrase " R5.
           STOP RUN.
