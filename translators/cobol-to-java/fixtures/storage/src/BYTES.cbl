       IDENTIFICATION DIVISION.
       PROGRAM-ID. BYTES.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       78 GREETING VALUE "hello".
       78 LIMIT-N  VALUE 3.
       01 REC.
          05 R-NAME   PIC X(5) VALUE "abc".
          05 R-AMT    PIC S9(3)V9 VALUE -12.5.
          05 FILLER   PIC X(2) VALUE "--".
          05 R-SUB.
             10 R-A   PIC 99 VALUE 7.
             10 R-B   PIC X(3) VALUE "xyz".
       01 REC-X REDEFINES REC PIC X(16).
       01 HALVES REDEFINES REC.
          05 H1       PIC X(8).
          05 H2       PIC X(8).
       01 COPY-REC    PIC X(16).
       01 NUMS.
          05 N-LEAD   PIC S9(3) SIGN LEADING SEPARATE VALUE -7.
          05 N-TRAIL  PIC S9(3) SIGN TRAILING SEPARATE VALUE 7.
          05 N-LEMB   PIC S9(3) SIGN LEADING VALUE -42.
       01 RJST        PIC X(6) JUSTIFIED RIGHT VALUE "J".
       01 DIGITS      PIC X(4) VALUE "1234".
       01 DNUM REDEFINES DIGITS PIC 9(4).
       01 RPOS         PIC 9 VALUE 2.
       01 RLEN         PIC 9 VALUE 3.
       PROCEDURE DIVISION.
           DISPLAY "[" REC "]".
           DISPLAY "[" REC-X "][" H1 "][" H2 "]".
           DISPLAY GREETING " " LIMIT-N.
           MOVE REC TO COPY-REC.
           DISPLAY "[" COPY-REC "]".
           MOVE "ZZ" TO R-SUB.
           DISPLAY "[" REC "] " R-A.
           IF R-A NUMERIC DISPLAY "num" ELSE DISPLAY "not num" END-IF.
           MOVE 5 TO R-A.
           IF R-A NUMERIC DISPLAY "num" ELSE DISPLAY "not num" END-IF.
           MOVE R-AMT TO R-SUB.
           DISPLAY "[" R-SUB "]".
           IF H2 > H1 DISPLAY "h2 > h1" ELSE DISPLAY "h2 <= h1" END-IF.
           IF REC = COPY-REC DISPLAY "same" ELSE DISPLAY "differ" END-IF.
           DISPLAY REC-X(RPOS:RLEN) "|" REC-X(10:) "|" R-NAME(2:1).
           MOVE "QQ" TO REC-X(RPOS:2).
           DISPLAY "[" R-NAME "]".
           MOVE 1234 TO REC-X(12:RLEN).
           DISPLAY "[" REC-X "]".
           DISPLAY N-LEAD "|" N-TRAIL "|" N-LEMB "|" NUMS "|".
           ADD 10 TO N-LEAD N-TRAIL N-LEMB.
           DISPLAY N-LEAD "|" N-TRAIL "|" N-LEMB "|" NUMS "|".
           DISPLAY "[" RJST "]".
           MOVE "abcdefgh" TO RJST.
           DISPLAY "[" RJST "]".
           MOVE "ab" TO RJST.
           DISPLAY "[" RJST "]".
           ADD 1 TO DNUM.
           DISPLAY DIGITS.
           MOVE "12a4" TO DIGITS.
           IF DNUM NUMERIC DISPLAY "num" ELSE DISPLAY "not num" END-IF.
           IF DIGITS(1:2) NUMERIC DISPLAY "num" ELSE DISPLAY "not num".
           INITIALIZE REC.
           DISPLAY "[" REC "]".
           MOVE ALL "z" TO REC.
           INITIALIZE REC WITH FILLER REPLACING NUMERIC BY 9.
           DISPLAY "[" REC "]".
           MOVE ALL "z" TO REC.
           INITIALIZE REC ALL TO VALUE.
           DISPLAY "[" REC "]".
           MOVE ALL "z" TO REC.
           INITIALIZE REC REPLACING ALPHANUMERIC BY "q" THEN TO DEFAULT.
           DISPLAY "[" REC "]".
           MOVE ALL "z" TO REC.
           INITIALIZE R-NAME R-A.
           DISPLAY "[" REC "]".
           MOVE SPACES TO REC.
           DISPLAY "[" REC "]".
           MOVE ZERO TO R-SUB.
           DISPLAY "[" REC "]".
           MOVE 12345 TO REC.
           DISPLAY "[" REC "]".
           STOP RUN.
