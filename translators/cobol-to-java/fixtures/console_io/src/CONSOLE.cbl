       IDENTIFICATION DIVISION.
       PROGRAM-ID. CONSOLE.
      * DISPLAY UPON, EXHIBIT, ACCEPT from stdin and from the clock.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. GNU-LINUX.
       OBJECT-COMPUTER. GNU-LINUX.
       SPECIAL-NAMES.
           SYSERR IS ERR-OUT
           CONSOLE IS MY-TERM.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-LINE        PIC X(5).
       01 Ws-Num         PIC S9(3)V9 VALUE -12.5.
       01 WS-D6          PIC 9(6).
       01 WS-D8          PIC 9(8).
       01 WS-DAY5        PIC 9(5).
       01 WS-DAY7        PIC 9(7).
       01 WS-DOW         PIC 9.
       01 WS-T8          PIC 9(8).
       01 WS-T6          PIC 9(6).
       01 WS-DATE-X      PIC X(8).
       PROCEDURE DIVISION.
           DISPLAY "to console" UPON CONSOLE.
           DISPLAY "to my-term" UPON MY-TERM.
           DISPLAY "to stderr" UPON SYSERR.
           DISPLAY "to err-out" UPON ERR-OUT WITH NO ADVANCING.
           EXHIBIT NAMED WS-LINE Ws-Num.
           EXHIBIT "lit" Ws-Num 12.
           ACCEPT WS-LINE.
           DISPLAY "[" WS-LINE "]".
           ACCEPT WS-LINE FROM CONSOLE.
           DISPLAY "[" WS-LINE "]".
           ACCEPT WS-LINE.
           DISPLAY "[" WS-LINE "]".
           ACCEPT WS-LINE.
           DISPLAY "[" WS-LINE "]".
           ACCEPT WS-D6 FROM DATE.
           ACCEPT WS-D8 FROM DATE YYYYMMDD.
           ACCEPT WS-DAY5 FROM DAY.
           ACCEPT WS-DAY7 FROM DAY YYYYDDD.
           ACCEPT WS-DOW FROM DAY-OF-WEEK.
           ACCEPT WS-T8 FROM TIME.
           DIVIDE WS-T8 BY 100 GIVING WS-T6.
           ACCEPT WS-DATE-X FROM DATE YYYYMMDD.
           DISPLAY WS-D6 " " WS-D8 " " WS-DAY5 " " WS-DAY7 " " WS-DOW
               " " WS-T6 " " WS-DATE-X.
           STOP RUN.
