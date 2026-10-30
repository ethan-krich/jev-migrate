       IDENTIFICATION DIVISION.
       PROGRAM-ID. FNDISP.
      * DISPLAY of numeric function results; X and Z literals.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 A        PIC S9(3)V99 VALUE -12.5.
       01 B        PIC 9(3)V9 VALUE 7.
       01 C        PIC 99 VALUE 3.
       01 P3       PIC S9(5)V9 COMP-3 VALUE -42.7.
       01 BN       PIC S9(4) COMP VALUE -15.
       01 SS       PIC S99V9 SIGN LEADING SEPARATE VALUE -9.9.
       01 ST       PIC S99 SIGN TRAILING SEPARATE VALUE -4.
       01 BIG      PIC 9(10) VALUE 4294967295.
       01 BIG2     PIC 9(10) VALUE 1234567890.
       01 HUGE     PIC 9(18) VALUE 999999999999999999.
       01 FR       PIC V9(9) VALUE .123456789.
       01 TXT      PIC X(12) VALUE " -1,234.50 ".
       01 HX       PIC X(4).
       PROCEDURE DIVISION.
       MAIN-PARA.
           DISPLAY FUNCTION ABS(A) " " FUNCTION ABS(-3) " "
                   FUNCTION ABS(C) " " FUNCTION ABS(P3).
           DISPLAY FUNCTION ABS(BN) " " FUNCTION ABS(SS) " "
                   FUNCTION ABS(ST) " " FUNCTION ABS(+2.50).
           DISPLAY FUNCTION ABS(.05) " " FUNCTION ABS(-0).
           DISPLAY FUNCTION MAX(A B C) " " FUNCTION MAX(1 2.50) " "
                   FUNCTION MAX(3 C) " " FUNCTION MAX(C 3).
           DISPLAY FUNCTION MIN(A B C) " " FUNCTION MIN(1 2) " "
                   FUNCTION MIN(P3 BN) " " FUNCTION MIN(-3 -5).
           DISPLAY FUNCTION MAX(SS ST) " " FUNCTION MIN(SS ST).
           DISPLAY FUNCTION SUM(A B C) " " FUNCTION SUM(1 2) " "
                   FUNCTION SUM(0.1 0.2) " " FUNCTION SUM(-1 1).
           DISPLAY FUNCTION SUM(BIG 1) " " FUNCTION SUM(BIG BIG).
           DISPLAY FUNCTION SUM(BIG2 0) " "
                   FUNCTION SUM(HUGE HUGE).
           DISPLAY FUNCTION SUM(HUGE HUGE HUGE HUGE HUGE HUGE HUGE
                   HUGE HUGE HUGE HUGE HUGE HUGE HUGE HUGE HUGE HUGE
                   HUGE HUGE HUGE).
           DISPLAY FUNCTION SUM(FR FR) " "
                   FUNCTION SUM(FR 0.0000000001).
           DISPLAY FUNCTION SUM(HUGE FR).
           DISPLAY FUNCTION SUM(-2147483648 0) " "
                   FUNCTION SUM(-2147483647 0).
           DISPLAY FUNCTION NUMVAL(" 12.50 ") " " FUNCTION NUMVAL("-3")
                   " " FUNCTION NUMVAL("0.000").
           DISPLAY FUNCTION NUMVAL-C("$1,234.5CR") " "
                   FUNCTION NUMVAL-C(TXT).
           DISPLAY FUNCTION REM(7.5 2) " " FUNCTION REM(-7.25 2) " "
                   FUNCTION REM(7 2).
           DISPLAY FUNCTION MOD(BIG 4294967296) " "
                   FUNCTION MOD(BIG2 2000000000).
           DISPLAY FUNCTION INTEGER(BIG2) " "
                   FUNCTION INTEGER-PART(HUGE) " "
                   FUNCTION INTEGER(-2.5).
           DISPLAY X"41424320" "|" Z"AB" "|" X'2A'.
           MOVE X"31323334" TO HX.
           IF HX = "1234" DISPLAY "hex moved " HX END-IF.
           MOVE Z"xy" TO HX.
           IF HX(3:1) = X"00" DISPLAY "z has nul" END-IF.
           STOP RUN.
